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