package com.google.mediapipe.examples.imagegeneration.helper

import android.R.attr.bitmap
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.lang.IllegalArgumentException


class ImageGenerationHelper(
    val context: Context,
    val outputSize: Int,
    val displayIteration: Int
) {
    init {
        // Initialize image generation model
        setupImageGeneration()
    }

    // Setup image generation model with output size, iteration
    private fun setupImageGeneration() {
        throw IllegalArgumentException("Not implemented yet")
    }

    // Set input prompt, iteration, seed
    fun setInput(prompt: String, iteration: Int, seed: Int) {

    }

    fun execute(state: Boolean): Bitmap {
        // execute image generation model

        // test bitmap, should remove later
        return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            val paint = Paint()
            paint.color = Color.RED
            canvas.drawPaint(paint)
        }
    }
}
