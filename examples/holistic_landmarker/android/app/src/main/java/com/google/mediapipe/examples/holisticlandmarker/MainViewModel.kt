package com.google.mediapipe.examples.holisticlandmarker

/*
 * Copyright 2024 The TensorFlow Authors. All Rights Reserved.
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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    val helperState: MutableLiveData<HelperState> = MutableLiveData(
        HelperState()
    )

    fun setMinFaceLandmarkConfidence(confidence: Float) {
        helperState.value = helperState.value?.copy(
            minFacePresenceThreshold = confidence
        )
    }

    fun setMinHandLandmarkConfidence(confidence: Float) {
        helperState.value =
            helperState.value?.copy(
                minHandLandmarkThreshold = confidence
            )
    }

    fun setMinPoseLandmarkConfidence(confidence: Float) {
        helperState.value =
            helperState.value?.copy(
                minPosePresenceThreshold = confidence
            )
    }

    fun setMinFaceDetectionConfidence(confidence: Float) {
        helperState.value =
            helperState.value?.copy(
                minFaceDetectionThreshold = confidence
            )
    }

    fun setMinPoseDetectionConfidence(confidence: Float) {
        helperState.value =
            helperState.value?.copy(
                minPoseDetectionThreshold = confidence
            )
    }

    fun setMinPoseSuppressionConfidence(confidence: Float) {
        helperState.value =
            helperState.value?.copy(
                minPoseSuppressionThreshold = confidence
            )
    }

    fun setMinFaceSuppressionConfidence(confidence: Float) {
        helperState.value =
            helperState.value?.copy(
                minFaceSuppressionThreshold = confidence
            )
    }

    fun setFaceBlendMode(faceBlendMode: Boolean) {
        helperState.value =
            helperState.value?.copy(isFaceBlendMode = faceBlendMode)
    }

    fun setPoseSegmentationMarks(poseSegmentationMarks: Boolean) {
        helperState.value =
            helperState.value?.copy(isPoseSegmentationMarks = poseSegmentationMarks)
    }

    fun setDelegate(delegate: Int) {
        helperState.value = helperState.value?.copy(delegate = delegate)
    }
}

data class HelperState(
    val delegate: Int = HolisticLandmarkerHelper.DELEGATE_CPU,
    val minFacePresenceThreshold: Float = HolisticLandmarkerHelper.DEFAULT_MIN_FACE_PRESENCE_CONFIDENCE,
    val minHandLandmarkThreshold: Float = HolisticLandmarkerHelper.DEFAULT_MIN_HAND_LANDMARKS_CONFIDENCE,
    val minPosePresenceThreshold: Float = HolisticLandmarkerHelper.DEFAULT_MIN_POSE_PRESENCE_CONFIDENCE,
    val minFaceDetectionThreshold: Float = HolisticLandmarkerHelper.DEFAULT_MIN_FACE_DETECTION_CONFIDENCE,
    val minPoseDetectionThreshold: Float = HolisticLandmarkerHelper.DEFAULT_MIN_POSE_DETECTION_CONFIDENCE,
    val minPoseSuppressionThreshold: Float = HolisticLandmarkerHelper.DEFAULT_MIN_POSE_SUPPRESSION_THRESHOLD,
    val minFaceSuppressionThreshold: Float = HolisticLandmarkerHelper.DEFAULT_MIN_FACE_SUPPRESSION_THRESHOLD,
    val isFaceBlendMode: Boolean = HolisticLandmarkerHelper.DEFAULT_FACE_BLEND_SHAPES,
    val isPoseSegmentationMarks: Boolean = HolisticLandmarkerHelper.DEFAULT_POSE_SEGMENTATION_MARK,
)
