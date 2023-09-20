package com.google.mediapipe.examples.imagegeneration

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import com.google.mediapipe.examples.imagegeneration.helper.ImageGenerationHelper
import kotlinx.coroutines.GlobalScope
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

    fun updateDisplayOptions(displayOptions: DisplayOptions) {
        _uiState.update { it.copy(displayOptions = displayOptions) }
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

    fun updatePlugin(plugin: Int) {
        _uiState.update {
            it.copy(
                plugins = when (plugin) {
                    0 -> Plugins.FACE
                    1 -> Plugins.DEPTH
                    2 -> Plugins.EDGE
                    else -> throw IllegalArgumentException("Invalid plugin")
                }
            )
        }
    }

    fun updateInputBitmap(bitmap: Bitmap) {
        _uiState.update { it.copy(inputBitmap = bitmap) }
    }

    fun updateUseLora(useLora: Boolean) {
        _uiState.update { it.copy(useLora = useLora) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun initializeImageGenerator() {
        val displayIteration = _uiState.value.displayIteration
        val displayOptions = _uiState.value.displayOptions
        try {
            if (displayIteration == null && displayOptions == DisplayOptions.ITERATION) {
                _uiState.update { it.copy(error = "Display iteration cannot be empty") }
                return
            }

            _uiState.update { it.copy(isInitializing = true) }
            val mainLooper = Looper.getMainLooper()
            GlobalScope.launch {
                helper?.initializeImageGenerator()
                Handler(mainLooper).post {
                    _uiState.update {
                        it.copy(
                            initialized = true, isInitializing = false
                        )
                    }
                }
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

    // Create image generation helper
    fun createImageGenerationHelper(context: Context) {
        helper = ImageGenerationHelper(context)
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

        // Generate without iterations
//        val mainLooper = Looper.getMainLooper()
//        GlobalScope.launch {
//            val image = helper?.generate()
//            Handler(mainLooper).post {
//                _uiState.update {
//                    it.copy(
//                        isGenerating = false,
//                        outputBitmap = image
//                    )
//                }
//            }


        // Generate with iterations
        val mainLooper = Looper.getMainLooper()
        val tmpIteration = 10
        _uiState.update { it.copy(displayIteration = 1) }
        GlobalScope.launch {

            // if display option is final, use generate method, else use execute method
            if (uiState.value.displayOptions == DisplayOptions.FINAL) {
                val result = helper?.generate()
                _uiState.update {
                    it.copy(outputBitmap = result)
                }
            } else {
                helper?.setInput(prompt, tmpIteration, seed)

                val displayIteration = _uiState.value.displayIteration ?: 0
                for (step in 0 until tmpIteration) {

                    val result =
                        helper?.execute((displayIteration > 0 && ((step + 1) % displayIteration == 0)))

                    _uiState.update {
                        it.copy(
                            generatingMessage = "Generating ... %.2f%%".format(
                                (step.toFloat() / iteration.toFloat() * 100f)
                            ), outputBitmap = result
                        )
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isGenerating = false, generatingMessage = "Generate"
                )
            }
//
        }
    }

    fun resetUiState() {
        _uiState.update {
            UiState()
        }
        helper = null
    }
}

data class UiState(
    val error: String? = null,
    val inputBitmap: Bitmap? = null,
    val outputBitmap: Bitmap? = null,
    val displayOptions: DisplayOptions = DisplayOptions.FINAL,
    val plugins: Plugins = Plugins.FACE,
    val useLora: Boolean = true,
    val displayIteration: Int? = null,
    val prompt: String = "",
    val iteration: Int? = null,
    val seed: Int? = null,
    val initialized: Boolean = false,
    val initializedOutputSize: Int? = null,
    val initializedDisplayIteration: Int? = null,
    val isGenerating: Boolean = false,
    val isInitializing: Boolean = false,
    val generatingMessage: String = "",
)

enum class DisplayOptions {
    ITERATION, FINAL
}

enum class Plugins {
    FACE, DEPTH, EDGE
}
