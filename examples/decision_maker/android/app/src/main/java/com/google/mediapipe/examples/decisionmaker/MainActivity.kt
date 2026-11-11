/*
 * Copyright 2026 The MediaPipe Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.decisionmaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mediapipe.examples.common.ui.MediaPipeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DecisionMakerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MediaPipeTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                DecisionMakerScreen(
                    uiState = uiState,
                    onSelectQuestionType = viewModel::selectQuestionType,
                    onInputTextChange = viewModel::updateInputText,
                    onConditionChange = viewModel::updateCondition,
                    onThresholdChange = viewModel::updateThreshold,
                    onThresholdChangeFinished = {
                        if (uiState.outcome != null) {
                            viewModel.evaluate()
                        }
                    },
                    onInstructionsChange = viewModel::updateInstructions,
                    onAddItem = viewModel::addItem,
                    onUpdateItemLabel = viewModel::updateItemLabel,
                    onUpdateItemDescription = viewModel::updateItemDescription,
                    onRemoveItem = viewModel::removeItem,
                    onEvaluate = viewModel::evaluate,
                    onSelectDelegate = viewModel::selectDelegate,
                    onSelectModel = viewModel::selectModel,
                    onRetryInitialization = viewModel::retryInitialization,
                )
            }
        }
    }
}
