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
    }

    fun setInput(prompt: String, iteration: Int, seed: Int) {
        // Step 3 - accept inputs
    }


    fun generate(prompt: String, iteration: Int, seed: Int): Bitmap {
        // Step 4 - generate without showing iterations
        return Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    }

    fun execute(showResult: Boolean): Bitmap {
        // Step 5 - generate with iterations
        return Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    }

    fun close() {
        try {
            imageGenerator.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
