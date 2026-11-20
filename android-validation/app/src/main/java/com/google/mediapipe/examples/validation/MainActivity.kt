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

package com.google.mediapipe.examples.validation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.common.ui.MediaPipeTheme
import com.google.mediapipe.examples.common.ui.MediaPipeTopBar
import com.google.mediapipe.examples.validation.runner.SampleInfo
import org.json.JSONObject

/**
 * Lists the samples this app was built with. Each entry opens the activity of that sample, the
 * same one its own app starts with. The release validation does not use this screen; it starts
 * the samples through the instrumentation of the runner.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaPipeTheme {
                val context = LocalContext.current
                val (samples, versions) = remember { SampleInfo.read(context) }
                SampleListScreen(
                    samples = samples,
                    versions = versions,
                    onOpenSample = { sample -> startActivity(Intent().setClassName(context, sample.activity)) },
                )
            }
        }
    }
}

@Composable
private fun SampleListScreen(samples: List<SampleInfo>, versions: JSONObject, onOpenSample: (SampleInfo) -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediaPipeTopBar(
                title = stringResource(R.string.validation_app_name),
                subtitle = "${samples.size} samples",
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    text = stringResource(R.string.validation_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(samples, key = { it.task }) { sample ->
                ListItem(
                    overlineContent = { Text(sample.domain) },
                    headlineContent = { Text(sample.heading) },
                    supportingContent = { Text(sample.path, fontFamily = FontFamily.Monospace) },
                    modifier = Modifier.fillMaxWidth().clickable { onOpenSample(sample) }.testTag("sample_${sample.task}"),
                )
                HorizontalDivider()
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(text = stringResource(R.string.validation_versions), style = MaterialTheme.typography.labelLarge)
                    for (name in versions.keys().asSequence().sorted()) {
                        Text(
                            text = "$name ${versions.getString(name)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
