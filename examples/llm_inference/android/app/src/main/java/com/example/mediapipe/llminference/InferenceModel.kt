package com.example.mediapipe.llminference

import android.content.Context
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

class InferenceModel private constructor(context: Context) {
    private var llmInference: LlmInference

    private val modelExists: Boolean
        get() = File(MODEL_PATH).exists()

    init {
        if (!modelExists) {
            throw IllegalArgumentException("Model not found at path: $MODEL_PATH")
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(MODEL_PATH)
            .setDelegate(Delegate.GPU)
            .setNumDecodeStepsPerSync(3)
            .setMaxSequenceLength(1024)
            .setTopK(1)
            .setRandomSeed(0)
            .setTemperature(0f)
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
    }

    fun generateResponse(prompt: String): String {
        return llmInference.generateResponse(prompt)
    }

    companion object {
        private const val MODEL_PATH = "/data/local/tmp/llm/model_gpu.tflite"
        private var instance: InferenceModel? = null

        fun getInstance(context: Context): InferenceModel {
            return if (instance != null) {
                instance!!
            } else {
                InferenceModel(context).also { instance = it }
            }
        }
    }
}
