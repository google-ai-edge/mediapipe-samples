package com.google.mediapipe.examples.universalembedder

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedder
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedderOptions
import java.io.File
import java.io.FileOutputStream

/** An embedding plus how long it took to produce. */
data class EmbeddingRun(val embedding: Embedding, val millis: Long)

/**
 * Thin wrapper around [UniversalEmbedder].
 *
 * The embedder maps text, images and audio into a single shared vector space, which is what makes
 * cross-modal comparison ("does this sentence describe this picture?") possible.
 *
 * Not thread safe: callers must serialize access (see the single-thread dispatcher in
 * [MainActivity]).
 */
class UniversalEmbedderHelper(private val context: Context) {

    private var embedder: UniversalEmbedder? = null
    private var currentlyOnGpu = false

    val isReady: Boolean
        get() = embedder != null

    /** Builds the engine, or rebuilds it if the requested accelerator changed. */
    fun prepare(useGpu: Boolean) {
        if (embedder != null && currentlyOnGpu == useGpu) return
        embedder?.close()
        embedder = null

        // The LiteRT JNI layer opens the model by absolute path, so it cannot be read straight out
        // of the APK's assets; copy it into filesDir on first launch.
        val modelFile = File(context.filesDir, MODEL_NAME)
        if (!modelFile.exists()) {
            Log.i(TAG, "Copying $MODEL_NAME from assets to filesDir…")
            context.assets.open(MODEL_NAME).use { input ->
                FileOutputStream(modelFile).use { output -> input.copyTo(output) }
            }
        }

        val delegate = if (useGpu) Delegate.GPU else Delegate.CPU
        val options = UniversalEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelFile.absolutePath).build())
            // Delegates are per-modality: the text tower encodes strings, the vision tower images.
            .setTextDelegate(delegate)
            .setVisionDelegate(delegate)
            .setCacheDir(context.cacheDir.absolutePath)
            // Embeddings are unit length, so a dot product is the cosine similarity.
            .setL2Normalize(true)
            .build()

        val startedAt = System.currentTimeMillis()
        embedder = UniversalEmbedder.createFromOptions(context, options)
        currentlyOnGpu = useGpu
        Log.i(TAG, "Embedder ready on $delegate in ${System.currentTimeMillis() - startedAt}ms")
    }

    fun embedText(text: String): EmbeddingRun {
        val engine = checkNotNull(embedder) { "prepare() must be called first" }
        val startedAt = System.currentTimeMillis()
        val result = engine.embedText(text)
        return EmbeddingRun(result.embeddings().first(), System.currentTimeMillis() - startedAt)
    }

    fun embedImage(bitmap: Bitmap): EmbeddingRun {
        val engine = checkNotNull(embedder) { "prepare() must be called first" }
        val image = BitmapImageBuilder(bitmap).build()
        val startedAt = System.currentTimeMillis()
        val result = engine.embedImage(image)
        return EmbeddingRun(result.embeddings().first(), System.currentTimeMillis() - startedAt)
    }

    fun close() {
        embedder?.close()
        embedder = null
    }

    companion object {
        private const val TAG = "UniversalEmbedder"
        private const val MODEL_NAME = "embedding_gemma_v2_q4c_multisig.litertlm"

        /** Cosine similarity, in [-1, 1]. Identical inputs score 1. */
        fun similarity(a: Embedding, b: Embedding): Double =
            UniversalEmbedder.cosineSimilarity(a, b)
    }
}
