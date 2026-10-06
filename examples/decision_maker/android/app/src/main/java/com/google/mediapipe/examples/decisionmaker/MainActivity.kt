/*
 * Copyright 2026 The MediaPipe Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.decisionmaker

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.google.mediapipe.examples.decisionmaker.databinding.ActivityMainBinding
import com.google.mediapipe.examples.decisionmaker.databinding.ItemQuestionRowBinding
import com.google.mediapipe.examples.decisionmaker.databinding.ItemResultBarBinding
import com.google.mediapipe.tasks.decision.BooleanResult
import com.google.mediapipe.tasks.decision.ChoiceResult
import com.google.mediapipe.tasks.decision.ScoreResult
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private enum class QuestionType { BOOLEAN, CHOICE, SCORE }

    /** A choice option (key + description) or a rubric level (name + description). */
    private data class Item(var label: String, var description: String = "")

    /** Editable fields of one question type; kept per tab so switching tabs keeps your edits. */
    private data class Draft(
        var input: String,
        var condition: String = "",
        /** Boolean only: answer Yes when P(yes) >= threshold. */
        var threshold: Float = DEFAULT_THRESHOLD,
        val items: MutableList<Item> = mutableListOf(),
        var instructions: String = "",
    )

    /** What the result card shows: a headline, an optional subtitle and probability bars. */
    private data class Outcome(val headline: String, val subtitle: String?, val bars: List<Bar>)
    private data class Bar(val label: String, val probability: Float, val highlighted: Boolean)

    private val drafts = mutableMapOf(
        // Clear-cut sentiment works well with every bundled model; try lowering / raising the
        // threshold, or editing the review, to see the answer flip.
        QuestionType.BOOLEAN to Draft(
            input = "This product is amazing, it works perfectly and I use it every day!",
            condition = "The review is positive.",
        ),
        QuestionType.CHOICE to Draft(
            input = "My package never arrived even though tracking says delivered.",
            items = mutableListOf(
                Item("shipping", "Delivery problems, lost packages, tracking"),
                Item("billing", "Payment issues, duplicate charges, invoices"),
                Item("technical", "App crashes, login errors, bugs"),
            ),
            instructions = "Which department should handle this ticket?",
        ),
        // Give each level a short, concrete description: bare labels such as "Negative" /
        // "Positive" give much less accurate scores.
        QuestionType.SCORE to Draft(
            input = "The support agent was friendly and fixed my issue fast.",
            items = mutableListOf(
                Item("Very dissatisfied", "angry, problem not solved, terrible service"),
                Item("Dissatisfied", "slow or unhelpful support, problem partly solved"),
                Item("Neutral", "okay, average, nothing special"),
                Item("Satisfied", "helpful support, problem solved"),
                Item("Very satisfied", "excellent, fast, friendly support, delighted"),
            ),
            instructions = "Rate how satisfied the customer is with the support experience.",
        ),
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var helper: DecisionMakerHelper

    /** Option / level rows currently shown in the editor, in order. */
    private val itemRows = mutableListOf<ItemQuestionRowBinding>()

    /** Model loading and inference run here, one at a time, off the main thread. */
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var questionType = QuestionType.BOOLEAN
    private var isModelReady = false

    /** True while the result card shows a result (not a placeholder or status message). */
    private var hasResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        helper = DecisionMakerHelper(this)
        showDraft(questionType)

        binding.tabQuestionType.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                saveDraft()
                questionType = QuestionType.values()[tab.position]
                showDraft(questionType)
                resetResult()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        binding.btnAddItem.setOnClickListener { addItemRow(Item(""), requestFocus = true) }
        binding.btnEvaluate.setOnClickListener { evaluate() }
        initThresholdSlider()

        initBottomSheetControls()
        setupDecisionMaker()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.execute { helper.clearDecisionMaker() }
        executor.shutdown()
    }

    private fun initThresholdSlider() {
        binding.sliderThreshold.addOnChangeListener { _, value, _ ->
            drafts.getValue(QuestionType.BOOLEAN).threshold = value
            binding.tvThresholdValue.text = String.format(Locale.US, "%.2f", value)
        }
        // Re-run the question when the slider is released, so the answer follows the threshold.
        binding.sliderThreshold.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                if (hasResult) evaluate()
            }
        })
    }

    private fun initBottomSheetControls() {
        val sheet = binding.bottomSheetLayout
        val behavior = BottomSheetBehavior.from(sheet.root)
        sheet.sheetHeader.setOnClickListener {
            behavior.state =
                if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) BottomSheetBehavior.STATE_COLLAPSED
                else BottomSheetBehavior.STATE_EXPANDED
        }

        sheet.toggleDelegate.check(delegateButtonId(helper.currentDelegate))
        sheet.toggleDelegate.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val delegate =
                if (checkedId == R.id.btn_gpu) DecisionMakerHelper.DELEGATE_GPU
                else DecisionMakerHelper.DELEGATE_CPU
            if (delegate != helper.currentDelegate) {
                helper.currentDelegate = delegate
                setupDecisionMaker()
            }
        }

        val models = resources.getStringArray(R.array.models_spinner_titles)
        sheet.dropdownModel.setText(models[helper.currentModel], false)
        sheet.dropdownModel.setOnItemClickListener { _, _, position, _ ->
            if (position != helper.currentModel) {
                helper.currentModel = position
                setupDecisionMaker()
            }
        }
    }

    private fun delegateButtonId(delegate: Int) =
        if (delegate == DecisionMakerHelper.DELEGATE_GPU) R.id.btn_gpu else R.id.btn_cpu

    private fun setupDecisionMaker() {
        isModelReady = false
        // The last inference time belongs to the previous model / delegate.
        binding.bottomSheetLayout.inferenceTimeVal.setText(R.string.inference_time_placeholder)
        setBusy(true, getString(R.string.tv_loading_model))
        executor.execute {
            val error = runCatching { helper.setupDecisionMaker() }.exceptionOrNull()
            runOnUiThread {
                setBusy(false)
                if (error == null) {
                    isModelReady = true
                    resetResult()
                    return@runOnUiThread
                }
                Log.e(TAG, "Failed to create DecisionMaker", error)
                if (helper.currentDelegate == DecisionMakerHelper.DELEGATE_GPU &&
                    error !is DecisionMakerHelper.ModelNotFoundException
                ) {
                    // GPU unavailable on this device: fall back to CPU, like the other samples.
                    // (A missing model file would fail on CPU too, so it's reported directly.)
                    Toast.makeText(this, R.string.toast_gpu_fallback, Toast.LENGTH_SHORT).show()
                    helper.currentDelegate = DecisionMakerHelper.DELEGATE_CPU
                    // currentDelegate is already CPU, so the toggle listener won't reload.
                    binding.bottomSheetLayout.toggleDelegate.check(R.id.btn_cpu)
                    setupDecisionMaker()
                } else {
                    showHeadline(error.message ?: error.toString(), placeholder = true)
                }
            }
        }
    }

    private fun evaluate() {
        if (!isModelReady) return
        saveDraft()
        val draft = drafts.getValue(questionType)
        val type = questionType
        if (draft.input.isBlank()) {
            Toast.makeText(this, R.string.toast_need_input, Toast.LENGTH_SHORT).show()
            return
        }
        val items = draft.items
            .map { Item(it.label.trim(), it.description.trim()) }
            .filter { it.label.isNotEmpty() }
        if (type != QuestionType.BOOLEAN && items.size < 2) {
            Toast.makeText(this, R.string.toast_need_items, Toast.LENGTH_SHORT).show()
            return
        }

        setBusy(true)
        executor.execute {
            val outcome = runCatching {
                when (type) {
                    QuestionType.BOOLEAN -> {
                        val threshold = draft.threshold
                        helper.evaluateBoolean(draft.input, draft.condition.trim(), threshold)
                            .let { it.inferenceTime to formatBoolean(it.result, threshold) }
                    }

                    QuestionType.CHOICE -> {
                        val options = items.associate { it.label to it.description }
                        helper.evaluateChoice(draft.input, options, draft.instructions.trim())
                            .let { it.inferenceTime to formatChoice(it.result, options) }
                    }

                    QuestionType.SCORE -> {
                        // Each level is sent as "name: description".
                        val rubric = items.map {
                            if (it.description.isEmpty()) it.label else "${it.label}: ${it.description}"
                        }
                        helper.evaluateScore(draft.input, rubric, draft.instructions.trim())
                            .let { it.inferenceTime to formatScore(it.result, items.map { i -> i.label }) }
                    }
                }
            }
            runOnUiThread {
                setBusy(false)
                outcome.onSuccess { (inferenceTime, result) ->
                    showOutcome(result)
                    binding.bottomSheetLayout.inferenceTimeVal.text =
                        String.format(Locale.US, "%d ms", inferenceTime)
                }.onFailure { error ->
                    Log.e(TAG, "Evaluation failed", error)
                    Toast.makeText(this, error.message ?: error.toString(), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ---- Result formatting ----

    private fun formatBoolean(r: BooleanResult, threshold: Float) = Outcome(
        headline = getString(if (r.value) R.string.result_yes else R.string.result_no),
        subtitle = getString(R.string.result_threshold, threshold),
        bars = listOf(
            Bar(getString(R.string.result_yes), r.probabilityTrue, r.value),
            Bar(getString(R.string.result_no), 1f - r.probabilityTrue, !r.value),
        ),
    )

    private fun formatChoice(r: ChoiceResult, options: Map<String, String>) = Outcome(
        headline = r.selectedKey,
        subtitle = options[r.selectedKey]?.takeIf { it.isNotEmpty() },
        bars = r.probabilities.entries.sortedByDescending { it.value }
            .map { Bar(it.key, it.value, it.key == r.selectedKey) },
    )

    private fun formatScore(r: ScoreResult, levels: List<String>): Outcome {
        val probabilities = r.levelProbabilities
        // Expected score on a 1..N scale.
        val expected = probabilities.withIndex().sumOf { (i, p) -> (i + 1) * p.toDouble() }
        val best = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        return Outcome(
            headline = String.format(Locale.US, "%.2f / %d", expected, probabilities.size),
            subtitle = levels.getOrNull(best)?.let { getString(R.string.result_most_likely, it) },
            bars = probabilities.mapIndexed { i, p ->
                Bar("${i + 1} · ${levels.getOrElse(i) { "" }}", p, i == best)
            },
        )
    }

    private fun showOutcome(outcome: Outcome) {
        hasResult = true
        showHeadline(outcome.headline, placeholder = false)
        binding.tvResultSubtitle.text = outcome.subtitle
        binding.tvResultSubtitle.visibility = if (outcome.subtitle == null) View.GONE else View.VISIBLE

        val container = binding.resultBars
        container.removeAllViews()
        val primary = ContextCompat.getColor(this, R.color.mp_color_primary)
        val muted = ContextCompat.getColor(this, R.color.result_bar_muted)
        for (bar in outcome.bars) {
            val row = ItemResultBarBinding.inflate(layoutInflater, container, false)
            val p = bar.probability.coerceIn(0f, 1f)
            row.tvBarLabel.text = bar.label
            row.tvBarLabel.setTypeface(null, if (bar.highlighted) Typeface.BOLD else Typeface.NORMAL)
            row.tvBarValue.text = String.format(Locale.US, "%.1f%%", p * 100)
            row.barProgress.setIndicatorColor(if (bar.highlighted) primary else muted)
            row.barProgress.progress = (p * row.barProgress.max).roundToInt()
            container.addView(row.root)
        }
        container.visibility = if (outcome.bars.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Large, dark headline for results; smaller grey text for placeholders and status. */
    private fun showHeadline(text: String, placeholder: Boolean) {
        binding.tvResult.text = text
        binding.tvResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (placeholder) 16f else 28f)
        binding.tvResult.setTypeface(null, if (placeholder) Typeface.NORMAL else Typeface.BOLD)
        binding.tvResult.setTextColor(
            ContextCompat.getColor(this, if (placeholder) R.color.label_text_color else R.color.mp_color_primary_dark)
        )
    }

    // ---- Question editor ----

    private fun showDraft(type: QuestionType) {
        val draft = drafts.getValue(type)
        binding.etInputText.setText(draft.input)
        binding.etQuestion.setText(draft.condition)
        binding.etInstructions.setText(draft.instructions)

        // Boolean questions only need a condition; the others need a list and instructions.
        val isBoolean = type == QuestionType.BOOLEAN
        binding.conditionLayout.visibility = if (isBoolean) View.VISIBLE else View.GONE
        binding.thresholdSection.visibility = if (isBoolean) View.VISIBLE else View.GONE
        binding.sliderThreshold.value = drafts.getValue(QuestionType.BOOLEAN).threshold
        binding.itemsSection.visibility = if (isBoolean) View.GONE else View.VISIBLE
        binding.instructionsLayout.visibility = if (isBoolean) View.GONE else View.VISIBLE

        val isScore = type == QuestionType.SCORE
        binding.tvItemsTitle.setText(if (isScore) R.string.title_rubric else R.string.title_options)
        binding.tvItemsHelper.setText(if (isScore) R.string.helper_rubric else R.string.helper_options)
        binding.btnAddItem.setText(if (isScore) R.string.btn_add_level else R.string.btn_add_option)

        binding.itemsContainer.removeAllViews()
        itemRows.clear()
        draft.items.forEach { addItemRow(it) }
    }

    private fun addItemRow(item: Item, requestFocus: Boolean = false) {
        val row = ItemQuestionRowBinding.inflate(layoutInflater, binding.itemsContainer, false)
        row.etLabel.setHint(
            if (questionType == QuestionType.SCORE) R.string.hint_level_label else R.string.hint_option_key
        )
        row.etLabel.setText(item.label)
        row.etDescription.setText(item.description)
        row.btnRemove.setOnClickListener {
            binding.itemsContainer.removeView(row.root)
            itemRows.remove(row)
            renumberRows()
        }
        itemRows += row
        binding.itemsContainer.addView(row.root)
        renumberRows()
        if (requestFocus) row.etLabel.requestFocus()
    }

    /** Score levels are numbered 1..N (lowest first); choice options are lettered A, B, C… */
    private fun renumberRows() {
        itemRows.forEachIndexed { i, row ->
            row.tvBadge.text =
                if (questionType == QuestionType.SCORE) "${i + 1}"
                else if (i < 26) ('A' + i).toString() else "${i + 1}"
        }
    }

    private fun saveDraft() {
        drafts.getValue(questionType).apply {
            input = binding.etInputText.text.toString()
            condition = binding.etQuestion.text.toString()
            instructions = binding.etInstructions.text.toString()
            if (questionType != QuestionType.BOOLEAN) {
                items.clear()
                itemRows.mapTo(items) {
                    Item(it.etLabel.text.toString(), it.etDescription.text.toString())
                }
            }
        }
    }

    // ---- UI state ----

    private fun resetResult() {
        hasResult = false
        showHeadline(getString(R.string.tv_result_placeholder), placeholder = true)
        binding.tvResultSubtitle.visibility = View.GONE
        binding.resultBars.visibility = View.GONE
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        binding.btnEvaluate.isEnabled = !busy
        binding.progressBar.visibility = if (busy) View.VISIBLE else View.INVISIBLE
        if (message != null) {
            hasResult = false
            showHeadline(message, placeholder = true)
            binding.tvResultSubtitle.visibility = View.GONE
            binding.resultBars.visibility = View.GONE
        }
    }

    companion object {
        private const val TAG = "DecisionMaker"
        private const val DEFAULT_THRESHOLD = 0.5f
    }
}
