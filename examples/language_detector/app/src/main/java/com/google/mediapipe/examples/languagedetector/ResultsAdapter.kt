package com.google.mediapipe.examples.languagedetector

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.languagedetector.databinding.LanguageDetectedBinding
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.text.languagedetector.LanguagePrediction

class ResultsAdapter : RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {

    private var resultsList: List<LanguagePrediction> = emptyList()

    inner class ViewHolder(private val binding: LanguageDetectedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(label: String, score: Float) {
            with(binding) {
                result.text = binding.root.context.getString(
                    R.string.result_display_text,
                    label,
                    score
                ).replaceFirstChar { it.titlecase() }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateResult(results: List<LanguagePrediction>) {
        resultsList = results
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            LanguageDetectedBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = resultsList[position]
        holder.bind(category.languageCode(), category.probability())
    }

    override fun getItemCount() = resultsList.size
}