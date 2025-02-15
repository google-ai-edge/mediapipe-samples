package com.google.mediapipe.examples.llminference

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

private class MissingAccessTokenException :
    Exception("Please try again after sign in")

@Composable
internal fun LoadingRoute(
    onModelLoaded: () -> Unit = { },
    onGoBack: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    var errorMessage by remember { mutableStateOf("") }

    var progress by remember { mutableStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }
    var job: Job? by remember { mutableStateOf(null) }
    val client = remember { OkHttpClient() }

    if (errorMessage != "") {
        ErrorMessage(errorMessage, onGoBack)
    } else if (isDownloading) {
        DownloadIndicator(progress) {
            job?.cancel()
            isDownloading = false

            CoroutineScope(Dispatchers.Main).launch {
                deleteDownloadedFile(context)
                withContext(Dispatchers.Main) {
                    errorMessage = "Download Cancelled"
                }
            }
        }
    } else {
        LoadingIndicator()
    }

    LaunchedEffect(Unit) {
        job = launch(Dispatchers.IO) {
            try {
                if (!InferenceModel.modelExists(context)) {
                    if (InferenceModel.model.url.isEmpty()) {
                        throw Exception("Download failed due to empty URL")
                    }
                    isDownloading = true
                    downloadModel(context, InferenceModel.model, client) { newProgress ->
                        progress = newProgress
                    }
                }

                isDownloading = false
                InferenceModel.resetInstance(context)
                // Notify the UI that the model has finished loading
                withContext(Dispatchers.Main) {
                    onModelLoaded()
                }
            } catch (e: MissingAccessTokenException) {
                errorMessage = e.localizedMessage ?: "Unknown Error"
            } catch (e: ModelLoadFailException) {
                errorMessage = e.localizedMessage ?: "Unknown Error"
                // Remove invalid model file
                CoroutineScope(Dispatchers.Main).launch {
                    deleteDownloadedFile(context)
                }
            } catch (e: Exception) {
                val error = e.localizedMessage ?: "Unknown Error"
                errorMessage = "${error}, please copy the model manually to ${InferenceModel.model.path}"
            }
        }
    }
}

private fun downloadModel(context: Context, model: Model, client: OkHttpClient, onProgressUpdate: (Int) -> Unit) {
    val requestBuilder = Request.Builder().url(model.url)

    if (model.needsAuth) {
        val accessToken = SecureStorage.getToken(context)
        if (accessToken.isNullOrEmpty()) {
            // Trigger LoginActivity if no access token is found
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            throw MissingAccessTokenException()
        } else {
            requestBuilder.addHeader("Authorization", "Bearer $accessToken")
        }
    }

    val outputFile = File(context.filesDir, File(InferenceModel.model.path).name)
    val response = client.newCall(requestBuilder.build()).execute()
    if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")

    response.body?.byteStream()?.use { inputStream ->
        FileOutputStream(outputFile).use { outputStream ->
            val buffer = ByteArray(4096)
            var bytesRead: Int
            var totalBytesRead = 0L
            val contentLength = response.body?.contentLength() ?: -1

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                val progress = if (contentLength > 0) {
                    (totalBytesRead * 100 / contentLength).toInt()
                } else {
                    -1
                }
                onProgressUpdate(progress)
            }
            outputStream.flush()
        }
    }
}

private suspend fun deleteDownloadedFile(context: Context) {
    withContext(Dispatchers.IO) {
        val outputFile = File(context.filesDir, File(InferenceModel.model.path).name)
        if (outputFile.exists()) {
            outputFile.delete()
        }
    }
}

@Composable
fun DownloadIndicator(progress: Int, onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Downloading Model: $progress%",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        CircularProgressIndicator(progress = { progress / 100f })
        Button(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
            Text("Cancel")
        }
    }
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
    errorMessage: String,
    onGoBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
        Button(onClick = onGoBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("Go Back")
        }
    }
}

