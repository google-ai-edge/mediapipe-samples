package com.google.mediapipe.examples.imagegeneration.plugins

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.mediapipe.examples.imagegeneration.ImageGenerationHelper
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ConditionOptions.ConditionType
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PluginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    private var helper: ImageGenerationHelper? = null
    val uiState: StateFlow<UiState> = _uiState
    private val MODEL_PATH = "/data/local/tmp/image_generator/bins/"

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
                    0 -> ConditionType.FACE
                    1 -> ConditionType.DEPTH
                    2 -> ConditionType.EDGE
                    else -> throw IllegalArgumentException("Invalid plugin")
                }
            )
        }
    }

    fun updateInputBitmap(bitmap: Bitmap) {
        _uiState.update { it.copy(inputBitmap = bitmap) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearGenerationTime() {
        _uiState.update { it.copy(generateTime = null) }
    }

    fun clearInitializedTime() {
        _uiState.update { it.copy(initializedTime = null) }
    }


    fun initializeImageGenerator() {
        val displayIteration = _uiState.value.displayIteration
        val displayOptions = _uiState.value.displayOptions
        val conditionType = _uiState.value.plugins
        try {
            if (displayIteration == null && displayOptions == DisplayOptions.ITERATION) {
                _uiState.update { it.copy(error = "Display iteration cannot be empty") }
                return
            }

            _uiState.update { it.copy(isInitializing = true) }
            val mainLooper = Looper.getMainLooper()
            GlobalScope.launch {
                val startTime = System.currentTimeMillis()
                when(conditionType) {
                    ConditionType.FACE -> helper?.initializeImageGeneratorWithFacePlugin(MODEL_PATH)
                    ConditionType.EDGE -> helper?.initializeImageGeneratorWithEdgePlugin(MODEL_PATH)
                    ConditionType.DEPTH -> helper?.initializeImageGeneratorWithDepthPlugin(MODEL_PATH)
                }

                Handler(mainLooper).post {
                    _uiState.update {
                        it.copy(
                            initialized = true,
                            isInitializing = false,
                            initializedTime = System.currentTimeMillis() - startTime
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
        val displayIteration = _uiState.value.displayIteration ?: 0
        val inputImage = _uiState.value.inputBitmap
        val conditionType = _uiState.value.plugins
        var isDisplayStep = false
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
        if (inputImage == null) {
            _uiState.update { it.copy(error = "Input image cannot be empty") }
            return
        }

        _uiState.update {
            it.copy(
                generatingMessage = "Generating...",
                isGenerating = true
            )
        }

        // Generate with iterations
        GlobalScope.launch {

            val startTime = System.currentTimeMillis()

            // if display option is final, use generate method, else use execute method
            if (uiState.value.displayOptions == DisplayOptions.FINAL) {
                val result = helper?.generate(
                    prompt,
                    BitmapImageBuilder(inputImage).build(),
                    conditionType,
                    iteration,
                    seed
                )
                _uiState.update {
                    it.copy(outputBitmap = result)
                }
            } else {
                helper?.setInput(
                    prompt,
                    BitmapImageBuilder(inputImage).build(),
                    conditionType,
                    iteration,
                    seed
                )
                for (step in 0 until iteration) {
                    isDisplayStep =
                        (displayIteration > 0 && ((step + 1) % displayIteration == 0))
                    val result = helper?.execute(isDisplayStep)

                    if (isDisplayStep) {
                        _uiState.update {
                            it.copy(
                                outputBitmap = result,
                                generatingMessage = "Generating... (${step + 1}/$iteration)"
                            )
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isGenerating = false,
                    generatingMessage = "Generate",
                    generateTime = System.currentTimeMillis() - startTime
                )
            }
        }
    }

    fun closeGenerator() {
        helper?.close()
    }
}

data class UiState(
    val error: String? = null,
    val inputBitmap: Bitmap? = null,
    val outputBitmap: Bitmap? = null,
    val displayOptions: DisplayOptions = DisplayOptions.FINAL,
    val plugins: ConditionType = ConditionType.FACE,
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
    val generateTime: Long? = null,
    val initializedTime: Long? = null,
)

enum class DisplayOptions {
    ITERATION, FINAL
}