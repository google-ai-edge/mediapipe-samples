package com.google.mediapipe.examples.imagegeneration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ConditionOptions
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ConditionOptions.ConditionType
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ConditionOptions.EdgeConditionOptions
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ConditionOptions.FaceConditionOptions
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ImageGeneratorOptions


class ImageGenerationHelper(
    val context: Context
) {

    lateinit var imageGenerator: ImageGenerator

    // Setup image generation model with output size, iteration
    fun initializeImageGenerator(modelPath: String) {
        val options = ImageGeneratorOptions.builder().setImageGeneratorModelDirectory(modelPath)
            .build()

        imageGenerator = ImageGenerator.createFromOptions(context, options)
    }

    fun initializeLoRAWeightGenerator(modelPath: String, weightsPath: String) {
        val options = ImageGeneratorOptions.builder()
            .setLoraWeightsFilePath(weightsPath)
            .setImageGeneratorModelDirectory(modelPath)
            .build()

        imageGenerator = ImageGenerator.createFromOptions(context, options)
    }

    fun initializeFaceImageGenerator(modelPath: String) {
        val options = ImageGeneratorOptions.builder().setImageGeneratorModelDirectory(modelPath)
            .build()

        val faceModelBaseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val pluginModelBaseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmark_plugin.tflite")
            .build()

        val faceConditionOptions = FaceConditionOptions.builder()
            .setFaceModelBaseOptions(faceModelBaseOptions)
            .setPluginModelBaseOptions(pluginModelBaseOptions)
            .setMinFaceDetectionConfidence(0.3f)
            .setMinFacePresenceConfidence(0.3f)
            .build()

        val conditionOptions = ImageGenerator.ConditionOptions.builder().setFaceConditionOptions(faceConditionOptions).build()
        imageGenerator = ImageGenerator.createFromOptions(context, options, conditionOptions)
    }

    fun initializeEdgeImageGenerator(modelPath: String) {
        val options = ImageGeneratorOptions.builder().setImageGeneratorModelDirectory(modelPath)
            .build()

        val pluginModelBaseOptions = BaseOptions.builder()
            .setModelAssetPath("canny_edge_plugin.tflite")
            .build()

        val edgeConditionOptions = EdgeConditionOptions.builder()
            .setThreshold1(100.0f) // default = 100.0f
            .setThreshold2(100.0f) // default = 100.0f
            .setApertureSize(3) // default = 3
            .setL2Gradient(false) // default = false
            .setPluginModelBaseOptions(pluginModelBaseOptions)
            .build()

        val conditionOptions = ConditionOptions.builder().setEdgeConditionOptions(edgeConditionOptions).build()
        imageGenerator = ImageGenerator.createFromOptions(context, options, conditionOptions)
    }

    fun initializeDepthImageGenerator(modelPath: String) {

        val options = ImageGeneratorOptions.builder().setImageGeneratorModelDirectory(modelPath)
            .build()

        val depthModelBaseOptions = BaseOptions.builder()
            .setModelAssetPath("depth_model.tflite")
            .build()

        val pluginModelBaseOptions = BaseOptions.builder()
            .setModelAssetPath("depth_plugin.tflite")
            .build()

        val depthConditionOptions = ImageGenerator.ConditionOptions.DepthConditionOptions.builder()
            .setDepthModelBaseOptions(depthModelBaseOptions)
            .setPluginModelBaseOptions(pluginModelBaseOptions)
            .build()

        val conditionOptions = ImageGenerator.ConditionOptions.builder().setDepthConditionOptions(depthConditionOptions).build()
        imageGenerator = ImageGenerator.createFromOptions(context, options, conditionOptions)
    }

    fun setInput(prompt: String, conditionalImage: MPImage, conditionType: ConditionType, iteration: Int, seed: Int) {
        imageGenerator.setInputs(prompt, conditionalImage, conditionType, iteration, seed)
    }

    // Set input prompt, iteration, seed
    fun setInput(prompt: String, iteration: Int, seed: Int) {
        imageGenerator.setInputs(prompt, iteration, seed)
    }


    fun generate(): Bitmap {
        // TODO Needs update for fix
        val result = imageGenerator.generate("purple teapot sitting on a green table", 10, 0)
        val bitmap = BitmapExtractor.extract(result?.generatedImage())
        return bitmap
    }

    fun execute(showResult: Boolean): Bitmap {
        // execute image generation model
        val result = imageGenerator.execute(showResult)

//        val image = result?.generatedImage()
        if( result == null || result.generatedImage() == null ) {
            return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                val paint = Paint()
                paint.color = Color.WHITE
                canvas.drawPaint(paint)
            }
        }

        val bitmap =
            BitmapExtractor.extract(result?.generatedImage())

        return bitmap
    }
}
