package com.example.mediapipe.llminference.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mediapipe.llminference.R
import com.example.mediapipe.llminference.InferenceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LoadingRoute(
    onModelLoaded: () -> Unit = { }
) {
    val context = LocalContext.current.applicationContext
    var errorMessage by remember { mutableStateOf("") }
    var finishedLoading by remember { mutableStateOf(false) }

    if (errorMessage != "") {
        ErrorMessage(errorMessage)
    } else {
        LoadingIndicator()
    }

    LaunchedEffect(finishedLoading, block = {
        // Create the LlmInference in a separate thread
        withContext(Dispatchers.IO) {
            try {
                InferenceModel.getInstance(context)
                // Notify the UI that the model has finished loading
                withContext(Dispatchers.Main) {
                    onModelLoaded()
                    finishedLoading = true
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "UnknownError"
            }
        }
    })
}

@Composable
fun LoadingIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.loading_model),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(bottom = 8.dp)
        )
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorMessage(
    errorMessage: String
) {
    Text(text = errorMessage)
}