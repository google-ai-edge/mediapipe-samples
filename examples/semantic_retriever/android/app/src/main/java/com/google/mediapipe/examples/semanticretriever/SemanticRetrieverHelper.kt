package com.google.mediapipe.examples.semanticretriever

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.retrieval.components.AppSearchVectorStore
import com.google.mediapipe.tasks.retrieval.components.SqliteVectorStore
import com.google.mediapipe.tasks.retrieval.components.VectorStore
import com.google.mediapipe.tasks.retrieval.semanticretriever.RetrievalResult
import com.google.mediapipe.tasks.retrieval.semanticretriever.SemanticRetriever
import com.google.mediapipe.tasks.retrieval.semanticretriever.SemanticRetrieverComponents
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedder
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedderOptions
import java.io.File
import java.io.FileOutputStream

/**
 * Owns the embedding engine and the currently selected vector store.
 *
 * The [UniversalEmbedder] wraps a ~470 MB LiteRT model, so it is created exactly once and kept
 * alive for the lifetime of the helper. Switching vector stores only swaps the cheap
 * [SemanticRetriever] / [VectorStore] pair on top of that same embedder — tearing the engine down
 * and standing a second one up would either fail to mmap the model file or leave callers holding a
 * closed engine.
 *
 * This class is not thread safe: callers must serialize access (see the single-thread dispatcher
 * in [MainActivity]).
 */
class SemanticRetrieverHelper(private val context: Context) {

    private var universalEmbedder: UniversalEmbedder? = null
    private var semanticRetriever: SemanticRetriever? = null
    private var currentlyOnGpu = false

    /** True once a vector store has been opened and the helper can accept work. */
    val isReady: Boolean
        get() = semanticRetriever != null

    /**
     * Loads the embedding model. Safe to call repeatedly; the engine is only built the first time.
     */
    fun prepareEmbedder(useGpu: Boolean = false) {
        if (universalEmbedder != null && currentlyOnGpu == useGpu) return

        // The accelerator is baked into the engine, so changing it means rebuilding it.
        if (universalEmbedder != null) {
            semanticRetriever?.close()
            semanticRetriever = null
            universalEmbedder?.close()
            universalEmbedder = null
        }

        // The LiteRT JNI layer opens the model by absolute path, so it cannot be read straight out
        // of the APK's assets; copy it into filesDir on first launch.
        val modelFile = File(context.filesDir, MODEL_NAME)
        if (!modelFile.exists()) {
            Log.i(TAG, "Copying $MODEL_NAME from assets to filesDir…")
            context.assets.open(MODEL_NAME).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Model copied (${modelFile.length()} bytes).")
        }

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelFile.absolutePath)
            .build()

        // Delegates are per-modality: the text tower encodes queries, the vision tower encodes
        // images. Both are pushed onto the same accelerator here.
        val delegate = if (useGpu) Delegate.GPU else Delegate.CPU
        val embedderOptions = UniversalEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .setTextDelegate(delegate)
            .setVisionDelegate(delegate)
            .setCacheDir(context.cacheDir.absolutePath)
            .build()

        val startedAt = System.currentTimeMillis()
        universalEmbedder = UniversalEmbedder.createFromOptions(context, embedderOptions)
        currentlyOnGpu = useGpu
        Log.i(TAG, "Embedding engine ready on $delegate in ${System.currentTimeMillis() - startedAt}ms")
    }

    /**
     * Closes the previous store (if any) and opens [useAppSearch] ? AppSearch : SQLite, reusing the
     * already loaded embedding engine.
     */
    fun openStore(useAppSearch: Boolean) {
        val embedder = requireNotNull(universalEmbedder) {
            "prepareEmbedder() must be called before openStore()"
        }

        // SemanticRetriever.close() closes the vector store it was built with, and leaves the
        // embedder untouched — which is exactly what we want here.
        semanticRetriever?.close()
        semanticRetriever = null

        val vectorStore: VectorStore = if (useAppSearch) {
            AppSearchVectorStore(context, DB_NAME)
        } else {
            SqliteVectorStore(context, DB_NAME, 768)
        }

        val components = SemanticRetrieverComponents()
            .setVectorStore(vectorStore)
            .addProvider(embedder.provider)

        semanticRetriever = SemanticRetriever.createFromComponents(context, components)
        Log.i(TAG, "Opened ${if (useAppSearch) "AppSearch" else "SQLite"} vector store.")
    }

    /**
     * Drops [ids] from the open store. Used to reset state when switching backends, which is safer
     * than deleting the database files out from under a live store.
     */
    fun clear(ids: List<String>) {
        semanticRetriever?.delete(ids)
    }

    fun embedImage(id: String, uri: Uri) {
        checkNotNull(semanticRetriever) { "No vector store is open" }.insertImage(id, uri)
    }

    fun searchImages(query: String, limit: Int = 5): List<RetrievalResult> {
        return semanticRetriever?.retrieve(query, limit) ?: emptyList()
    }

    fun close() {
        semanticRetriever?.close()
        semanticRetriever = null
        universalEmbedder?.close()
        universalEmbedder = null
    }

    private companion object {
        const val TAG = "SemanticRetriever"
        const val MODEL_NAME = "embedding_gemma_v2_q4c_multisig.litertlm"
        const val DB_NAME = "semantic_db"
    }
}
