package com.google.mediapipe.examples.imagegeneration.helper

import android.R.attr.bitmap
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ImageGeneratorOptions
import java.lang.IllegalArgumentException


class ImageGenerationHelper(
    val context: Context
) {

    lateinit var imageGenerator: ImageGenerator

    // Setup image generation model with output size, iteration
    fun initializeImageGenerator() {
        Log.e("Test", "calling setup image generation")
        val options = ImageGeneratorOptions.builder().setImageGeneratorModelDirectory("/data/local/tmp/image_generator/bins/").build()
        imageGenerator = ImageGenerator.createFromOptions(context, options)
        Log.e("Test", "initialized!")
    }

    // Set input prompt, iteration, seed
    fun setInput(prompt: String, iteration: Int, seed: Int) {
        imageGenerator.setInputs(prompt, iteration, 0)
    }

    fun generate(): Bitmap {
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


//        val width = image!!.width
//        val height = image.height
//
//        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        bitmap.copyPixelsFromBuffer(byteBuffer)

        return bitmap
    }
}
