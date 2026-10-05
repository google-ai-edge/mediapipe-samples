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

package com.google.mediapipe.examples.semanticretriever

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mediapipe.tasks.retrieval.semanticretriever.RetrievalResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var helper: SemanticRetrieverHelper? = null
    private lateinit var tvStatus: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var spinnerStatus: ProgressBar
    private lateinit var progressIndex: LinearProgressIndicator
    private lateinit var btnEmbedImages: MaterialButton
    private lateinit var rvResults: RecyclerView
    private lateinit var resultsAdapter: ResultsAdapter
    private lateinit var etQuery: TextInputEditText
    private lateinit var tilQuery: TextInputLayout
    private lateinit var chipGroup: ChipGroup
    private lateinit var toggleStore: MaterialButtonToggleGroup
    private lateinit var switchGpu: SwitchMaterial

    /** True while the model is loading, indexing or searching. */
    private var isBusy = false

    /** True once the sample images have been embedded into the current vector store. */
    private var isIndexed = false

    /**
     * Every call into the retriever runs here. A single thread guarantees that model loading,
     * store switching, indexing and searching can never overlap — the native engine is not
     * reentrant, and overlapping work was what produced "EmbeddingEngine is not initialized".
     */
    private val retrieverDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** In-flight store switch, cancelled if the user toggles again before it lands. */
    private var storeJob: Job? = null

    private var useAppSearch = true
    private var useGpu = true

    private val sampleImages = listOf(
        "red_apple.jpg",
        "yellow_banana.jpg",
        "cute_cat.jpg",
        "fast_car.jpg",
        "green_tree.jpg",
        "blue_sky.jpg",
        "coffee_mug.jpg",
        "open_book.jpg",
        "sunny_beach.jpg",
        "snowy_mountain.jpg"
    )

    private val suggestions = listOf(
        "a piece of fruit",
        "an animal",
        "something to drink",
        "a vacation spot",
        "a vehicle"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvEmpty = findViewById(R.id.tvEmpty)
        spinnerStatus = findViewById(R.id.spinnerStatus)
        progressIndex = findViewById(R.id.progressIndex)
        btnEmbedImages = findViewById(R.id.btnEmbedImages)
        rvResults = findViewById(R.id.rvResults)
        etQuery = findViewById(R.id.etQuery)
        tilQuery = findViewById(R.id.tilQuery)
        chipGroup = findViewById(R.id.chipSuggestions)

        resultsAdapter = ResultsAdapter()
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = resultsAdapter

        toggleStore = findViewById(R.id.toggleStore)
        toggleStore.check(R.id.btnAppSearch)
        toggleStore.addOnButtonCheckedListener { _, checkedId, isChecked ->
            // Only react to the newly selected button, not the deselected one.
            if (isChecked) {
                useAppSearch = checkedId == R.id.btnAppSearch
                reload()
            }
        }

        switchGpu = findViewById(R.id.switchGpu)
        switchGpu.setOnCheckedChangeListener { _, checked ->
            useGpu = checked
            reload()
        }

        btnEmbedImages.setOnClickListener { embedSampleImages() }

        tilQuery.setEndIconOnClickListener { runSearch() }
        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch()
                true
            } else {
                false
            }
        }

        suggestions.forEach { suggestion ->
            val chip = Chip(this).apply {
                text = suggestion
                isClickable = true
                isCheckable = false
                setOnClickListener {
                    etQuery.setText(suggestion)
                    runSearch()
                }
            }
            chipGroup.addView(chip)
        }

        reload()
    }

    private fun runSearch() {
        if (isBusy) {
            Toast.makeText(
                this,
                "Busy — wait for the current step to finish",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val query = etQuery.text?.toString().orEmpty().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter a search query first", Toast.LENGTH_SHORT).show()
            return
        }
        hideKeyboard()
        searchImages(query)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etQuery.windowToken, 0)
    }

    /** Shows/hides the small spinner next to the status line and disables input while busy. */
    private fun setBusy(busy: Boolean, status: String) {
        isBusy = busy
        spinnerStatus.visibility = if (busy) View.VISIBLE else View.GONE
        btnEmbedImages.isEnabled = !busy
        switchGpu.isEnabled = !busy
        // Disabling the group alone does not propagate to its buttons.
        for (i in 0 until toggleStore.childCount) {
            toggleStore.getChildAt(i).isEnabled = !busy
        }
        tvStatus.text = status
        refreshSearchControls()
    }

    /**
     * Indexing is a one-shot action per vector store: once the samples are in, the index
     * button goes away and the search UI takes over.
     */
    private fun setIndexed(indexed: Boolean) {
        isIndexed = indexed
        btnEmbedImages.visibility = if (indexed) View.GONE else View.VISIBLE
        refreshSearchControls()
        updateEmptyState()
    }

    /** Searching only makes sense once something has been indexed and nothing else is running. */
    private fun refreshSearchControls() {
        val enabled = isIndexed && !isBusy
        tilQuery.isEnabled = enabled
        etQuery.isEnabled = enabled
        tilQuery.alpha = if (enabled) 1f else 0.5f
        chipGroup.alpha = if (enabled) 1f else 0.5f
        for (i in 0 until chipGroup.childCount) {
            chipGroup.getChildAt(i).isEnabled = enabled
        }
    }

    private fun reload() {
        // Switching stores is a full reset: no index, no query, no results.
        storeJob?.cancel()
        setIndexed(false)
        val accelerator = if (useGpu) "GPU" else "CPU"
        val storeName = if (useAppSearch) "AppSearch" else "SQLite"
        setBusy(true, "Loading $storeName on $accelerator…")
        etQuery.setText("")
        progressIndex.visibility = View.GONE
        progressIndex.progress = 0
        resultsAdapter.setResults(emptyList())
        updateEmptyState()

        storeJob = uiScope.launch {
            try {
                withContext(retrieverDispatcher) {
                    val retriever = helper ?: SemanticRetrieverHelper(this@MainActivity).also {
                        helper = it
                    }
                    // Loads the model on first run only; subsequent switches reuse the engine.
                    retriever.prepareEmbedder(useGpu)
                    retriever.openStore(useAppSearch)
                    // Start from a known-empty store without deleting files under a live store.
                    retriever.clear(sampleImages.map { it.substringBefore(".") })
                }
                setBusy(false, "Ready · $storeName on $accelerator · store is empty")
                setIndexed(false)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error opening store", e)
                setBusy(false, "Initialization failed: ${e.message}")
            }
        }
    }

    private fun embedSampleImages() {
        val retriever = helper
        if (retriever == null || !retriever.isReady || isBusy) {
            Toast.makeText(this, "Still getting ready, please wait…", Toast.LENGTH_SHORT).show()
            return
        }
        setBusy(true, "Indexing…")
        progressIndex.visibility = View.VISIBLE
        progressIndex.progress = 0
        val started = System.currentTimeMillis()
        uiScope.launch {
            try {
                sampleImages.forEachIndexed { index, imageName ->
                    val id = imageName.substringBefore(".")
                    val label = id.replace('_', ' ')
                    tvStatus.text = "Embedding ${index + 1}/${sampleImages.size}: $label"
                    progressIndex.setProgressCompat(index * 100 / sampleImages.size, true)
                    withContext(retrieverDispatcher) {
                        val assetUri = Uri.parse("file:///android_asset/images/$imageName")
                        retriever.embedImage(id, assetUri)
                    }
                    Log.d("MainActivity", "Embedded $id")
                }
                val seconds = (System.currentTimeMillis() - started) / 1000.0
                val accelerator = if (useGpu) "GPU" else "CPU"
                progressIndex.setProgressCompat(100, true)
                // Let the bar finish animating, then tuck it away again.
                progressIndex.postDelayed({ progressIndex.visibility = View.GONE }, 600)
                setBusy(
                    false,
                    "Indexed ${sampleImages.size} images in %.1fs on $accelerator · ready to search"
                        .format(seconds)
                )
                setIndexed(true)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error embedding", e)
                progressIndex.visibility = View.GONE
                setBusy(false, "Indexing failed: ${e.message}")
            }
        }
    }

    private fun searchImages(query: String) {
        val retriever = helper
        if (retriever == null || !retriever.isReady) {
            Toast.makeText(this, "Still getting ready, please wait…", Toast.LENGTH_SHORT).show()
            return
        }
        setBusy(true, "Searching for \"$query\"…")
        uiScope.launch {
            try {
                val started = System.currentTimeMillis()
                val results = withContext(retrieverDispatcher) { retriever.searchImages(query, 5) }
                val millis = System.currentTimeMillis() - started
                setBusy(false, "${results.size} results for \"$query\" in ${millis}ms")
                resultsAdapter.setResults(results)
                updateEmptyState()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error searching", e)
                setBusy(false, "Search failed: ${e.message}")
            }
        }
    }

    private fun updateEmptyState() {
        tvEmpty.visibility = if (resultsAdapter.itemCount == 0) View.VISIBLE else View.GONE
        tvEmpty.setText(if (isIndexed) R.string.empty_no_results else R.string.empty_not_indexed)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
        val retriever = helper
        helper = null
        // Close off the UI thread: releasing the native engine can block.
        Executors.newSingleThreadExecutor().execute {
            retriever?.close()
            retrieverDispatcher.close()
        }
    }
}

