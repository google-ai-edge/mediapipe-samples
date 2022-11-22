/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
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
package com.google.mediapipe.examples.textclassifier

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.textclassifier.databinding.ItemClassificationBinding
import com.google.mediapipe.tasks.components.containers.Category

class ResultsAdapter : RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {

    private var resultsList: List<Category> = emptyList()
    private var currentModel = TextClassifierHelper.WORD_VEC

    inner class ViewHolder(private val binding: ItemClassificationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(label: String, score: Float) {
            with(binding) {
                val displayLabel =
                    if (currentModel == TextClassifierHelper.WORD_VEC
                    ) {
                        // Category name 1 is Positive and 0 is Negative.
                        if (label == "1") "Positive" else "Negative"
                    } else {
                        label
                    }

                result.text = binding.root.context.getString(
                    R.string.result_display_text,
                    displayLabel,
                    score
                ).replaceFirstChar { it.titlecase() }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateResult(results: List<Category>, model: String) {
        resultsList = results
        currentModel = model
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            ItemClassificationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = resultsList[position]
        holder.bind(category.categoryName(), category.score())
    }

    override fun getItemCount() = resultsList.size
}
