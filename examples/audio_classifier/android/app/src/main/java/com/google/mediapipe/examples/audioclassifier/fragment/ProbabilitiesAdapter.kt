/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
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

package com.google.mediapipe.examples.audioclassifier.fragment

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.audioclassifier.R
import com.google.mediapipe.examples.audioclassifier.databinding.ItemProbabilityBinding
import com.google.mediapipe.tasks.components.containers.Category

class ProbabilitiesAdapter :
    RecyclerView.Adapter<ProbabilitiesAdapter.ViewHolder>() {
    private var categoryList: List<Category> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun updateCategoryList(categoryList: List<Category>) {
        this.categoryList = categoryList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            ItemProbabilityBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categoryList[position]
        holder.bind(category.categoryName(), category.score(), category.index())
    }

    override fun getItemCount(): Int {
        return categoryList.size
    }

    class ViewHolder(private val binding: ItemProbabilityBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var primaryProgressColorList: IntArray =
            binding.root.resources.getIntArray((R.array.colors_progress_primary))
        private var backgroundProgressColorList: IntArray =
            binding.root.resources.getIntArray((R.array.colors_progress_background))

        fun bind(label: String, score: Float, index: Int) {
            with(binding) {
                labelTextView.text = label

                progressBar.progressBackgroundTintList =
                    ColorStateList.valueOf(
                        backgroundProgressColorList[index % backgroundProgressColorList.size]
                    )

                progressBar.progressTintList =
                    ColorStateList.valueOf(
                        primaryProgressColorList[index % primaryProgressColorList.size]
                    )

                val newValue = (score * 100).toInt()
                progressBar.progress = newValue
            }
        }
    }
}
