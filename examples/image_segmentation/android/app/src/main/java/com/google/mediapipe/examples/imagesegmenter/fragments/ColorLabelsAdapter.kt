/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.imagesegmenter.fragments

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.imagesegmenter.databinding.ItemColorLabelsBinding
import com.google.mediapipe.examples.imagesegmenter.toAlphaColor

class ColorLabelsAdapter :
    RecyclerView.Adapter<ColorLabelsAdapter.ViewHolder>() {
    private var coloredLabels: List<Pair<String, Int>> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun updateResultLabels(coloredLabels: List<Pair<String, Int>>) {
        this.coloredLabels = coloredLabels
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemColorLabelsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(label: String, rgbColor: Int) {
            with(binding) {
                tvLabel.text = label
                val drawable =
                    flBackgroundLabel.background.mutate() as GradientDrawable
                drawable.setColor(rgbColor.toAlphaColor())
                drawable.invalidateSelf()
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            ItemColorLabelsBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        coloredLabels[position].let {
            holder.bind(it.first, it.second)
        }
    }

    override fun getItemCount(): Int = coloredLabels.size
}
