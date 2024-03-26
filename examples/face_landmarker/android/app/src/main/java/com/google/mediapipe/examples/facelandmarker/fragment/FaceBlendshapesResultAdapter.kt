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

package com.google.mediapipe.examples.facelandmarker.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.facelandmarker.databinding.FaceBlendshapesResultBinding
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class FaceBlendshapesResultAdapter :
    RecyclerView.Adapter<FaceBlendshapesResultAdapter.ViewHolder>() {
    companion object {
        private const val NO_VALUE = "--"
    }

    private var categories: MutableList<Category?> = MutableList(52) { null }

    fun updateResults(faceLandmarkerResult: FaceLandmarkerResult? = null) {
        categories = MutableList(52) { null }
        if (faceLandmarkerResult != null && faceLandmarkerResult.faceBlendshapes().isPresent) {
            val faceBlendshapes = faceLandmarkerResult.faceBlendshapes().get()
            val sortedCategories = faceBlendshapes[0].sortedByDescending { it.score() }
            val min = kotlin.math.min(sortedCategories.size, categories.size)
            for (i in 0 until min) {
                categories[i] = sortedCategories[i]
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = FaceBlendshapesResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        categories[position].let { category ->
            holder.bind(category?.categoryName(), category?.score())
        }
    }

    override fun getItemCount(): Int = categories.size

    inner class ViewHolder(private val binding: FaceBlendshapesResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(label: String?, score: Float?) {
            with(binding) {
                tvLabel.text = label ?: NO_VALUE
                tvScore.text = if (score != null) String.format(
                    "%.2f",
                    score
                ) else NO_VALUE
            }
        }
    }


}
