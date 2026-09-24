package com.google.mediapipe.examples.universalembedder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mediapipe.tasks.components.containers.Embedding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Embeds two inputs — each either text or an image — and reports their cosine similarity.
 *
 * Because [com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedder] projects every
 * modality into the same vector space, "a yellow fruit" can be compared directly against a photo of
 * a banana.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var helper: UniversalEmbedderHelper
    private lateinit var slotA: InputSlot
    private lateinit var slotB: InputSlot
    private lateinit var btnCompare: MaterialButton
    private lateinit var switchGpu: SwitchMaterial
    private lateinit var tvStatus: TextView
    private lateinit var spinnerStatus: ProgressBar
    private lateinit var cardResult: MaterialCardView
    private lateinit var tvSimilarity: TextView
    private lateinit var tvVectorInfo: TextView
    private lateinit var pbSimilarity: LinearProgressIndicator

    /** The engine is not reentrant, so every call into it is serialized here. */
    private val embedderDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private var isBusy = false
    private var useGpu = false

    private val sampleImages = listOf(
        "red_apple.jpg", "yellow_banana.jpg", "cute_cat.jpg", "fast_car.jpg", "green_tree.jpg",
        "blue_sky.jpg", "coffee_mug.jpg", "open_book.jpg", "sunny_beach.jpg", "snowy_mountain.jpg"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCompare = findViewById(R.id.btnCompare)
        switchGpu = findViewById(R.id.switchGpu)
        tvStatus = findViewById(R.id.tvStatus)
        spinnerStatus = findViewById(R.id.spinnerStatus)
        cardResult = findViewById(R.id.cardResult)
        tvSimilarity = findViewById(R.id.tvSimilarity)
        tvVectorInfo = findViewById(R.id.tvVectorInfo)
        pbSimilarity = findViewById(R.id.pbSimilarity)

        slotA = InputSlot(findViewById(R.id.slotA), getString(R.string.slot_a)) { invalidateResult() }
        slotB = InputSlot(findViewById(R.id.slotB), getString(R.string.slot_b)) { invalidateResult() }

        // A text/image pairing out of the box, so the cross-modal point lands immediately.
        slotA.setText("a yellow piece of fruit")
        slotB.setMode(InputMode.IMAGE)
        slotB.selectImage(1) // yellow_banana.jpg

        btnCompare.setOnClickListener { compare() }
        switchGpu.setOnCheckedChangeListener { _, checked ->
            useGpu = checked
            prepare()
        }

        helper = UniversalEmbedderHelper(this)
        prepare()
    }

    /** A shown score belongs to the inputs that produced it, so drop it when they change. */
    private fun invalidateResult() {
        cardResult.visibility = View.GONE
        if (::slotA.isInitialized) slotA.clearInfo()
        if (::slotB.isInitialized) slotB.clearInfo()
    }

    private fun prepare() {
        job?.cancel()
        setBusy(true, "Loading model on ${if (useGpu) "GPU" else "CPU"}…")
        cardResult.visibility = View.GONE
        job = uiScope.launch {
            try {
                withContext(embedderDispatcher) { helper.prepare(useGpu) }
                setBusy(false, "Ready · running on ${if (useGpu) "GPU" else "CPU"}")
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing embedder", e)
                setBusy(false, "Initialization failed: ${e.message}")
            }
        }
    }

    private fun compare() {
        if (isBusy || !helper.isReady) {
            Toast.makeText(this, "Still getting ready, please wait…", Toast.LENGTH_SHORT).show()
            return
        }
        val inputA = slotA.currentInput()
        val inputB = slotB.currentInput()
        if (inputA == null || inputB == null) {
            Toast.makeText(this, "Fill in both inputs first", Toast.LENGTH_SHORT).show()
            return
        }
        hideKeyboard()
        setBusy(true, "Embedding both inputs…")

        job = uiScope.launch {
            try {
                val runA = withContext(embedderDispatcher) { embed(inputA) }
                slotA.showInfo("${inputA.label} · ${runA.embedding.floatEmbedding().size}d · ${runA.millis}ms")

                val runB = withContext(embedderDispatcher) { embed(inputB) }
                slotB.showInfo("${inputB.label} · ${runB.embedding.floatEmbedding().size}d · ${runB.millis}ms")

                val similarity = UniversalEmbedderHelper.similarity(runA.embedding, runB.embedding)
                showResult(similarity, runA.embedding, runB.embedding, runA.millis + runB.millis)
            } catch (e: Exception) {
                Log.e(TAG, "Error embedding", e)
                setBusy(false, "Embedding failed: ${e.message}")
            }
        }
    }

    private fun embed(input: SlotInput): EmbeddingRun = when (input) {
        is SlotInput.Text -> helper.embedText(input.text)
        is SlotInput.Image -> helper.embedImage(input.bitmap)
    }

    private fun showResult(
        similarity: Double,
        embeddingA: Embedding,
        embeddingB: Embedding,
        totalMillis: Long
    ) {
        cardResult.visibility = View.VISIBLE
        tvSimilarity.text = "%.4f".format(similarity)
        // Cosine similarity is in [-1, 1]; map onto the 0-100 bar.
        pbSimilarity.setProgressCompat((((similarity + 1) / 2) * 100).toInt().coerceIn(0, 100), true)
        // Show both vectors: the score above is the angle between exactly these two.
        tvVectorInfo.text = "A ${preview(embeddingA)}\nB ${preview(embeddingB)}"
        setBusy(false, "Embedded both inputs in ${totalMillis}ms on ${if (useGpu) "GPU" else "CPU"}")
    }

    /** First few dimensions of an embedding, e.g. `[-0.010, +0.026, …] 768d`. */
    private fun preview(embedding: Embedding): String {
        val vector = embedding.floatEmbedding()
        val head = vector.take(4).joinToString(", ") { "%+.3f".format(it) }
        return "[$head, …] ${vector.size}d"
    }

    private fun setBusy(busy: Boolean, status: String) {
        isBusy = busy
        spinnerStatus.visibility = if (busy) View.VISIBLE else View.GONE
        btnCompare.isEnabled = !busy
        switchGpu.isEnabled = !busy
        tvStatus.text = status
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(btnCompare.windowToken, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
        val engine = helper
        // Releasing the native engine can block, so keep it off the UI thread.
        Executors.newSingleThreadExecutor().execute {
            engine.close()
            embedderDispatcher.close()
        }
    }

    /** Which kind of input a slot currently holds. */
    private enum class InputMode { TEXT, IMAGE }

    private sealed interface SlotInput {
        val label: String

        data class Text(val text: String) : SlotInput {
            override val label get() = "text"
        }

        data class Image(val name: String, val bitmap: Bitmap) : SlotInput {
            override val label get() = name.substringBefore(".")
        }
    }

    /** Binds one included `view_input_slot` and tracks its text/image state. */
    private inner class InputSlot(root: View, label: String, val onChanged: () -> Unit) {
        private val toggleMode: MaterialButtonToggleGroup = root.findViewById(R.id.toggleMode)
        private val tilText: TextInputLayout = root.findViewById(R.id.tilText)
        private val etText: TextInputEditText = root.findViewById(R.id.etText)
        private val scrollImages: View = root.findViewById(R.id.scrollImages)
        private val imageStrip: LinearLayout = root.findViewById(R.id.imageStrip)
        private val tvInfo: TextView = root.findViewById(R.id.tvSlotInfo)

        private var mode = InputMode.TEXT
        private var selectedImage = 0
        private val thumbCards = mutableListOf<MaterialCardView>()

        init {
            root.findViewById<TextView>(R.id.tvSlotLabel).text = label
            toggleMode.check(R.id.btnModeText)
            toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    setMode(if (checkedId == R.id.btnModeImage) InputMode.IMAGE else InputMode.TEXT)
                    onChanged()
                }
            }
            etText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) = onChanged()
            })
            buildImageStrip()
            applyMode()
        }

        private fun buildImageStrip() {
            sampleImages.forEachIndexed { index, name ->
                val card = LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.item_thumb, imageStrip, false) as MaterialCardView
                card.findViewById<ImageView>(R.id.ivThumb).setImageBitmap(loadThumb(name))
                card.setOnClickListener {
                    selectImage(index)
                    onChanged()
                }
                imageStrip.addView(card)
                thumbCards += card
            }
            highlightSelection()
        }

        fun setMode(newMode: InputMode) {
            mode = newMode
            if (toggleMode.checkedButtonId !=
                if (newMode == InputMode.IMAGE) R.id.btnModeImage else R.id.btnModeText
            ) {
                toggleMode.check(
                    if (newMode == InputMode.IMAGE) R.id.btnModeImage else R.id.btnModeText
                )
            }
            applyMode()
        }

        private fun applyMode() {
            tilText.visibility = if (mode == InputMode.TEXT) View.VISIBLE else View.GONE
            scrollImages.visibility = if (mode == InputMode.IMAGE) View.VISIBLE else View.GONE
            tvInfo.visibility = View.GONE
        }

        fun setText(text: String) = etText.setText(text)

        fun selectImage(index: Int) {
            selectedImage = index
            highlightSelection()
        }

        fun clearInfo() {
            tvInfo.visibility = View.GONE
        }

        private fun highlightSelection() {
            thumbCards.forEachIndexed { index, card ->
                card.strokeColor = if (index == selectedImage) {
                    getColor(R.color.mp_color_primary)
                } else {
                    getColor(R.color.thumb_stroke)
                }
            }
        }

        fun showInfo(text: String) {
            tvInfo.text = text
            tvInfo.visibility = View.VISIBLE
        }

        fun currentInput(): SlotInput? = when (mode) {
            InputMode.TEXT -> etText.text?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { SlotInput.Text(it) }

            InputMode.IMAGE -> {
                val name = sampleImages[selectedImage]
                loadFull(name)?.let { SlotInput.Image(name, it) }
            }
        }
    }

    private fun loadThumb(name: String): Bitmap? = decode(name, sampleSize = 8)

    private fun loadFull(name: String): Bitmap? = decode(name, sampleSize = 2)

    private fun decode(name: String, sampleSize: Int): Bitmap? = try {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        assets.open("images/$name").use { BitmapFactory.decodeStream(it, null, options) }
    } catch (e: Exception) {
        Log.e(TAG, "Unable to load images/$name", e)
        null
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
