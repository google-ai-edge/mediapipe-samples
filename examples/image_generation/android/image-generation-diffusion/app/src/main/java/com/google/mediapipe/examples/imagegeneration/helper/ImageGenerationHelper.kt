package com.google.mediapipe.examples.imagegeneration.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ConditionOptions.ConditionType
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

        Log.e("Test", "initialized!")
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