package com.shubham0204.ml.mediapipehandsdemo

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.sqrt

class BrushManager {

    private var newStrokeAdded = false
    private val strokes = ArrayList<BrushPath>()
    private var currentStroke = BrushPath()
    private val fingerDrawingThreshold = 60.0f

    private var x1 = 0.0f
    private var y1 = 0.0f
    private var x2 = 0.0f
    private var y2 = 0.0f
    private var midX = 0.0f
    private var midY = 0.0f
    private var distance = 0.0f

    var isDrawing = false

    fun nextPoints(positions : HandLandmarks, color : Color ) {
        x1 = positions.middleFinger.x
        y1 = positions.middleFinger.y
        x2 = positions.index.x
        y2 = positions.index.y
        distance = sqrt( ( x2 - x1 ).pow(2) + ( y2 - y1 ).pow(2) )
        if( distance < fingerDrawingThreshold  ) {
            midX = ( x1 + x2 ) / 2
            midY = ( y1 + y2 ) / 2
            if( newStrokeAdded && midX != 0.0f && midY != 0.0f ) {
                currentStroke.start( midX , midY )
                newStrokeAdded = false
            }
            if( x2 != 0.0f && y2 != 0.0f ) {
                isDrawing = true
                addPointToStroke( midX , midY )
            }
        }
        else {
            strokes.add( currentStroke )
            currentStroke = BrushPath()
            currentStroke.pathColor = color
            isDrawing = false
            newStrokeAdded = true
        }
    }

    fun getAllStrokes() : List<BrushPath> {
        return strokes
    }

    fun getCurrentStroke() : BrushPath {
        return currentStroke
    }

    fun clear() {
        strokes.clear()
        currentStroke.reset()
    }

    private fun addPointToStroke( x : Float , y : Float ) {
        currentStroke.addPoint( x , y )
    }

}