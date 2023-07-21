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

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.objectdetection.objectdetector.ObjectDetectorHelper
import com.google.mediapipe.examples.objectdetection.composables.ResultsOverlay
import com.google.mediapipe.examples.objectdetection.utils.getFittedBoxSize
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectionResult
import java.util.concurrent.Executors

// ImageDetectionView detects objects in an image and then displays that image with a results
// overlay on top of it

// It takes as an input the object detection options, an image uri, and function to set the
// inference time state
@Composable
fun ImageDetectionView(
    threshold: Float,
    maxResults: Int,
    delegate: Int,
    mlModel: Int,
    imageUri: Uri,
    setInferenceTime: (Int) -> Unit,
) {
    // We first define some states to hold the results and the image information after being loaded
    var loadedImage by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var results by remember {
        mutableStateOf<ObjectDetectionResult?>(null)
    }

    // Here we load the image from the uri
    val context = LocalContext.current
    val source = ImageDecoder.createSource(context.contentResolver, imageUri)
    loadedImage = ImageDecoder.decodeBitmap(source)

    // Now that we have the image loaded, we run object detection on it
    loadedImage?.copy(Bitmap.Config.ARGB_8888, true)?.let { image ->

        // For a better performance, we run the object detection in the background
        val backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor.execute {
            // We create an instance of the ObjectDetectorHelper to perform the detection with
            val objectDetectorHelper = ObjectDetectorHelper(
                context = context,
                threshold = threshold,
                currentDelegate = delegate,
                currentModel = mlModel,
                maxResults = maxResults,
                runningMode = RunningMode.IMAGE,
            )

            // Now we use the ObjectDetectorHelper instance to detect objects in the image
            val resultBundle = objectDetectorHelper.detectImage(image)

            // After performing the detection, we check for results, and update the states if
            // there's any
            if (resultBundle != null) {
                setInferenceTime(resultBundle.inferenceTime.toInt())
                results = resultBundle.results.first()
            }

            // Finally we clear the ObjectDetectorHelper instance
            objectDetectorHelper.clearObjectDetector()
        }
    }


    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // We check if the image is loaded, then we display it
        loadedImage?.let { _loadedImage ->
            val imageBitmap = _loadedImage.asImageBitmap()

            // When displaying the image, we want to scale it to fit in the available space, filling as
            // much space as it can with being cropped. While this behavior is easily achieved out of
            // the box with composables, we need the results overlay layer to have the exact same size
            // of the rendered image so that the results are drawn correctly on top of it. So we'll have
            // to calculate the size of the image after being scaled to fit in the available space
            // manually. To do that, we use the "getFittedBoxSize" function. Go to its implementation
            // for an explanation of how it works.

            val boxSize = getFittedBoxSize(
                containerSize = Size(
                    width = this.maxWidth.value,
                    height = this.maxHeight.value,
                ),
                boxSize = Size(
                    width = _loadedImage.width.toFloat(),
                    height = _loadedImage.height.toFloat()
                )
            )

            // Now that we have the exact UI size, we display the image and the results
            Box(
                modifier = Modifier
                    .width(boxSize.width.dp)
                    .height(boxSize.height.dp)
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                results?.let { _results ->
                    ResultsOverlay(
                        results = _results,
                        frameWidth = _loadedImage.width,
                        frameHeight = _loadedImage.height,
                    )
                }
            }
        }
    }

}