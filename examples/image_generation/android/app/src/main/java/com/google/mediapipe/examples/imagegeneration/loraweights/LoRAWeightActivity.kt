package com.google.mediapipe.examples.imagegeneration.loraweights

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.mediapipe.examples.imagegeneration.R
import com.google.mediapipe.examples.imagegeneration.databinding.ActivityLoraBinding
import kotlinx.coroutines.launch
import java.util.*

class LoRAWeightActivity : AppCompatActivity() {
    companion object {
        private const val DEFAULT_DISPLAY_ITERATION = 5
        private const val DEFAULT_ITERATION = 20
        private const val DEFAULT_SEED = 0
        private val DEFAULT_PROMPT = R.string.default_lora_plugin
        private val DEFAULT_DISPLAY_OPTIONS = R.id.radio_final // FINAL
    }

    private lateinit var binding: ActivityLoraBinding
    private val viewModel: LoRAWeightViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel.createImageGenerationHelper(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Update UI
                viewModel.uiState.collect { uiState ->
                    binding.llInitializeSection.visibility =
                        if (uiState.initialized) android.view.View.GONE else android.view.View.VISIBLE
                    binding.llGenerateSection.visibility =
                        if (uiState.initialized) android.view.View.VISIBLE else android.view.View.GONE
                    binding.llDisplayIteration.visibility =
                        if (uiState.displayOptions == DisplayOptions.ITERATION) android.view.View.VISIBLE else android.view.View.GONE

                    // Button initialize is enabled when (the display option is final or iteration and display iteration is not null) and is not initializing
                    binding.btnInitialize.isEnabled =
                        (uiState.displayOptions == DisplayOptions.FINAL || (uiState.displayOptions == DisplayOptions.ITERATION && uiState.displayIteration != null)) && !uiState.isInitializing

                    if (uiState.isGenerating) {
                        binding.btnGenerate.isEnabled = false
                        binding.btnGenerate.text = uiState.generatingMessage
                        binding.tvDisclaimer.visibility = View.VISIBLE
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
                    showGenerateTime(uiState.generateTime)
                    showInitializedTime(uiState.initializedTime)
                }
            }
        }

        handleListener()
        setDefaultValue()
    }

    private fun handleListener() {
        binding.btnInitialize.setOnClickListener {
            viewModel.initializeImageGenerator()
            closeSoftKeyboard()
        }
        binding.btnGenerate.setOnClickListener {
            viewModel.generateImage()
            closeSoftKeyboard()
        }
        binding.btnSeedRandom.setOnClickListener {
            randomSeed()
            closeSoftKeyboard()
        }

        binding.radioDisplayOptions.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.radio_iteration -> {
                    viewModel.updateDisplayOptions(DisplayOptions.ITERATION)
                }

                R.id.radio_final -> {
                    viewModel.updateDisplayOptions(DisplayOptions.FINAL)
                }
            }
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

    private fun showGenerateTime(time: Long?) {
        if (time == null) return
        runOnUiThread {
            Toast.makeText(
                this,
                "Generation time: ${time / 1000.0} seconds",
                Toast.LENGTH_SHORT
            ).show()
        }
        // prevent showing error message twice
        viewModel.clearGenerateTime()
    }

    private fun showInitializedTime(time: Long?) {
        if (time == null) return
        runOnUiThread {
            Toast.makeText(
                this,
                "Initialized time: ${time / 1000.0} seconds",
                Toast.LENGTH_SHORT
            ).show()
        }
        // prevent showing error message twice
        viewModel.clearInitializedTime()
    }

    private fun closeSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun setDefaultValue() {
        with(binding) {
            edtPrompt.setText(getString(DEFAULT_PROMPT))
            edtIterations.setText(DEFAULT_ITERATION.toString())
            edtSeed.setText(DEFAULT_SEED.toString())
            radioDisplayOptions.check(DEFAULT_DISPLAY_OPTIONS)
            edtDisplayIteration.setText(DEFAULT_DISPLAY_ITERATION.toString())
        }
    }

    private fun randomSeed() {
        val random = Random()
        val seed = Math.abs(random.nextInt())
        binding.edtSeed.setText(seed.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.closeGenerator()
    }
}
