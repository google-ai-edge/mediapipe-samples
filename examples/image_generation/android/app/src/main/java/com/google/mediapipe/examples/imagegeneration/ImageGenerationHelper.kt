package com.google.mediapipe.examples.imagegeneration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
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
        val options = ImageGeneratorOptions.builder()
            .setImageGeneratorModelDirectory(modelPath)
            .build()

        imageGenerator = ImageGenerator.createFromOptions(context, options)
    }

    fun initializeImageGeneratorWithFacePlugin(modelPath: String) {
        val options = ImageGeneratorOptions.builder()
            .setImageGeneratorModelDirectory(modelPath)
            .build()

        val faceModelBaseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val facePluginModelBaseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmark_plugin.tflite")
            .build()

        val faceConditionOptions = FaceConditionOptions.builder()
            .setFaceModelBaseOptions(faceModelBaseOptions)
            .setPluginModelBaseOptions(facePluginModelBaseOptions)
            .setMinFaceDetectionConfidence(0.3f)
            .setMinFacePresenceConfidence(0.3f)
            .build()

        val conditionOptions = ConditionOptions.builder()
            .setFaceConditionOptions(faceConditionOptions)
            .build()

        imageGenerator =
            ImageGenerator.createFromOptions(context, options, conditionOptions)
    }

    fun initializeImageGeneratorWithEdgePlugin(modelPath: String) {
        val options = ImageGeneratorOptions.builder()
            .setImageGeneratorModelDirectory(modelPath)
            .build()

        val edgePluginModelBaseOptions = BaseOptions.builder()
            .setModelAssetPath("canny_edge_plugin.tflite")
            .build()

        val edgeConditionOptions = EdgeConditionOptions.builder()
            .setThreshold1(100.0f) // default = 100.0f
            .setThreshold2(100.0f) // default = 100.0f
            .setApertureSize(3) // default = 3
            .setL2Gradient(false) // default = false
            .setPluginModelBaseOptions(edgePluginModelBaseOptions)
            .build()

        val conditionOptions = ConditionOptions.builder()
            .setEdgeConditionOptions(edgeConditionOptions)
            .build()

        imageGenerator =
            ImageGenerator.createFromOptions(context, options, conditionOptions)
    }

    fun initializeImageGeneratorWithDepthPlugin(modelPath: String) {
        val options = ImageGeneratorOptions.builder()
            .setImageGeneratorModelDirectory(modelPath)
            .build()

        val depthModelBaseOptions = BaseOptions.builder()
            .setModelAssetPath("depth_model.tflite")
            .build()

        val depthPluginModelBaseOptions = BaseOptions.builder()
            .setModelAssetPath("depth_plugin.tflite")
            .build()

        val depthConditionOptions =
            ConditionOptions.DepthConditionOptions.builder()
                .setDepthModelBaseOptions(depthModelBaseOptions)
                .setPluginModelBaseOptions(depthPluginModelBaseOptions)
                .build()

        val conditionOptions = ConditionOptions.builder()
            .setDepthConditionOptions(depthConditionOptions)
            .build()

        imageGenerator =
            ImageGenerator.createFromOptions(context, options, conditionOptions)
    }

    fun initializeLoRAWeightGenerator(modelPath: String, weightsPath: String) {
        val options = ImageGeneratorOptions.builder()
            .setLoraWeightsFilePath(weightsPath)
            .setImageGeneratorModelDirectory(modelPath)
            .build()

        imageGenerator = ImageGenerator.createFromOptions(context, options)
    }

    fun setInput(
        prompt: String,
        conditionalImage: MPImage,
        conditionType: ConditionType,
        iteration: Int,
        seed: Int
    ) {
        imageGenerator.setInputs(
            prompt,
            conditionalImage,
            conditionType,
            iteration,
            seed
        )
    }

    // Set input prompt, iteration, seed
    fun setInput(prompt: String, iteration: Int, seed: Int) {
        imageGenerator.setInputs(prompt, iteration, seed)
    }


    fun generate(prompt: String, iteration: Int, seed: Int): Bitmap {
        val result = imageGenerator.generate(prompt, iteration, seed)
        val bitmap = BitmapExtractor.extract(result?.generatedImage())
        return bitmap
    }

    fun generate(
        prompt: String,
        inputImage: MPImage,
        conditionType: ConditionType,
        iteration: Int,
        seed: Int
    ): Bitmap {
        val result = imageGenerator.generate(
            prompt,
            inputImage,
            conditionType,
            iteration,
            seed
        )
        val bitmap = BitmapExtractor.extract(result?.generatedImage())
        return bitmap
    }

    fun execute(showResult: Boolean): Bitmap {
        // execute image generation model
        val result = imageGenerator.execute(showResult)

        if (result == null || result.generatedImage() == null) {
            return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                .apply {
                    val canvas = Canvas(this)
                    val paint = Paint()
                    paint.color = Color.WHITE
                    canvas.drawPaint(paint)
                }
        }

        val bitmap =
            BitmapExtractor.extract(result.generatedImage())

        return bitmap
    }

    fun createConditionImage(
        inputImage: MPImage,
        conditionType: ConditionType
    ): Bitmap {
        return BitmapExtractor.extract(imageGenerator.createConditionImage(inputImage, conditionType))
    }

    fun close() {
        try {
            imageGenerator.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
