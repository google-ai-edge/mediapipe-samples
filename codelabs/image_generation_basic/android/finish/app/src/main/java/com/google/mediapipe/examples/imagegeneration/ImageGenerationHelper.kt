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

    fun initializeImageGenerator(modelPath: String) {
        // Step 2 - initialize the image generator
        val options = ImageGeneratorOptions.builder()
            .setImageGeneratorModelDirectory(modelPath)
            .build()

        imageGenerator = ImageGenerator.createFromOptions(context, options)
    }

    fun setInput(prompt: String, iteration: Int, seed: Int) {
        // Step 3 - accept inputs
        imageGenerator.setInputs(prompt, iteration, seed)
    }


    fun generate(prompt: String, iteration: Int, seed: Int): Bitmap {
        // Step 4 - generate without showing iterations
        val result = imageGenerator.generate(prompt, iteration, seed)
        val bitmap = BitmapExtractor.extract(result?.generatedImage())
        return bitmap
    }

    fun execute(showResult: Boolean): Bitmap {
        // Step 5 - generate with iterations
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

    fun close() {
        try {
            imageGenerator.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
