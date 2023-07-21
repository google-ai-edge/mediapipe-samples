/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.objectdetection.home.gallery

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mediapipe.examples.objectdetection.objectdetector.ObjectDetectorHelper
import com.google.mediapipe.examples.objectdetection.composables.ResultsOverlay
import com.google.mediapipe.examples.objectdetection.utils.getFittedBoxSize
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectionResult
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// VideoDetectionView detects objects in a video periodically each couple of frames, and then plays
// the video while displaying results overlay on top of it and updating them as the video progresses

// It takes as an input the object detection options, a video uri, and function to set the inference
// time state

@Composable
fun VideoDetectionView(
    threshold: Float,
    maxResults: Int,
    delegate: Int,
    mlModel: Int,
    setInferenceTime: (Int) -> Unit,
    videoUri: Uri,
) {
    // We first define some states

    // This state is used to indirectly control the video playback
    var isPlaying by remember {
        mutableStateOf(false)
    }

    // These two states hold the video dimensions, we don't know their values yet so we just set
    // them to 1x1 for now and updating them after loading the video
    var videoHeight by remember {
        mutableStateOf(1)
    }

    var videoWidth by remember {
        mutableStateOf(1)
    }

    // This state holds the results currently being displayed
    var results by remember {
        mutableStateOf<ObjectDetectionResult?>(null)
    }

    // We use ExoPlayer to play our video from the uri with no sounds
    val exoPlayer = ExoPlayer.Builder(LocalContext.current)
        .build()
        .also { exoPlayer ->
            val mediaItem = MediaItem.Builder()
                .setUri(videoUri)
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.volume = 0f
        }

    // Now we run object detection on our video. For a better performance, we run it in background
    val context = LocalContext.current
    val backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
    backgroundExecutor.execute {
        // We create an instance of the ObjectDetectorHelper to perform the detection with
        val objectDetectorHelper =
            ObjectDetectorHelper(
                context = context,
                threshold = threshold,
                currentDelegate = delegate,
                currentModel = mlModel,
                maxResults = maxResults,
                runningMode = RunningMode.VIDEO,
            )

        // We specify the interval (in milliseconds) by which to perform object detection. Current
        // value means we perform object detection on our video every 300 ms
        val videoInterval = 300L

        // Now we start detecting objects in our video by the specified interval
        val detectionResults = objectDetectorHelper.detectVideoFile(videoUri, videoInterval)

        // After performing the detection we get many results. We want to display all of them
        // as the video progresses. So we start the video and keep track of the time elapsed since
        // we started the video, and display the corresponding set of results based on the specified
        // detection interval.
        if (detectionResults != null) {
            val videoStartTimeMs = SystemClock.uptimeMillis()

            // At this point we want the video to start playing as we're displaying the results, but
            // we can't use exoplayer instance directly here cause it can't be used from another
            // thread, so we use this state to control that indirectly. The AndroidView composable
            // has an "update" block that runs with each recomposition, so we check for this value
            // there, and when it's true, we start the video from there
            isPlaying = true

            backgroundExecutor.scheduleAtFixedRate(
                {
                    val videoElapsedTimeMs =
                        SystemClock.uptimeMillis() - videoStartTimeMs
                    val resultIndex =
                        videoElapsedTimeMs.div(videoInterval).toInt()

                    if (resultIndex >= detectionResults.results.size) {
                        backgroundExecutor.shutdown()
                    } else {
                        results = detectionResults.results[resultIndex]
                        setInferenceTime(detectionResults.inferenceTime.toInt())
                    }
                },
                0,
                videoInterval,
                TimeUnit.MILLISECONDS,
            )

        }
        objectDetectorHelper.clearObjectDetector()
    }

    // On disposal of this composable, we release the exo player
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // When displaying the video, we want to scale it to fit in the available space, filling as
        // much space as it can with being cropped. While this behavior is easily achieved out of
        // the box with composables, we need the results overlay layer to have the exact same size
        // of the rendered video so that the results are drawn correctly on top of it. So we'll have
        // to calculate the size of the video after being scaled to fit in the available space
        // manually. To do that, we use the "getFittedBoxSize" function. Go to its implementation
        // for an explanation of how it works.

        val boxSize = getFittedBoxSize(
            containerSize = Size(
                width = this.maxWidth.value,
                height = this.maxHeight.value,
            ),
            boxSize = Size(
                width = videoWidth.toFloat(),
                height = videoHeight.toFloat()
            )
        )

        // Now that we have the exact UI size, we display the video and the results
        Box(
            modifier = Modifier
                .width(boxSize.width.dp)
                .height(boxSize.height.dp)
        ) {
            AndroidView(
                factory = {
                    StyledPlayerView(context).apply {
                        hideController()
                        useController = false
                        player = exoPlayer
                    }
                },
                // This block runs with every recomposition of the AndroidView, and we're using it
                // to play start playing the video as soon as "isPlaying" is set to true (which will
                // trigger a recomposition which in turn will trigger this bloc of code)
                update = {
                    Log.i("Big Chungus", "VideoDetectionView: Updated")
                    if (isPlaying) {
                        videoHeight = exoPlayer.videoSize.height
                        videoWidth = exoPlayer.videoSize.width
                        exoPlayer.play()
                    }
                }
            )
            results?.let {
                ResultsOverlay(
                    results = it,
                    frameWidth = videoWidth,
                    frameHeight = videoHeight,
                )
            }
        }
        // While the object detection is in progress, we display a circular progress indicator
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}