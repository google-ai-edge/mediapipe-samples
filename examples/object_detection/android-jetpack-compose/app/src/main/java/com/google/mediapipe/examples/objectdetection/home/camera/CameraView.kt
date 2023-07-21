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
package com.google.mediapipe.examples.objectdetection.home.camera

import android.Manifest
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.examples.objectdetection.objectdetector.ObjectDetectorHelper
import com.google.mediapipe.examples.objectdetection.objectdetector.ObjectDetectorListener
import com.google.mediapipe.examples.objectdetection.composables.ResultsOverlay
import com.google.mediapipe.examples.objectdetection.utils.getFittedBoxSize
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectionResult
import java.util.concurrent.Executors

// Here we have the camera view which is displayed in Home screen

// It's used to run object detection on live camera feed

// It takes as input the object detection options, and a function to update the inference time state

// You will notice we have a decorator that indicated we're using an experimental API for
// permissions, we're using it cause it's easy to check for permissions with it, and we need camera
// permission in this composable.
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraView(
    threshold: Float,
    maxResults: Int,
    delegate: Int,
    mlModel: Int,
    setInferenceTime: (newInferenceTime: Int) -> Unit,
) {
    // We first have to deal with the camera permission, so we declare a state for it
    val storagePermissionState: PermissionState =
        rememberPermissionState(Manifest.permission.CAMERA)

    // When using this composable, we wanna check the camera permission state, and ask for the
    // permission to use the phone camera in case we don't already have it
    LaunchedEffect(key1 = Unit) {
        if (!storagePermissionState.hasPermission) {
            storagePermissionState.launchPermissionRequest()
        }
    }


    // In case we don't have the permission to use a camera, we'll just display a text to let the
    // user know that that's the case, and we won't show anything else
    if (!storagePermissionState.hasPermission) {
        Text(text = "No Storage Permission!")
        return
    }

    // At this point we have our permission to use the camera. Now we define some states

    // This state holds the object detection results
    var results by remember {
        mutableStateOf<ObjectDetectionResult?>(null)
    }

    // These states hold the dimensions of the camera frames. We don't know their values yet so
    // we just set them initially to 1x1
    var frameHeight by remember {
        mutableStateOf(4)
    }

    var frameWidth by remember {
        mutableStateOf(3)
    }

    // This state is used to prevent further state updates when this camera view is being disposed
    // We check for it before updating states, and we set it to false when we dispose of the view
    var active by remember {
        mutableStateOf(true)
    }

    // We need the following objects setup the camera preview later
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }


    // Here we setup what will happen when the camera view is being disposed. We just need to set
    // "active" to false to stop any further state updates, and to close any currently open cameras
    DisposableEffect(Unit) {
        onDispose {
            active = false;
            cameraProviderFuture.get().unbindAll()
        }
    }

    // Next we describe the UI of this camera view.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // There's a specific behavior we need to achieve here: we want to have the whole camera
        // frame contained (fitted) inside the available UI space. This way we can always see the
        // entire frame with no crops regardless of the screen orientation, as the view will just
        // scale down to fit the available space

        // So we need to get the scaled down size of the camera frame to fit in the available space,
        // to do that, we're gonna use the getFittedBoxSize function that do that calculation for us

        // You can go to the implementation of that function for an explanation of how it works
        val cameraPreviewSize = getFittedBoxSize(
            containerSize = Size(
                width = this.maxWidth.value,
                height = this.maxHeight.value,
            ),
            boxSize = Size(
                width = frameWidth.toFloat(),
                height = frameHeight.toFloat()
            )
        )

        // Now that we have the exact UI dimensions of the camera preview, we apply them to a Box
        // composable, which will contain the camera preview and the results overlay on top of it,
        // both having the exact same UI dimensions
        Box(
            Modifier
                .width(cameraPreviewSize.width.dp)
                .height(cameraPreviewSize.height.dp),
        ) {
            // We're using CameraX to use the phone's camera, and since it doesn't have a prebuilt
            // composable in Jetpack Compose, we use AndroidView to implement it
            AndroidView(
                factory = { ctx ->
                    // We start by instantiating the camera preview view to be displayed
                    val previewView = PreviewView(ctx)
                    val executor = ContextCompat.getMainExecutor(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        // We set a surface for the camera input feed to be displayed in, which is
                        // in the camera preview view we just instantiated
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        // We specify what phone camera to use. In our case it's the back camera
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build()

                        // We instantiate an image analyser to apply some transformations on the
                        // input frame before feeding it to the object detector
                        val imageAnalyzer =
                            ImageAnalysis.Builder()
                                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()

                        // Now we're ready to apply object detection. For a better performance, we
                        // execute the object detection process in a new thread.
                        val backgroundExecutor = Executors.newSingleThreadExecutor()

                        backgroundExecutor.execute {

                            // To apply object detection, we use our ObjectDetectorHelper class,
                            // which abstracts away the specifics of using MediaPipe  for object
                            // detection from the UI elements of the app
                            val objectDetectorHelper =
                                ObjectDetectorHelper(
                                    context = ctx,
                                    threshold = threshold,
                                    currentDelegate = delegate,
                                    currentModel = mlModel,
                                    maxResults = maxResults,
                                    // Since we're detecting objects in a live camera feed, we need
                                    // to have a way to listen for the results
                                    objectDetectorListener = ObjectDetectorListener(
                                        onErrorCallback = { _, _ -> },
                                        onResultsCallback = {
                                            // On receiving results, we now have the exact camera
                                            // frame dimensions, so we set them here
                                            frameHeight = it.inputImageHeight
                                            frameWidth = it.inputImageWidth

                                            // Then we check if the camera view is still active,
                                            // if so, we set the state of the results and
                                            // inference time.
                                            if (active) {
                                                results = it.results.first()
                                                setInferenceTime(it.inferenceTime.toInt())
                                            }
                                        }
                                    ),
                                    runningMode = RunningMode.LIVE_STREAM
                                )

                            // Now that we have our ObjectDetectorHelper instance, we set is as an
                            // analyzer and start detecting objects from the camera live stream
                            imageAnalyzer.setAnalyzer(
                                backgroundExecutor,
                                objectDetectorHelper::detectLivestreamFrame
                            )
                        }

                        // We close any currently open camera just in case, then open up
                        // our own to be display the live camera feed
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            imageAnalyzer,
                            preview
                        )
                    }, executor)
                    // We return our preview view from the AndroidView factory to display it
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Finally, we check for current results, if there's any, we display the results overlay
            results?.let {
                ResultsOverlay(
                    results = it,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight
                )
            }
        }
    }

}

