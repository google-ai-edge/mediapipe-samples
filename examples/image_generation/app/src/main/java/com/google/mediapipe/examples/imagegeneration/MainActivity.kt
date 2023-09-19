package com.google.mediapipe.examples.imagegeneration

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.mediapipe.examples.imagegeneration.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MediaPipe Image Generation"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    binding.btnInitialize.isEnabled =
                        uiState.outputSize != null && uiState.displayIteration != null && !uiState.isGenerating
                    binding.btnInitialize.text =
                        if (uiState.initialized) "Initialized (Output size:${uiState.initializedOutputSize} Iterations: ${uiState.initializedDisplayIteration})" else "Initialize"

                    if (uiState.isGenerating) {
                        binding.btnGenerate.isEnabled = false
                        binding.btnGenerate.text = uiState.generatingMessage
                    } else {
                        binding.btnGenerate.text = "Generate"
                        if (uiState.initialized) {
                            binding.btnGenerate.isEnabled =
                                uiState.prompt.isNotEmpty() && uiState.iteration != null && uiState.seed != null
                        } else {
                            binding.btnGenerate.isEnabled = false
                        }
                    }
                    binding.imgOutput.setImageBitmap(uiState.outputBitmap)

                    showError(uiState.error)
                }
            }
        }

        handleListener()
    }

    private fun handleListener() {
        binding.btnInitialize.setOnClickListener {
            viewModel.createImageGenerationHelper(this)
            closeSoftKeyboard()
        }
        binding.btnGenerate.setOnClickListener {
            viewModel.generateImage()
            closeSoftKeyboard()
        }
        binding.edtOutputSize.doOnTextChanged { text, _, _, _ ->
            viewModel.updateOutputSize(text.toString().toIntOrNull())
        }
        binding.edtDisplayIteration.doOnTextChanged { text, _, _, _ ->
            viewModel.updateDisplayIteration(text.toString().toIntOrNull())
        }
        binding.edtPrompt.doOnTextChanged { text, _, _, _ ->
            viewModel.updatePrompt(text.toString())
        }
        binding.edtIterations.doOnTextChanged { text, _, _, _ ->
            viewModel.updateIteration(text.toString().toIntOrNull())
        }
        binding.edtSeed.doOnTextChanged { text, _, _, _ ->
            viewModel.updateSeed(text.toString().toIntOrNull())
        }
    }

    private fun showError(message: String?) {
        if (message.isNullOrEmpty()) return
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        // prevent showing error message twice
        viewModel.clearError()
    }

    private fun closeSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
