/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
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
package com.google.mediapipe.examples.handlandmarker.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Camera
import androidx.camera.core.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.google.mediapipe.examples.handlandmarker.HandLandmarkerHelper
import com.google.mediapipe.examples.handlandmarker.MainViewModel
import com.google.mediapipe.examples.handlandmarker.R
import com.google.mediapipe.examples.handlandmarker.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), HandLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "Hand Landmarker"
        private const val ANGLE_SMOOTHING_ALPHA = 0.25
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT
    private var isFrontCamera = true
    private val smoothedAngles = mutableMapOf<String, Double>()

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        // Start the HandLandmarkerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if (handLandmarkerHelper.isClose()) {
                handLandmarkerHelper.setupHandLandmarker()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(this::handLandmarkerHelper.isInitialized) {
            viewModel.setMaxHands(handLandmarkerHelper.maxNumHands)
            viewModel.setMinHandDetectionConfidence(handLandmarkerHelper.minHandDetectionConfidence)
            viewModel.setMinHandTrackingConfidence(handLandmarkerHelper.minHandTrackingConfidence)
            viewModel.setMinHandPresenceConfidence(handLandmarkerHelper.minHandPresenceConfidence)
            viewModel.setDelegate(handLandmarkerHelper.currentDelegate)

            // Close the HandLandmarkerHelper and release resources
            backgroundExecutor.execute { handLandmarkerHelper.clearHandLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create the HandLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            handLandmarkerHelper = HandLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                maxNumHands = viewModel.currentMaxHands,
                currentDelegate = viewModel.currentDelegate,
                handLandmarkerHelperListener = this
            )
        }

        // Attach listeners to UI control widgets
        initBottomSheetControls()
    }

    private fun initBottomSheetControls() {
        // init bottom sheet settings
        fragmentCameraBinding.bottomSheetLayout.maxHandsValue.text =
            viewModel.currentMaxHands.toString()
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandDetectionConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandTrackingConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandPresenceConfidence
            )

        // When clicked, lower hand detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (handLandmarkerHelper.minHandDetectionConfidence >= 0.2) {
                handLandmarkerHelper.minHandDetectionConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise hand detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (handLandmarkerHelper.minHandDetectionConfidence <= 0.8) {
                handLandmarkerHelper.minHandDetectionConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower hand tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (handLandmarkerHelper.minHandTrackingConfidence >= 0.2) {
                handLandmarkerHelper.minHandTrackingConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise hand tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (handLandmarkerHelper.minHandTrackingConfidence <= 0.8) {
                handLandmarkerHelper.minHandTrackingConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower hand presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (handLandmarkerHelper.minHandPresenceConfidence >= 0.2) {
                handLandmarkerHelper.minHandPresenceConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise hand presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (handLandmarkerHelper.minHandPresenceConfidence <= 0.8) {
                handLandmarkerHelper.minHandPresenceConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, reduce the number of hands that can be detected at a
        // time
        fragmentCameraBinding.bottomSheetLayout.maxHandsMinus.setOnClickListener {
            if (handLandmarkerHelper.maxNumHands > 1) {
                handLandmarkerHelper.maxNumHands--
                updateControlsUi()
            }
        }

        // When clicked, increase the number of hands that can be detected
        // at a time
        fragmentCameraBinding.bottomSheetLayout.maxHandsPlus.setOnClickListener {
            if (handLandmarkerHelper.maxNumHands < 2) {
                handLandmarkerHelper.maxNumHands++
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference.
        // Current options are CPU and GPU
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate, false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
                ) {
                    try {
                        handLandmarkerHelper.currentDelegate = p2
                        updateControlsUi()
                    } catch(e: UninitializedPropertyAccessException) {
                        Log.e(TAG, "HandLandmarkerHelper has not been initialized yet.")
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Update the values displayed in the bottom sheet. Reset Handlandmarker
    // helper.
    private fun updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.maxHandsValue.text =
            handLandmarkerHelper.maxNumHands.toString()
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                handLandmarkerHelper.minHandDetectionConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                handLandmarkerHelper.minHandTrackingConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                handLandmarkerHelper.minHandPresenceConfidence
            )

        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        backgroundExecutor.execute {
            handLandmarkerHelper.clearHandLandmarker()
            handLandmarkerHelper.setupHandLandmarker()
        }
        fragmentCameraBinding.overlay.clear()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val availableCameras = cameraProvider.availableCameraInfos
        if (availableCameras.isEmpty()) {
            Log.e(TAG, "No cameras are available on this device.")
            Toast.makeText(
                requireContext(),
                R.string.no_cameras_available,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val hasRequestedFacing =
            availableCameras.any { it.lensFacing == cameraFacing }
        val cameraSelector = when {
            hasRequestedFacing -> {
                CameraSelector.Builder().requireLensFacing(cameraFacing).build()
            }
            availableCameras.any { it.lensFacing == CameraSelector.LENS_FACING_BACK } -> {
                Log.w(TAG, "Requested camera facing is unavailable; using the back camera.")
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            else -> {
                Log.w(TAG, "Camera facing is not reported; using the first available camera.")
                isFrontCamera = false
                CameraSelector.Builder()
                    .addCameraFilter { cameraInfos -> cameraInfos.take(1) }
                    .build()
            }
        }

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectHand(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            isFrontCamera = hasRequestedFacing && cameraFacing == CameraSelector.LENS_FACING_FRONT

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectHand(imageProxy: ImageProxy) {
        handLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = isFrontCamera
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after hand have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    override fun onResults(
        resultBundle: HandLandmarkerHelper.ResultBundle
    ) {
        // Write the raw normalized landmarks and joint flexion angles to Logcat.
        // This runs for each completed live-stream frame and does not change the UI.
        logLandmarksAndFingerAngles(resultBundle.results.first())

        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    private fun logLandmarksAndFingerAngles(
        result: HandLandmarkerResult
    ) {
        val hands = result.landmarks()
        val callbackDelayMs = (android.os.SystemClock.uptimeMillis() - result.timestampMs()).coerceAtLeast(0L)
        if (hands.isEmpty()) {
            synchronized(smoothedAngles) { smoothedAngles.clear() }
            Log.i(TAG, "No hand detected frame=${result.timestampMs()}ms callback_delay=${callbackDelayMs}ms")
            return
        }

        hands.forEachIndexed { handIndex, landmarks ->
            val points = landmarks.mapIndexed { index, point ->
                "$index(${point.x().format3()},${point.y().format3()},${point.z().format3()})"
            }.joinToString(" ")

            val fingerAngles = listOf(
                "thumb" to listOf("MCP" to Triple(1, 2, 3), "IP" to Triple(2, 3, 4)),
                "index" to listOf("MCP" to Triple(0, 5, 6), "PIP" to Triple(5, 6, 7), "DIP" to Triple(6, 7, 8)),
                "middle" to listOf("MCP" to Triple(0, 9, 10), "PIP" to Triple(9, 10, 11), "DIP" to Triple(10, 11, 12)),
                "ring" to listOf("MCP" to Triple(0, 13, 14), "PIP" to Triple(13, 14, 15), "DIP" to Triple(14, 15, 16)),
                "pinky" to listOf("MCP" to Triple(0, 17, 18), "PIP" to Triple(17, 18, 19), "DIP" to Triple(18, 19, 20))
            ).flatMap { (name, joints) ->
                joints.map { (jointName, points) ->
                    val (a, joint, c) = points
                    val angle = flexionDegrees(landmarks[a], landmarks[joint], landmarks[c])
                    "$name.$jointName" to angle
                }
            }.toMap()
            val fingerAnglesRaw = fingerAngles.entries.joinToString(" ") { (key, angle) ->
                "$key=${angle.format1()}"
            }
            val fingerAnglesSmooth = fingerAngles.entries.joinToString(" ") { (key, angle) ->
                "$key=${smoothAngle("flex.$key", angle).format1()}"
            }
            val interFingerAngles = getInterFingerAngles(landmarks)
            val interFingerAnglesRaw = interFingerAngles.entries.joinToString(" ") { (name, angle) ->
                "$name=${angle.format1()}"
            }
            val interFingerAnglesSmooth = interFingerAngles.entries.joinToString(" ") { (name, angle) ->
                "$name=${smoothAngle("spread.$name", angle).format1()}"
            }
            val handedness = result.handednesses().getOrNull(handIndex)?.firstOrNull()
            val handLabel = handedness?.categoryName() ?: "unknown"
            val handScore = handedness?.score()?.format2() ?: "n/a"

            // Keep each frame in one line so Android Studio Logcat can filter/copy it easily.
            Log.i(
                TAG,
                "frame=${result.timestampMs()}ms callback_delay=${callbackDelayMs}ms hand=$handIndex " +
                    "side=$handLabel side_score=$handScore points=$points " +
                    "angles_flexion_deg=$fingerAnglesRaw angles_flexion_smooth_deg=$fingerAnglesSmooth " +
                    "angles_between_deg=$interFingerAnglesRaw angles_between_smooth_deg=$interFingerAnglesSmooth"
            )
        }
    }

    /** Dampens frame-to-frame landmark jitter while preserving the raw values in Logcat. */
    private fun smoothAngle(key: String, value: Double): Double = synchronized(smoothedAngles) {
        val smoothed = smoothedAngles[key]?.let { previous -> previous + ANGLE_SMOOTHING_ALPHA * (value - previous) }
            ?: value
        smoothedAngles[key] = smoothed
        smoothed
    }

    /** Angle between each finger's MCP-to-tip direction vectors, in degrees. */
    private fun getInterFingerAngles(
        landmarks: List<NormalizedLandmark>
    ): Map<String, Double> {
        fun direction(from: Int, to: Int): DoubleArray {
            val start = landmarks[from]
            val end = landmarks[to]
            return doubleArrayOf(
                (end.x() - start.x()).toDouble(),
                (end.y() - start.y()).toDouble(),
                (end.z() - start.z()).toDouble()
            )
        }

        val directions = linkedMapOf(
            "thumb" to direction(2, 4),
            "index" to direction(5, 8),
            "middle" to direction(9, 12),
            "ring" to direction(13, 16),
            "pinky" to direction(17, 20)
        )

        fun angleBetween(first: DoubleArray, second: DoubleArray): Double {
            val dot = first.indices.sumOf { first[it] * second[it] }
            val firstLength = kotlin.math.sqrt(first.sumOf { it * it })
            val secondLength = kotlin.math.sqrt(second.sumOf { it * it })
            if (firstLength == 0.0 || secondLength == 0.0) return 0.0
            val cosine = (dot / (firstLength * secondLength)).coerceIn(-1.0, 1.0)
            return Math.toDegrees(kotlin.math.acos(cosine))
        }

        val result = linkedMapOf<String, Double>()
        val names = directions.keys.toList()
        for (index in 0 until names.lastIndex) {
            val first = names[index]
            val second = names[index + 1]
            result["$first-$second"] = angleBetween(directions.getValue(first), directions.getValue(second))
        }
        result["thumb-pinky"] = angleBetween(
            directions.getValue("thumb"),
            directions.getValue("pinky")
        )
        return result
    }

    /** Returns joint flexion: 0° is straight and larger values indicate more bending. */
    private fun flexionDegrees(
        a: NormalizedLandmark,
        joint: NormalizedLandmark,
        c: NormalizedLandmark
    ): Double {
        val ax = a.x() - joint.x()
        val ay = a.y() - joint.y()
        val az = a.z() - joint.z()
        val cx = c.x() - joint.x()
        val cy = c.y() - joint.y()
        val cz = c.z() - joint.z()
        val dot = ax * cx + ay * cy + az * cz
        val aLength = kotlin.math.sqrt(ax * ax + ay * ay + az * az)
        val cLength = kotlin.math.sqrt(cx * cx + cy * cy + cz * cz)
        if (aLength == 0f || cLength == 0f) return 0.0
        val cosine = (dot / (aLength * cLength)).coerceIn(-1f, 1f)
        val interiorAngle = Math.toDegrees(kotlin.math.acos(cosine).toDouble())
        return 180.0 - interiorAngle
    }

    private fun Float.format3(): String = String.format(Locale.US, "%.3f", this)
    private fun Double.format1(): String = String.format(Locale.US, "%.1f", this)
    private fun Float.format2(): String = String.format(Locale.US, "%.2f", this)

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == HandLandmarkerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    HandLandmarkerHelper.DELEGATE_CPU, false
                )
            }
        }
    }
}
