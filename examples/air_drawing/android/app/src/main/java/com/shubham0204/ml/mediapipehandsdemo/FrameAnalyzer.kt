package com.shubham0204.ml.mediapipehandsdemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.core.ErrorListener
import com.google.mediapipe.tasks.core.OutputHandler
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class FrameAnalyzer( private val context : Context , private val handLandmarksResult : ( FloatArray ) -> Unit )
    : ImageAnalysis.Analyzer {

    private var isProcessing = false
    private var handLandmarker : HandLandmarker? = null
    private var layoutHeight = 0
    private var layoutWidth = 0
    private var isOverlayTransformInitialized = false
    private val overlayTransform = Matrix()

    private val resultListener = OutputHandler.ResultListener<HandLandmarkerResult, MPImage>{
            result, input ->
        for( handResult in result.landmarks() ) {
            // handResult[8]  -> Indicates INDEX_FINGER_TIP landmark
            // handResult[12] -> Indicates MIDDLE_FINGER_TIP landmark
            // landmark1 and landmark2 have normalized coordinates (lie in range [0, 1])
            // We multiply x-coordinates with the width of the screen
            // and y-coordinates with the height of the screen
            // to obtain coordinates for the screen
            handLandmarksResult( floatArrayOf(
                ( handResult[12].x() * layoutWidth  ),
                ( handResult[12].y() * layoutHeight ) ,
                ( handResult[8].x() * layoutWidth  ) ,
                ( handResult[8].y() * layoutHeight ) )  )
        }
        if( result.landmarks().size == 0 ) {
            // If no landmarks are detected, pass an array of zeros
            handLandmarksResult( FloatArray( 4 ) )
        }
        isProcessing = false
    }

    private val errorListener = ErrorListener {
        // Handle MediaPipe errors here
        Log.e( context.getString( R.string.app_name ) , "MediaPipe Error: $it")
    }

    fun setupHandLandmarker() {
        // Build the HandLandmarker solution
        // The corresponding .task file is placed in app/src/main/assets
        val baseOptionsBuilder = BaseOptions.builder()
            .setDelegate( Delegate.GPU )
            .setModelAssetPath( "hand_landmarker.task" )

        val baseOptions = baseOptionsBuilder.build()
        val optionsBuilder =
            HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumHands( 1 )
                .setResultListener( resultListener )
                .setErrorListener( errorListener )
                .setRunningMode(RunningMode.LIVE_STREAM)
        val options = optionsBuilder.build()
        handLandmarker = HandLandmarker.createFromOptions( context , options )
    }

    override fun analyze(image: ImageProxy) {
        if( isProcessing || handLandmarker == null ) {
            // An image is currently in processing, close the current image and return
            image.close()
            return
        }
        isProcessing = true

        var bitmapBuffer = Bitmap.createBitmap( image.width , image.height, Bitmap.Config.ARGB_8888 )
        image.use{ bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        // Initialize image-to-overlay transformation
        // This requires the camera frame's width and height
        if( !isOverlayTransformInitialized ) {
            overlayTransform.apply {
                // Rotate the points to compensate for image rotation
                postRotate( image.imageInfo.rotationDegrees.toFloat())
                // Mirror the points as front-camera is being used
                postScale(-1f, 1f, image.width.toFloat(), image.height.toFloat() )
            }
            isOverlayTransformInitialized = true
        }
        image.close()

        bitmapBuffer = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            overlayTransform, false)

        handLandmarker!!.detectAsync( BitmapImageBuilder( bitmapBuffer ).build() , System.currentTimeMillis() )

    }

    fun setLayoutDims( width : Int , height : Int ) {
        layoutHeight = height
        layoutWidth = width
    }

}