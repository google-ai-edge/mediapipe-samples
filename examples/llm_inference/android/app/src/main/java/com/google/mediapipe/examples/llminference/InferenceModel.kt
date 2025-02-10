package com.google.mediapipe.examples.llminference

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class InferenceModel private constructor(context: Context) {
    private var llmInference: LlmInference
    private var llmInferenceSession: LlmInferenceSession

    private val modelExists: Boolean
        get() = File(model.path).exists()

    private val _partialResults = MutableSharedFlow<Pair<String, Boolean>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val partialResults: SharedFlow<Pair<String, Boolean>> = _partialResults.asSharedFlow()
    val uiState: UiState

    init {
        if (!modelExists) {
            throw IllegalArgumentException("Model not found at path: ${model.path}")
        }

        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(model.path)
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
        llmInference = LlmInference.createFromOptions(context, inferenceOptions)
        llmInferenceSession = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
    }

    fun generateResponseAsync(prompt: String) {
        val formattedPrompt = model.uiState.formatPrompt(prompt)
        llmInferenceSession.addQueryChunk(formattedPrompt)
        llmInferenceSession.generateResponseAsync()
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
    }
}
