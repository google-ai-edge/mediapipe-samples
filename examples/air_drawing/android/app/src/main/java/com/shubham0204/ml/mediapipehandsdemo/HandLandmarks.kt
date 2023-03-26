package com.shubham0204.ml.mediapipehandsdemo

import android.graphics.PointF

// Data class which holds landmarks for the thumb and index finger
data class HandLandmarks(
    val middleFinger : PointF,
    val index : PointF
) {

    constructor() : this( PointF( 0.0f , 0.0f ) , PointF( 0.0f , 0.0f ))

    override fun toString(): String {
        return "Middle Finger: $middleFinger  Index: $index"
    }

}