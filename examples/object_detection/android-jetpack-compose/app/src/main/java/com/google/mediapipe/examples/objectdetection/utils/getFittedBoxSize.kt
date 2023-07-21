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
package com.google.mediapipe.examples.objectdetection.utils

import androidx.compose.ui.geometry.Size

// This function is used to calculate the size of a box after scaling it down to be fitted in a
// container of arbitrary size while preserving the aspect ration of the box

fun getFittedBoxSize(containerSize: Size, boxSize: Size): Size {
    // To achieve the "fitting" behaviour, we need to take into consideration the aspect ratio
    // of the container, and the aspect ratio of the box. We have two cases:

    // The box aspect ratio is wider than the container aspect ratio, in which case we
    // need to set the box width to max available width (i.e, the container width), and
    // scale down the height of the box to retain the same proportion of the original box

    // The box aspect ratio is taller than the container aspect ratio, in which case we
    // need to set the box height to max available height (i.e, the container height), and
    // scale down the width of the box to retain the same proportion of the original box

    val boxAspectRatio = boxSize.width / boxSize.height
    val containerAspectRatio = containerSize.width / containerSize.height

    return if (boxAspectRatio > containerAspectRatio) {
        Size(
            width = containerSize.width,
            height =  (containerSize.width / boxSize.width) * boxSize.height,
        )
    } else {
        Size(
            height = containerSize.height,
            width =  (containerSize.height / boxSize.height) * boxSize.width,
        )
    }
}