class ResultsAdapter : RecyclerView.Adapter<ResultsAdapter.ResultViewHolder>() {

    private val results = mutableListOf<RetrievalResult>()

    // Assets never change at runtime, so a tiny in-memory cache is all we need. Decoding
    // directly from assets also avoids stale entries from an image loader's disk cache.
    private val bitmapCache = object : LruCache<String, Bitmap>(16) {}

    fun setResults(newResults: List<RetrievalResult>) {
        results.clear()
        results.addAll(newResults)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val result = results[position]
        val id = result.id()
        holder.tvRank.text = (position + 1).toString()
        holder.tvId.text = id.replace('_', ' ').replaceFirstChar { it.uppercase() }

        val score = result.score()
        holder.tvScore.text = "Similarity %.4f".format(score)
        // Scores are cosine similarities in [-1, 1]; map to a 0-100 bar.
        holder.pbScore.setProgressCompat(((score + 1f) / 2f * 100).toInt().coerceIn(0, 100), true)

        holder.ivResult.setImageBitmap(loadAsset(holder.itemView.context, "images/$id.jpg"))
    }

    private fun loadAsset(context: Context, path: String): Bitmap? {
        bitmapCache.get(path)?.let { return it }
        return try {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }?.also { bitmapCache.put(path, it) }
        } catch (e: Exception) {
            Log.e("ResultsAdapter", "Unable to load asset $path", e)
            null
        }
    }

    override fun getItemCount(): Int = results.size

    class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivResult: ShapeableImageView = itemView.findViewById(R.id.ivResult)
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        val tvId: TextView = itemView.findViewById(R.id.tvId)
        val tvScore: TextView = itemView.findViewById(R.id.tvScore)
        val pbScore: LinearProgressIndicator = itemView.findViewById(R.id.pbScore)
    }
}
