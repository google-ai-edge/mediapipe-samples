package com.shubham0204.ml.mediapipehandsdemo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.pow
import kotlin.math.sqrt

// BrushPath -> A wrapper class around androidx.compose.ui.graphics.Path
// to provide quadratic curve approximation and eliminate drawing noise
class BrushPath {

    private var prevPosX = 0.0f
    private var prevPosY = 0.0f
    private var midX = 0.0f
    private var midY = 0.0f
    private var distance = 0.0f

    // Threshold which determines whether a curve should be drawn from ( prevPosX , prevPosY ) to ( x , y )
    // Smaller the value, greater is the freedom to draw intricate strokes
    private val distanceThreshold = 10.0f

    var path = Path()
    var pathColor = Color.Blue


    fun start( x : Float , y : Float ) {
        prevPosX = x
        prevPosY = y
        path.moveTo( x , y )
    }

    fun addPoint( x : Float , y : Float ) {
        distance = sqrt( ( x - prevPosX ).pow(2) + ( y - prevPosY ).pow(2) )
        // Check if distance from previous point is greater than a predefined threshold
        // It asserts that random fluctuations in MediaPipe predictions are not drawn on
        // the screen - eliminate jitterness
        if ( distance > distanceThreshold ) {
            midX = ( prevPosX + x ) / 2
            midY = ( prevPosY + y ) / 2
            // Perform Bezier interpolation to achieve smoother curves
            path.quadraticBezierTo(prevPosX, prevPosY, midX, midY)
            prevPosX = x
            prevPosY = y
        }
    }

    fun reset() {
        path = Path()
        prevPosX = 0.0f
        prevPosY = 0.0f
        midX = 0.0f
        midY = 0.0f
    }

}