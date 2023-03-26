package com.shubham0204.ml.mediapipehandsdemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {

    private val cameraPermissionEnabled = MutableLiveData( false )
    private val controlsVisible = MutableLiveData( true )
    private val controlsVisibilityHandler = Handler( Looper.getMainLooper() )
    private val controlsVisibilityTimeout = 6000L

    private val drawColorName = MutableLiveData( Color.Black )
    private val fingerPosition = MutableLiveData<FloatArray>()
    private val brushManager = BrushManager()
    private lateinit var frameAnalyzer : FrameAnalyzer

    private var layoutWidth  = 0
    private var layoutHeight = 0

    private val backgroundTint = Color( 3 , 240 , 240 , 200 )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ActivityUI()
        }
        startControlsVisibilityTimer()
        frameAnalyzer = FrameAnalyzer( this , resultCallback )
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            frameAnalyzer.setupHandLandmarker()
        }

        // Enable fullscreen mode for immersive experience
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController!!
                .hide( WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
        else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }

        // Check if user has granted the CAMERA permission, else request it
        if ( ActivityCompat.checkSelfPermission( this , Manifest.permission.CAMERA )
            != PackageManager.PERMISSION_GRANTED ) {
            requestCameraPermission()
        }
        else {
            cameraPermissionEnabled.value = true
        }

    }

    private val resultCallback = { pos : FloatArray ->
        val job = CoroutineScope( Dispatchers.Main ).launch {
            fingerPosition.value = pos
        }
    }

    @Composable
    private fun ActivityUI() {
        ConstraintLayout {
            val preview = createRef()
            Camera( modifier = Modifier.constrainAs( preview ) {
                absoluteLeft.linkTo( parent.absoluteLeft )
                absoluteRight.linkTo( parent.absoluteRight )
                top.linkTo( parent.top )
                bottom.linkTo( parent.bottom )
            } )
            DrawingControls()
        }
    }

    @Composable
    private fun DrawingControls() {
        val areControlsVisible by controlsVisible.observeAsState()
        val cameraPermission by cameraPermissionEnabled.observeAsState()
        // Display drawing controls only when areControlsVisible = true and cameraPermission = true
        AnimatedVisibility( visible = (areControlsVisible ?: true) && (cameraPermission ?: false) ,
            enter = slideInVertically() ,
            exit = slideOutVertically()
        ) {
            ConstraintLayout( modifier = Modifier.fillMaxWidth() ) {
                val ( colorPicker , exportButton ) = createRefs()
                // ColorPicker composable that shows color patches
                ColorPicker(
                    modifier = Modifier
                        .constrainAs(colorPicker) {
                            absoluteRight.linkTo(parent.absoluteRight)
                            top.linkTo(parent.top)
                        }
                        .padding(16.dp)
                )
                // 'Share' button which lets users share their drawings
                Button(
                    onClick = { shareImage() } ,
                    modifier = Modifier
                        .constrainAs(exportButton) {
                            absoluteLeft.linkTo(parent.absoluteLeft)
                            top.linkTo(parent.top)
                        }
                        .padding(16.dp) ,
                    colors = ButtonDefaults.buttonColors( containerColor = Color.White , contentColor = Color.Blue )
                ) {
                    Icon( imageVector=Icons.Default.Share , contentDescription="Share drawing" )
                    Text(text = "Share" )
                }
            }

        }
    }


    @Composable
    private fun ColorPicker( modifier: Modifier ) {
        Row( modifier = modifier ) {
            ColorPatch(color = Color.Red)
            ColorPatch(color = Color.Yellow)
            ColorPatch(color = Color.Blue)
            ColorPatch(color = Color.Black)
            ColorPatch(color = Color.Green)
        }
    }

    @Composable
    private fun ColorPatch(color : Color ) {
        Canvas(modifier = Modifier
            .size(32.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        drawColorName.value = color
                    }
                )
            },
            onDraw =  {
            if( color == drawColorName.value ) {
                drawCircle( color = Color.White , radius = 10.dp.toPx() )
            }
            drawCircle( color = color , radius = 8.dp.toPx() )
        })
    }

    @Composable
    private fun Camera( modifier: Modifier ) {
        val cameraPermissionState by cameraPermissionEnabled.observeAsState()
        // Show CameraPermissionStatus if cameraPermissionState = false i.e. user hasn't granted CAMERA permission
        // Else, show the CameraPreview
        CameraPreview(modifier = modifier, isVisible = cameraPermissionState ?: false )
        CameraPermissionStatus(isVisible = !(cameraPermissionState ?: false))
    }

    @Composable
    private fun CameraPreview( modifier: Modifier , isVisible : Boolean ) {
        AnimatedVisibility(visible = isVisible ) {
            CameraXPreview(  modifier.fillMaxSize() )
            DrawingBackground( modifier )
            DrawingOverlay()
        }
    }

    @Composable
    private fun CameraPermissionStatus( isVisible: Boolean ) {
        AnimatedVisibility(visible = isVisible) {
            Box( modifier = Modifier.fillMaxSize() ) {
                Column( modifier = Modifier.align( Alignment.Center )) {
                    Text( text = "Allow Camera Permissions" )
                    Text( text = "The app cannot work without the camera permission." )
                    Button(
                        onClick = { requestCameraPermission() } ,
                        modifier = Modifier.align( Alignment.CenterHorizontally )
                    ) {
                        Text(text = "Allow")
                    }
                }
            }
        }
    }

    @Composable
    private fun DrawingBackground( modifier: Modifier ) {
        // https://stackoverflow.com/a/66942801/13546426
        val context = LocalContext.current
        Surface(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible.value = true
                            startControlsVisibilityTimer()
                        },
                        onDoubleTap = {
                            brushManager.clear()
                            fingerPosition.value = FloatArray(4)
                            Toast
                                .makeText(context, "Screen cleared.", Toast.LENGTH_SHORT)
                                .show()
                        }
                    )
                },
            color = backgroundTint
        ) {}
    }

    @Composable
    private fun DrawingOverlay() {
        val position by fingerPosition.observeAsState()
        Spacer(
            modifier= Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    layoutHeight = it.size.height
                    layoutWidth = it.size.width
                    frameAnalyzer.setLayoutDims(layoutWidth, layoutHeight)
                }
                .drawWithCache {
                    val drawPos = position ?: FloatArray(4)
                    val fingerPosition = HandLandmarks(
                        PointF(drawPos[0], drawPos[1]),
                        PointF(drawPos[2], drawPos[3])
                    )
                    brushManager.nextPoints(fingerPosition, drawColorName.value ?: Color.Blue)
                    onDrawBehind {
                        for (brushPath in brushManager.getAllStrokes()) {
                            drawPath(
                                path = brushPath.path,
                                color = brushPath.pathColor,
                                style = Stroke( 3.dp.toPx() )
                            )
                        }
                        drawPath(
                            brushManager.getCurrentStroke().path,
                            color = drawColorName.value ?: Color.Black,
                            style = Stroke(3.dp.toPx())
                        )
                        drawCircle(
                            color = if (brushManager.isDrawing) {
                                drawColorName.value ?: Color.Black
                            } else {
                                Color.White
                            },
                            radius = 10.0f,
                            center = Offset(drawPos[0].toFloat(), drawPos[1].toFloat())
                        )
                        drawCircle(
                            color = if (brushManager.isDrawing) {
                                drawColorName.value ?: Color.Black
                            } else {
                                Color.White
                            },
                            radius = 10.0f,
                            center = Offset(drawPos[2].toFloat(), drawPos[3].toFloat())
                        )
                    }
                } ,
        )
    }

    @Composable
    private fun CameraXPreview(modifier: Modifier ) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current
        val cameraProviderFuture = remember{ ProcessCameraProvider.getInstance( context ) }
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView( ctx )
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build()
                    val handAnalysis = ImageAnalysis.Builder()
                        .setTargetAspectRatio( AspectRatio.RATIO_16_9 )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat( ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888 )
                        .build()
                    handAnalysis.setAnalyzer( Executors.newSingleThreadExecutor() , frameAnalyzer )
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview ,
                        handAnalysis
                    )
                }, executor)
                previewView
            } ,
            modifier = modifier
        )
    }


    private fun startControlsVisibilityTimer() {
        controlsVisibilityHandler.removeCallbacks( switchControlsVisibilityRunnable )
        controlsVisibilityHandler.postDelayed( switchControlsVisibilityRunnable , controlsVisibilityTimeout )
    }

    private val switchControlsVisibilityRunnable = Runnable {
        controlsVisible.value = false
    }

    private fun shareImage() {
        CoroutineScope( Dispatchers.IO ).launch {
            val outputBitmap = Bitmap.createBitmap( layoutWidth , layoutHeight , Bitmap.Config.ARGB_8888 )
            val canvas = Canvas( outputBitmap )
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 5.0f
            }
            canvas.drawColor( android.graphics.Color.WHITE )
            for( brushPath in brushManager.getAllStrokes() ) {
                paint.color = android.graphics.Color.rgb(
                    ( brushPath.pathColor.red * 255 ).toInt() ,
                    ( brushPath.pathColor.green * 255 ).toInt() ,
                    ( brushPath.pathColor.blue * 255 ).toInt()
                )
                canvas.drawPath( brushPath.path.asAndroidPath() , paint)
            }
            val dirFile = File( filesDir , "saved_images" ).apply{
                if( !exists() ) {
                    mkdir()
                }
            }
            val tempFile = File( dirFile , "temp.png" )
            withContext( Dispatchers.IO ) {
                FileOutputStream( tempFile ).apply {
                    outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
                    close()
                }
            }
            withContext( Dispatchers.Main ) {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "image/png"
                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile( this@MainActivity ,
                    packageName , tempFile ))
                startActivity( intent )
            }
        }
    }


    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch( Manifest.permission.CAMERA )
    }

    private val cameraPermissionLauncher = registerForActivityResult( ActivityResultContracts.RequestPermission() ) {
            isGranted ->
        if ( isGranted ) {
            cameraPermissionEnabled.value = true
        }
    }

}


