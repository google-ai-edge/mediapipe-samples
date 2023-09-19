package com.google.mediapipe.examples.imagegeneration

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.examples.imagegeneration.helper.ImageGenerationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    private var helper: ImageGenerationHelper? = null
    val uiState: StateFlow<UiState> = _uiState

    fun updateDisplayIteration(displayIteration: Int?) {
        _uiState.update { it.copy(displayIteration = displayIteration) }
    }

    fun updateOutputSize(outputSize: Int?) {
        _uiState.update { it.copy(outputSize = outputSize) }
    }

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun updateIteration(iteration: Int?) {
        _uiState.update { it.copy(iteration = iteration) }
    }

    fun updateSeed(seed: Int?) {
        _uiState.update { it.copy(seed = seed) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // Create image generation helper
    fun createImageGenerationHelper(context: Context) {
        val outputSize = _uiState.value.outputSize
        val displayIteration = _uiState.value.displayIteration

        try {
            if (outputSize == null) {
                _uiState.update { it.copy(error = "Output size cannot be empty") }
                return
            }
            if (displayIteration == null) {
                _uiState.update { it.copy(error = "Display iteration cannot be empty") }
                return
            }

            helper = ImageGenerationHelper(
                context, outputSize, displayIteration
            )

            _uiState.update {
                it.copy(
                    initialized = true,
                    initializedOutputSize = outputSize,
                    initializedDisplayIteration = displayIteration
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    error = e.message
                        ?: "Error initializing image generation model",
                )
            }
        }
    }

    fun generateImage() {
        val prompt = _uiState.value.prompt
        val iteration = _uiState.value.iteration
        val seed = _uiState.value.seed
        if (prompt.isEmpty()) {
            _uiState.update { it.copy(error = "Prompt cannot be empty") }
            return
        }
        if (iteration == null) {
            _uiState.update { it.copy(error = "Iteration cannot be empty") }
            return
        }
        if (seed == null) {
            _uiState.update { it.copy(error = "Seed cannot be empty") }
            return
        }
        _uiState.update { it.copy(isGenerating = true) }

        helper?.setInput(prompt, iteration, seed)

        viewModelScope.launch(Dispatchers.IO) {
            val displayIteration = _uiState.value.displayIteration ?: 0
            for (step in 0 until iteration) {

                val result =
                    helper?.execute(displayIteration > 0 && ((step + 1) % displayIteration == 0))

                // test this task with execution time 1s, should remove this line in real app
                delay(1000)

                _uiState.update {
                    it.copy(
                        generatingMessage = "Generating ... %.2f%%".format(
                            (step.toFloat() / iteration.toFloat() * 100f)
                        )
                    )
                }
                if (result != null) {
                    // extract bitmap and update here
                    _uiState.update { it.copy(outputBitmap = result) }
                }
            }
            _uiState.update {
                it.copy(
                    isGenerating = false,
                    generatingMessage = "Generate"
                )
            }
        }
    }
}

data class UiState(
    val error: String? = null,
    val outputBitmap: Bitmap? = null,
    val outputSize: Int? = null,
    val displayIteration: Int? = null,
    val prompt: String = "",
    val iteration: Int? = null,
    val seed: Int? = null,
    val initialized: Boolean = false,
    val initializedOutputSize: Int? = null,
    val initializedDisplayIteration: Int? = null,
    val isGenerating: Boolean = false,
    val generatingMessage: String = "",
)
