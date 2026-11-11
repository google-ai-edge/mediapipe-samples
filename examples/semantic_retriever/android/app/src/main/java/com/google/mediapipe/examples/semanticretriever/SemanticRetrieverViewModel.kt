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

package com.google.mediapipe.examples.semanticretriever

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RetrieverDelegateOption(val id: Int, val label: String) {
    CPU(SemanticRetrieverHelper.DELEGATE_CPU, "CPU"),
    GPU(SemanticRetrieverHelper.DELEGATE_GPU, "GPU"),
}

enum class VectorStoreOption(val id: Int, val label: String) {
    APPSEARCH(SemanticRetrieverHelper.STORE_APPSEARCH, "AppSearch"),
    SQLITE(SemanticRetrieverHelper.STORE_SQLITE, "SQLite"),
}

data class GalleryItemSpec(
    val id: String,
    val label: String,
    val assetPath: String,
) {
    val assetUri: Uri
        get() = Uri.parse("file:///android_asset/$assetPath")
}

val BundledGalleryItems: List<GalleryItemSpec> =
    listOf(
        GalleryItemSpec("cute_cat", "Cute Cat", "images/cute_cat.jpg"),
        GalleryItemSpec("sunny_beach", "Sunny Beach", "images/sunny_beach.jpg"),
        GalleryItemSpec("snowy_mountain", "Snowy Mountain", "images/snowy_mountain.jpg"),
        GalleryItemSpec("coffee_mug", "Coffee Mug", "images/coffee_mug.jpg"),
        GalleryItemSpec("fast_car", "Fast Car", "images/fast_car.jpg"),
        GalleryItemSpec("blue_sky", "Blue Sky", "images/blue_sky.jpg"),
        GalleryItemSpec("green_tree", "Green Tree", "images/green_tree.jpg"),
        GalleryItemSpec("open_book", "Open Book", "images/open_book.jpg"),
        GalleryItemSpec("red_apple", "Red Apple", "images/red_apple.jpg"),
        GalleryItemSpec("yellow_banana", "Yellow Banana", "images/yellow_banana.jpg"),
    )

data class QuerySuggestion(
    val id: String,
    val label: String,
    val query: String,
)

val DefaultQuerySuggestions: List<QuerySuggestion> =
    listOf(
        QuerySuggestion("beach", "Beach", "warm sandy beach by the ocean"),
        QuerySuggestion("cat", "Cat", "a cute fluffy cat"),
        QuerySuggestion("mountain", "Mountain", "snowy mountain peak"),
        QuerySuggestion("coffee", "Coffee", "a hot mug of coffee"),
        QuerySuggestion("car", "Sports car", "a sleek fast sports car"),
        QuerySuggestion("apple", "Apple", "a crisp fresh red apple"),
    )

data class SearchMatchUi(
    val id: String,
    val label: String,
    val score: Double,
    val thumbnail: Bitmap?,
    val sourceTag: String,
)

data class SemanticRetrieverUiState(
    val query: String = DefaultQuerySuggestions.first().query,
    val delegateOption: RetrieverDelegateOption =
        if (SemanticRetrieverHelper.isAndroidEmulator()) {
            RetrieverDelegateOption.CPU
        } else {
            RetrieverDelegateOption.GPU
        },
    val storeOption: VectorStoreOption = VectorStoreOption.SQLITE,
    val isModelLoading: Boolean = true,
    val isIndexing: Boolean = false,
    val isSearching: Boolean = false,
    val indexingProgress: Int = 0,
    val indexingTotal: Int = BundledGalleryItems.size,
    val indexedCount: Int = 0,
    val matches: List<SearchMatchUi> = emptyList(),
    val lastSearchTimeMs: Long? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
) {
    val isBusy: Boolean
        get() = isModelLoading || isIndexing || isSearching

    val canSearch: Boolean
        get() = !isBusy && indexedCount > 0 && query.isNotBlank()
}

class SemanticRetrieverViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext
    private val engineExecutor = Executors.newSingleThreadExecutor()
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(SemanticRetrieverUiState())
    val uiState: StateFlow<SemanticRetrieverUiState> = _uiState.asStateFlow()

    private val thumbnailCache = mutableMapOf<String, Bitmap>()
    private var helper: SemanticRetrieverHelper? = null
    private var activeJob: Job? = null
    private var lastErrorMessage: String? = null

    private val listener =
        object : SemanticRetrieverHelper.RetrieverListener {
            override fun onError(error: String, errorCode: Int) {
                lastErrorMessage = error
            }
        }

    init {
        prepareAndAutoIndex(allowGpuFallback = true)
    }

    fun updateQuery(query: String) {
        _uiState.update {
            if (it.query == query) it else it.copy(query = query)
        }
    }

    fun applySuggestion(suggestion: QuerySuggestion) {
        _uiState.update { it.copy(query = suggestion.query) }
    }

    fun selectDelegate(option: RetrieverDelegateOption) {
        if (_uiState.value.delegateOption == option || _uiState.value.isBusy) return
        _uiState.update {
            it.copy(
                delegateOption = option,
                matches = emptyList(),
                lastSearchTimeMs = null,
                errorMessage = null,
                noticeMessage = null,
            )
        }
        prepareAndAutoIndex(allowGpuFallback = false)
    }

    fun selectStore(option: VectorStoreOption) {
        if (_uiState.value.storeOption == option || _uiState.value.isBusy) return
        _uiState.update {
            it.copy(
                storeOption = option,
                matches = emptyList(),
                lastSearchTimeMs = null,
                errorMessage = null,
                noticeMessage = null,
            )
        }
        prepareAndAutoIndex(allowGpuFallback = true)
    }

    fun reindexSampleGallery() {
        val state = _uiState.value
        if (state.isBusy) return
        activeJob?.cancel()
        activeJob =
            viewModelScope.launch {
                runIndexSampleGallery()
            }
    }

    fun clearIndex() {
        if (_uiState.value.isBusy) return
        activeJob?.cancel()
        activeJob =
            viewModelScope.launch {
                withContext(engineDispatcher) {
                    helper?.clearIndex()
                }
                _uiState.update {
                    it.copy(
                        indexedCount = 0,
                        matches = emptyList(),
                        lastSearchTimeMs = null,
                    )
                }
            }
    }

    fun addCustomPhoto(uri: Uri) {
        if (_uiState.value.isBusy) return
        activeJob?.cancel()
        _uiState.update {
            it.copy(isIndexing = true, indexingProgress = 0, indexingTotal = 1, errorMessage = null)
        }
        activeJob =
            viewModelScope.launch {
                val indexedCount =
                    withContext(engineDispatcher) {
                        val h = helper ?: return@withContext _uiState.value.indexedCount
                        val id = "user_photo_${System.currentTimeMillis()}"
                        h.insertImage(
                            id = id,
                            imageUri = uri,
                            metadata =
                                mapOf(
                                    "label" to "Custom Photo",
                                    "uri" to uri.toString(),
                                ),
                        )
                        decodeUriThumbnail(uri)?.let { thumbnailCache[id] = it }
                        h.getAllRecordIds().size
                    }
                _uiState.update {
                    it.copy(
                        isIndexing = false,
                        indexingProgress = 1,
                        indexedCount = indexedCount,
                    )
                }
            }
    }

    fun search() {
        val state = _uiState.value
        if (!state.canSearch) return
        val query = state.query.trim()

        activeJob?.cancel()
        _uiState.update { it.copy(isSearching = true, errorMessage = null) }
        activeJob =
            viewModelScope.launch {
                val bundle =
                    withContext(engineDispatcher) {
                        runCatching { helper?.retrieve(query, topK = 5) }
                            .onFailure { Log.e(TAG, "Search failed", it) }
                            .getOrNull()
                    }
                if (bundle == null) {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            errorMessage = lastErrorMessage ?: "Search failed.",
                        )
                    }
                    return@launch
                }
                val uiMatches =
                    bundle.results.map { res ->
                        val meta = res.metadata()
                        val sample = BundledGalleryItems.firstOrNull { it.id == res.id() }
                        val label = meta["label"] ?: sample?.label ?: res.id()
                        val bmp =
                            thumbnailCache[res.id()]
                                ?: sample?.let { loadAssetThumbnail(it.id, it.assetPath) }
                                ?: meta["uri"]?.let { runCatching { decodeUriThumbnail(Uri.parse(it)) }.getOrNull() }
                        SearchMatchUi(
                            id = res.id(),
                            label = label,
                            score = res.score(),
                            thumbnail = bmp,
                            sourceTag = if (sample != null) "Sample" else "Photo Picker",
                        )
                    }
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        matches = uiMatches,
                        lastSearchTimeMs = bundle.inferenceTime,
                    )
                }
            }
    }

    fun retryInitialization() {
        prepareAndAutoIndex(allowGpuFallback = true)
    }

    private fun prepareAndAutoIndex(allowGpuFallback: Boolean) {
        val targetDelegate = _uiState.value.delegateOption
        val targetStore = _uiState.value.storeOption
        activeJob?.cancel()
        lastErrorMessage = null
        _uiState.update {
            it.copy(isModelLoading = true, errorMessage = null)
        }
        activeJob =
            viewModelScope.launch {
                val outcome =
                    withContext(engineDispatcher) {
                        val instance =
                            helper
                                ?: SemanticRetrieverHelper(
                                    context = context,
                                    currentDelegate = targetDelegate.id,
                                    currentStore = targetStore.id,
                                    listener = listener,
                                ).also { helper = it }
                        instance.currentDelegate = targetDelegate.id
                        instance.currentStore = targetStore.id
                        if (instance.setupSemanticRetriever()) {
                            Triple(true, targetDelegate, null as String?)
                        } else if (
                            allowGpuFallback && targetDelegate == RetrieverDelegateOption.GPU
                        ) {
                            Log.w(TAG, "GPU initialization unavailable; falling back to CPU")
                            instance.currentDelegate = SemanticRetrieverHelper.DELEGATE_CPU
                            val cpuOk = instance.setupSemanticRetriever()
                            Triple(
                                cpuOk,
                                if (cpuOk) RetrieverDelegateOption.CPU else targetDelegate,
                                if (cpuOk) "GPU unavailable on this device — using CPU." else null,
                            )
                        } else {
                            Triple(false, targetDelegate, null as String?)
                        }
                    }

                val (ready, activeDelegate, notice) = outcome
                if (!ready) {
                    _uiState.update {
                        it.copy(
                            delegateOption = activeDelegate,
                            isModelLoading = false,
                            noticeMessage = notice,
                            errorMessage = lastErrorMessage ?: "Retriever failed to initialize.",
                        )
                    }
                    return@launch
                }

                val existingIds =
                    withContext(engineDispatcher) {
                        helper?.getAllRecordIds().orEmpty()
                    }
                _uiState.update {
                    it.copy(
                        delegateOption = activeDelegate,
                        isModelLoading = false,
                        noticeMessage = notice,
                        indexedCount = existingIds.size,
                    )
                }
                val existingSet = existingIds.toSet()
                val sampleIds = BundledGalleryItems.mapTo(mutableSetOf()) { it.id }
                val missingSamples = BundledGalleryItems.filter { it.id !in existingSet }
                // Resume partial initial indexing if a previous run was interrupted mid-gallery.
                if (existingSet.all { it in sampleIds } && missingSamples.isNotEmpty()) {
                    runIndexSampleGallery(missingSamples)
                }
            }
    }

    private suspend fun runIndexSampleGallery(
        items: List<GalleryItemSpec> = BundledGalleryItems,
    ) {
        _uiState.update {
            it.copy(
                isIndexing = true,
                indexingProgress = 0,
                indexingTotal = items.size,
                errorMessage = null,
            )
        }
        var latestCount = _uiState.value.indexedCount
        for ((idx, item) in items.withIndex()) {
            val ok =
                withContext(engineDispatcher) {
                    runCatching {
                            val h = helper ?: return@runCatching false
                            h.insertImage(
                                id = item.id,
                                imageUri = item.assetUri,
                                metadata =
                                    mapOf(
                                        "label" to item.label,
                                        "assetPath" to item.assetPath,
                                    ),
                            )
                            loadAssetThumbnail(item.id, item.assetPath)
                            true
                        }
                        .onFailure { Log.e(TAG, "Failed to index ${item.id}", it) }
                        .getOrDefault(false)
                }
            if (ok) {
                latestCount += 1
            }
            _uiState.update { it.copy(indexingProgress = idx + 1) }
        }
        val finalCount =
            withContext(engineDispatcher) {
                helper?.getAllRecordIds()?.size ?: latestCount
            }
        _uiState.update {
            it.copy(
                isIndexing = false,
                indexedCount = finalCount,
            )
        }
    }

    private fun loadAssetThumbnail(id: String, assetPath: String): Bitmap? {
        thumbnailCache[id]?.let { return it }
        return runCatching {
                context.assets.open(assetPath).use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            }
            .onSuccess { bmp -> if (bmp != null) thumbnailCache[id] = bmp }
            .getOrNull()
    }

    private fun decodeUriThumbnail(uri: Uri): Bitmap? {
        return runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            }
            .getOrNull()
    }

    override fun onCleared() {
        super.onCleared()
        val existing = helper
        helper = null
        engineExecutor.execute {
            existing?.clearSemanticRetriever()
            engineExecutor.shutdown()
        }
    }

    companion object {
        private const val TAG = "SemanticRetrieverVM"

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SemanticRetrieverViewModel(context.applicationContext) as T
                }
            }
    }
}
