package com.google.mediapipe.examples.llminference

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ModelLoadFailException :
    Exception("Failed to load model, please try again")

class InferenceModel private constructor(context: Context) {
    private var llmInference: LlmInference
    private var llmInferenceSession: LlmInferenceSession
    private val TAG = InferenceModel::class.qualifiedName

    private val _partialResults = MutableSharedFlow<Pair<String, Boolean>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val partialResults: SharedFlow<Pair<String, Boolean>> = _partialResults.asSharedFlow()
    val uiState: UiState

    init {
        if (!modelExists(context)) {
            throw IllegalArgumentException("Model not found at path: ${model.path}")
        }

        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath(context))
            .setMaxTokens(1024)
            .setResultListener { partialResult, done ->
                _partialResults.tryEmit(partialResult to done)
            }
            .build()

        val sessionOptions =  LlmInferenceSessionOptions.builder()
            .setTemperature(model.temperature)
            .setTopK(model.topK)
            .setTopP(model.topP)
            .build()

        uiState = model.uiState
        try {
            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
            llmInferenceSession =
                LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Load model error: ${e.message}", e)
            throw ModelLoadFailException()
        }
    }

    fun generateResponseAsync(prompt: String) {
        val formattedPrompt = model.uiState.formatPrompt(prompt)
        llmInferenceSession.addQueryChunk(formattedPrompt)
        llmInferenceSession.generateResponseAsync()
    }

    fun close() {
        llmInferenceSession.close()
        llmInference.close()
    }

    companion object {
        var model: Model = Model.GEMMA_CPU
        private var instance: InferenceModel? = null

        fun getInstance(context: Context): InferenceModel {
            return if (instance != null) {
                instance!!
            } else {
                InferenceModel(context).also { instance = it }
            }
        }

        fun resetInstance(context: Context): InferenceModel {
            return InferenceModel(context).also { instance = it }
        }

        fun modelPath(context: Context): String {
            val modelFile = File(model.path)
            val contextFile = File(context.filesDir, modelFile.name)

            return when {
                modelFile.exists() -> model.path
                contextFile.exists() -> contextFile.absolutePath
                else -> ""
            }
        }

        fun modelExists(context: Context): Boolean {
            return !modelPath(context).isEmpty()
        }
    }
}
