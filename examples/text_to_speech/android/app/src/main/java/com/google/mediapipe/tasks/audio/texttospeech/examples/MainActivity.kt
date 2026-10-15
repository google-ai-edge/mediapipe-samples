// Copyright 2026 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

@file:Suppress("PackageName", "KotlinArray")

package com.google.mediapipe.tasks.audio.texttospeech.examples

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.google.mediapipe.tasks.audio.texttospeech.SynthesisOptions
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeech
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeechOptions
import com.google.mediapipe.tasks.audio.texttospeech.VoiceInfo
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlinx.coroutines.runBlocking

/**
 * Interactive on-device demo for the MediaPipe-TTS Kotlin SDK running Kokoro-82M in-process.
 *
 * UI: pick one of five sample sentences or type your own, choose a language and a voice, then tap
 * **Speak** (unary) or **Stream** (clause-by-clause streaming). The model is fixed to the Kokoro
 * `.litertlm` bundle in the app's external files dir (pushed by `scripts/run_android_demo.sh`); it
 * is not user-configurable.
 *
 * Scripted launch (used by `scripts/run_android_demo.sh`; all extras optional). Passing `text` or
 * `voice` triggers an automatic synthesis once the engine is ready:
 * ```
 * adb shell am start -n com.google.mediapipe.tasks.audio.texttospeech.examples/.MainActivity \
 *   --es text "Hello from Kokoro" --es voice af_heart --ei num_threads 4 --ei target_bucket 64 \
 *   --ez play_audio true --ez stream false --ez autorun true
 * ```
 *
 * Progress is shown on screen and mirrored to logcat under tag [TAG]. Each synthesis finishes with
 * a machine-readable `MEDIAPIPE_TTS_DEMO_RESULT status=OK|ERROR ...` line.
 */
class MainActivity : Activity() {
  private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
    Thread(r, "mediapipe-tts-demo")
  }
  private val busy = AtomicBoolean(false)
  @Volatile private var stopRequested = false
  @Volatile private var activeTrack: AudioTrack? = null

  // Engine state (created once on the worker thread, closed in onDestroy).
  @Volatile private var tts: TextToSpeech? = null
  private var voices: List<VoiceInfo> = emptyList()
  private var languageTags: List<String> = emptyList()
  private var voicesForLanguage: List<VoiceInfo> = emptyList()
  private var currentLanguageIndex = -1
  private var pendingVoiceId: String? = null

  private lateinit var presetSpinner: Spinner
  private lateinit var textInput: EditText
  private lateinit var languageSpinner: Spinner
  private lateinit var voiceSpinner: Spinner
  private lateinit var speakButton: Button
  private lateinit var streamButton: Button
  private lateinit var stopButton: Button
  private lateinit var statusView: TextView
  private lateinit var logView: TextView
  private lateinit var logScroll: ScrollView
  private var statusDefaultColor: Int = 0
  private var lastPresetPosition = 0

  // Runtime options from the launch intent (not exposed in the UI).
  private var numThreads = 4
  private var targetBucket = 64
  private var playAudio = true
  private var streamMode = false

  // ---------------------------------------------------------------------------------------------
  // Lifecycle
  // ---------------------------------------------------------------------------------------------

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    buildUi()
    applyIntent(intent, initialLaunch = true)
    loadEngine()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    applyIntent(intent, initialLaunch = false)
    if (tts != null && shouldAutorun(intent)) speak(play = playAudio, streaming = streamMode)
  }

  override fun onDestroy() {
    stopRequested = true
    tts?.cancel()
    worker.execute {
      tts?.close()
      tts = null
    }
    worker.shutdown()
    super.onDestroy()
  }

  // ---------------------------------------------------------------------------------------------
  // UI construction (programmatic, no resources needed)
  // ---------------------------------------------------------------------------------------------

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  private fun label(text: String): TextView =
    TextView(this).apply {
      this.text = text
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      setTypeface(null, Typeface.BOLD)
      setPadding(0, dp(8), 0, dp(2))
    }

  @SuppressLint("SetTextI18n")
  private fun buildUi() {
    val pad = dp(12)
    val root =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad, pad, pad)
        // targetSdk 35 draws edge-to-edge: keep content clear of the status/navigation bars and
        // shrink above the soft keyboard.
        setOnApplyWindowInsetsListener { v, insets ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
          } else {
            @Suppress("DEPRECATION")
            v.setPadding(
              pad + insets.systemWindowInsetLeft,
              pad + insets.systemWindowInsetTop,
              pad + insets.systemWindowInsetRight,
              pad + insets.systemWindowInsetBottom,
            )
          }
          insets
        }
      }

    root.addView(
      TextView(this).apply {
        text = "MediaPipe TTS · Kokoro-82M · on-device"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTypeface(null, Typeface.BOLD)
      }
    )

    // 1. Sample sentences.
    root.addView(label("Sample text"))
    presetSpinner = Spinner(this)
    presetSpinner.adapter = spinnerAdapter(PRESETS.map { it.title })
    presetSpinner.setSelection(0, false)
    presetSpinner.onItemSelectedListener = onSelected { position ->
      if (position == lastPresetPosition) return@onSelected
      lastPresetPosition = position
      applyPreset(PRESETS[position])
    }
    root.addView(presetSpinner)

    // 2. Free-form text.
    root.addView(label("Text to speak (editable)"))
    textInput =
      EditText(this).apply {
        inputType =
          InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        minLines = 3
        maxLines = 6
        gravity = Gravity.TOP or Gravity.START
        hint = "Type anything…"
        setText(PRESETS[0].text)
      }
    root.addView(textInput)

    // 3. Language + voice.
    val pickers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val languageColumn =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label("Language"))
        languageSpinner = Spinner(this@MainActivity)
        addView(languageSpinner)
      }
    val voiceColumn =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label("Voice"))
        voiceSpinner = Spinner(this@MainActivity)
        addView(voiceSpinner)
      }
    pickers.addView(
      languageColumn,
      LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
    )
    pickers.addView(
      voiceColumn,
      LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f),
    )
    root.addView(pickers)
    languageSpinner.onItemSelectedListener = onSelected { position -> onLanguageSelected(position) }

    // 4. Actions.
    val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    speakButton =
      Button(this).apply {
        text = "Speak"
        isEnabled = false
        setOnClickListener { speak(play = true, streaming = false) }
      }
    streamButton =
      Button(this).apply {
        text = "Stream"
        isEnabled = false
        setOnClickListener { speak(play = true, streaming = true) }
      }
    stopButton =
      Button(this).apply {
        text = "Stop"
        isEnabled = false
        setOnClickListener { stop() }
      }
    buttons.addView(
      speakButton,
      LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f),
    )
    buttons.addView(
      streamButton,
      LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f),
    )
    buttons.addView(
      stopButton,
      LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
    )
    root.addView(buttons)

    // 5. Status + log.
    statusView =
      TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(6))
      }
    statusDefaultColor = statusView.currentTextColor
    root.addView(statusView)

    logView =
      TextView(this).apply {
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        setTextIsSelectable(true)
      }
    logScroll = ScrollView(this).apply { addView(logView) }
    root.addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

    setContentView(root)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      // Light background: use dark status-bar icons. Must run after setContentView (needs
      // DecorView).
      window.insetsController?.setSystemBarsAppearance(
        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
      )
    }
  }

  private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> =
    ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
      it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

  private fun onSelected(block: (Int) -> Unit): AdapterView.OnItemSelectedListener =
    object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        block(position)
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

  // ---------------------------------------------------------------------------------------------
  // Intent handling
  // ---------------------------------------------------------------------------------------------

  private fun shouldAutorun(intent: Intent): Boolean =
    intent.getBooleanExtra(
      EXTRA_AUTORUN,
      intent.hasExtra(EXTRA_TEXT) || intent.hasExtra(EXTRA_VOICE) || intent.hasExtra(EXTRA_STREAM),
    )

  private fun applyIntent(intent: Intent, initialLaunch: Boolean) {
    numThreads = intent.getIntExtra(EXTRA_NUM_THREADS, numThreads)
    targetBucket = intent.getIntExtra(EXTRA_TARGET_BUCKET, targetBucket)
    playAudio = intent.getBooleanExtra(EXTRA_PLAY_AUDIO, playAudio)
    streamMode = intent.getBooleanExtra(EXTRA_STREAM, streamMode)
    intent.getStringExtra(EXTRA_TEXT)?.let { textInput.setText(it) }
    intent.getStringExtra(EXTRA_VOICE)?.let { voiceId ->
      pendingVoiceId = voiceId
      if (!initialLaunch) selectVoice(voiceId)
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Engine lifecycle
  // ---------------------------------------------------------------------------------------------

  private fun findModelFile(): File? {
    val externalFile = File(getExternalFilesDir(null) ?: filesDir, MODEL_FILE_NAME)
    if (externalFile.isFile && externalFile.length() > 0) return externalFile

    val localTmpFile = File("/data/local/tmp", MODEL_FILE_NAME)
    if (localTmpFile.isFile && localTmpFile.length() > 0) return localTmpFile

    val internalFile = File(filesDir, MODEL_FILE_NAME)
    if (internalFile.isFile && internalFile.length() > 0) return internalFile

    try {
      assets.open(MODEL_FILE_NAME).use { input ->
        internalFile.outputStream().use { output ->
          input.copyTo(output)
        }
      }
      if (internalFile.isFile && internalFile.length() > 0) return internalFile
    } catch (_: Exception) {}

    return null
  }

  private fun loadEngine() {
    setStatus("Loading Kokoro engine…")
    log(
      "Device : ${Build.MANUFACTURER} ${Build.MODEL}, " +
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    )
    worker.execute {
      try {
        val modelFile = findModelFile()
        check(modelFile != null && modelFile.isFile && modelFile.length() > 0) {
          "Kokoro model not found. " +
            "Run scripts/run_android_demo.sh, or: " +
            "adb push ~/models/$MODEL_FILE_NAME ${(getExternalFilesDir(null) ?: filesDir).absolutePath}/$MODEL_FILE_NAME"
        }
        val options =
          TextToSpeechOptions(
            baseOptions =
              BaseOptions.builder()
                .setModelAssetPath(modelFile.absolutePath)
                .setDelegate(Delegate.GPU)
                .build(),
            defaultVoiceId = DEFAULT_VOICE,
            speakingRate = 1.0f,
            targetBucket = targetBucket,
            cacheDirectory = cacheDir.absolutePath,
          )
        val loadStart = SystemClock.elapsedRealtime()
        val engine = TextToSpeech.create(options)
        val loadMs = SystemClock.elapsedRealtime() - loadStart
        val loadedVoices = engine.listVoices()
        check(loadedVoices.isNotEmpty()) { "The model reported no voices." }
        log(
          "Engine created in $loadMs ms (threads=$numThreads, " +
            "target_bucket=$targetBucket); ${loadedVoices.size} voices"
        )

        runOnUiThread { setStatus("Warming up…") }
        val warmStart = SystemClock.elapsedRealtime()
        val unusedWarmup = engine.synthesize("Warm up.")
        log("Warm-up pass completed in ${SystemClock.elapsedRealtime() - warmStart} ms")

        tts = engine
        runOnUiThread {
          populateVoices(loadedVoices)
          setStatus("Ready — ${loadedVoices.size} voices in ${languageTags.size} languages")
          speakButton.isEnabled = true
          streamButton.isEnabled = true
          if (shouldAutorun(intent)) speak(play = playAudio, streaming = streamMode)
        }
      } catch (t: Throwable) {
        reportError("Failed to load the TTS engine", t)
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Language / voice / preset selection
  // ---------------------------------------------------------------------------------------------

  private fun populateVoices(loaded: List<VoiceInfo>) {
    voices = loaded.sortedBy { it.voiceId }
    languageTags =
      voices
        .map { it.locale }
        .distinct()
        .sortedWith(compareBy({ it != "en-US" }, { languageDisplayName(it) }))
    languageSpinner.adapter = spinnerAdapter(languageTags.map { languageDisplayName(it) })
    currentLanguageIndex = -1
    val wanted = pendingVoiceId
    pendingVoiceId = null
    if (wanted != null && voices.any { it.voiceId == wanted }) {
      selectVoice(wanted)
    } else {
      if (wanted != null) log("Requested voice '$wanted' is not in the model; using default.")
      selectLanguage(PRESETS[lastPresetPosition].languageTag)
    }
  }

  private fun languageDisplayName(tag: String): String {
    val name = Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH)
    return if (name.isBlank() || name == tag) tag else "$name [$tag]"
  }

  private fun voiceDisplayName(v: VoiceInfo): String {
    val stem =
      v.voiceId.substringAfter('_', v.voiceId).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    val gender =
      when (v.voiceId.getOrNull(1)) {
        'f' -> "female"
        'm' -> "male"
        else -> null
      }
    return if (gender != null) "$stem ($gender) · ${v.voiceId}" else "$stem · ${v.voiceId}"
  }

  private fun selectLanguage(tag: String) {
    val index = languageTags.indexOf(tag)
    if (index < 0) return
    if (languageSpinner.selectedItemPosition != index) languageSpinner.setSelection(index)
    // Spinner callbacks are delivered asynchronously; apply immediately so callers (and scripted
    // autorun) see a consistent voice list. The later callback is a no-op for the same index.
    onLanguageSelected(index)
  }

  private fun onLanguageSelected(position: Int) {
    if (position !in languageTags.indices) return
    if (position == currentLanguageIndex && pendingVoiceId == null) return
    currentLanguageIndex = position
    val tag = languageTags[position]
    voicesForLanguage = voices.filter { it.locale == tag }
    voiceSpinner.adapter = spinnerAdapter(voicesForLanguage.map { voiceDisplayName(it) })
    val wanted = pendingVoiceId ?: DEFAULT_VOICE
    val index = voicesForLanguage.indexOfFirst { it.voiceId == wanted }
    if (index >= 0) {
      pendingVoiceId = null
      voiceSpinner.setSelection(index)
    }
  }

  /** Selects [voiceId] in both spinners (switching language if necessary). */
  private fun selectVoice(voiceId: String) {
    val voice = voices.firstOrNull { it.voiceId == voiceId }
    if (voice == null) {
      if (voices.isEmpty()) {
        pendingVoiceId = voiceId // Engine not loaded yet; populateVoices() applies it later.
      } else {
        log("Requested voice '$voiceId' is not in the model; keeping the current selection.")
      }
      return
    }
    pendingVoiceId = voiceId
    selectLanguage(voice.locale)
  }

  private fun applyPreset(preset: Preset) {
    textInput.setText(preset.text)
    if (voices.isNotEmpty()) selectLanguage(preset.languageTag)
  }

  private fun selectedVoice(): VoiceInfo? =
    voicesForLanguage.getOrNull(voiceSpinner.selectedItemPosition)

  // ---------------------------------------------------------------------------------------------
  // Synthesis & playback
  // ---------------------------------------------------------------------------------------------

  /**
   * Synthesizes the current text with the selected voice. [play] controls speaker playback: the
   * Speak/Stream buttons always play; the scripted autorun honours the `play_audio` intent extra.
   * When [streaming] is true, clauses are synthesized and played incrementally via
   * [TextToSpeech.synthesizeStream] and an [AudioTrack] in [AudioTrack.MODE_STREAM].
   */
  private fun speak(play: Boolean = true, streaming: Boolean = false) {
    val engine = tts
    if (engine == null) {
      reportError("Engine is not ready yet", null)
      return
    }
    val text = textInput.text.toString().trim()
    if (text.isEmpty()) {
      reportError("Please enter some text to speak", null)
      return
    }
    val voice = selectedVoice()
    if (voice == null) {
      reportError("No voice selected", null)
      return
    }
    if (!busy.compareAndSet(false, true)) {
      setStatus("Already synthesizing — tap Stop first")
      return
    }
    hideKeyboard()
    stopRequested = false
    speakButton.isEnabled = false
    streamButton.isEnabled = false
    stopButton.isEnabled = true
    val modeLabel = if (streaming) "Streaming" else "Synthesizing"
    setStatus("$modeLabel with ${voice.voiceId} (${voice.locale})…")
    log("---")
    log("Text   : \"$text\"")
    log(
      "Voice  : ${voice.voiceId} (${voice.locale}) [mode=${if (streaming) "stream" else "unary"}]"
    )

    worker.execute {
      try {
        val synthStart = SystemClock.elapsedRealtime()
        if (streaming) {
          val player = TtsAudioPlayer()
          var ttfaMs = -1L
          var playbackWaitMs = 0L
          var chunks = 0
          var sampleRateHz = 24000
          var streamTrack: AudioTrack? = null
          val synthEnd: Long
          try {
            runBlocking {
              engine.synthesizeStream(text, SynthesisOptions(voiceId = voice.voiceId)).collect {
                chunk ->
                if (stopRequested) throw CancelledException()
                val now = SystemClock.elapsedRealtime()
                val elapsed = max(0L, now - synthStart - playbackWaitMs)
                if (ttfaMs < 0) ttfaMs = elapsed
                chunks += 1
                sampleRateHz = chunk.sampleRateHz
                player.enqueue(chunk)
                log(
                  "Chunk #$chunks: +${chunk.samples.size} samples @ ${chunk.sampleRateHz} Hz " +
                    "(t=${elapsed} ms)"
                )
                if (play && !stopRequested && chunk.samples.isNotEmpty()) {
                  val writeStart = SystemClock.elapsedRealtime()
                  val track =
                    streamTrack
                      ?: createStreamingTrack(chunk.sampleRateHz).also {
                        streamTrack = it
                        activeTrack = it
                        it.play()
                      }
                  runOnUiThread { setStatus("Streaming chunk #$chunks (TTFA ${ttfaMs} ms)…") }
                  val unused =
                    track.write(chunk.samples, 0, chunk.samples.size, AudioTrack.WRITE_BLOCKING)
                  playbackWaitMs += SystemClock.elapsedRealtime() - writeStart
                }
              }
            }
            synthEnd = SystemClock.elapsedRealtime()
            streamTrack?.let { track ->
              if (play && !stopRequested && player.totalSamples > 0) {
                val drainDeadline = SystemClock.elapsedRealtime() + 750L
                while (
                  !stopRequested &&
                    SystemClock.elapsedRealtime() < drainDeadline &&
                    track.playbackHeadPosition < player.totalSamples
                ) {
                  SystemClock.sleep(25)
                }
              }
            }
          } finally {
            activeTrack = null
            streamTrack?.let { track ->
              try {
                track.stop()
              } catch (_: IllegalStateException) {}
              track.release()
            }
          }
          val synthMs = max(0L, synthEnd - synthStart - playbackWaitMs)
          if (stopRequested) throw CancelledException()
          val totalSamples = player.totalSamples
          check(totalSamples > 0) { "The engine returned no audio samples." }

          val durationSeconds = totalSamples.toFloat() / sampleRateHz.toFloat()
          val audioMs = (durationSeconds * 1000f).toLong()
          val rtf = if (audioMs > 0) synthMs.toDouble() / audioMs.toDouble() else Double.NaN
          val summary =
            "${"%.2f".format(Locale.US, durationSeconds)} s audio in $synthMs ms " +
              "($chunks chunks, TTFA $ttfaMs ms) · RTF ${"%.2f".format(Locale.US, rtf)}x"
          log("Result : $totalSamples samples @ $sampleRateHz Hz; $summary")

          val wavFile = File(getExternalFilesDir(null) ?: filesDir, OUTPUT_WAV_NAME)
          player.writeWavFile(wavFile)
          log("WAV    : ${wavFile.absolutePath} (${wavFile.length()} bytes)")

          val playState =
            when {
              !play -> "skipped"
              stopRequested -> "stopped"
              else -> "ok"
            }
          log(
            "$RESULT_MARKER status=OK mode=stream ttfa_ms=$ttfaMs chunks=$chunks " +
              "synth_ms=$synthMs audio_ms=$audioMs rtf=${"%.4f".format(Locale.US, rtf)} " +
              "samples=$totalSamples sample_rate_hz=$sampleRateHz voice=${voice.voiceId} " +
              "locale=${voice.locale} play=$playState wav=${wavFile.absolutePath}"
          )
          runOnUiThread {
            setStatus(if (stopRequested) "Stopped — $summary" else "Done — $summary")
          }
        } else {
          val audio = engine.synthesize(text, SynthesisOptions(voiceId = voice.voiceId))
          val synthMs = SystemClock.elapsedRealtime() - synthStart
          if (stopRequested) throw CancelledException()
          check(audio.samples.isNotEmpty()) { "The engine returned no audio samples." }

          val audioMs = (audio.durationSeconds * 1000f).toLong()
          val rtf = if (audioMs > 0) synthMs.toDouble() / audioMs.toDouble() else Double.NaN
          val summary =
            "${"%.2f".format(Locale.US, audio.durationSeconds)} s audio in $synthMs ms · " +
              "RTF ${"%.2f".format(Locale.US, rtf)}x"
          log("Result : ${audio.samples.size} samples @ ${audio.sampleRateHz} Hz; $summary")
          runOnUiThread { setStatus(if (play) "Playing — $summary" else summary) }

          val wavFile = File(getExternalFilesDir(null) ?: filesDir, OUTPUT_WAV_NAME)
          TtsAudioPlayer().apply { enqueue(audio) }.writeWavFile(wavFile)
          log("WAV    : ${wavFile.absolutePath} (${wavFile.length()} bytes)")

          val playState =
            when {
              !play -> "skipped"
              stopRequested -> "stopped"
              else -> {
                playBlocking(audio.samples, audio.sampleRateHz)
                if (stopRequested) "stopped" else "ok"
              }
            }
          // The scripted runner waits for this marker; emit it only once the whole run (including
          // playback) has succeeded so an AudioTrack failure is reported as an error, not success.
          log(
            "$RESULT_MARKER status=OK synth_ms=$synthMs audio_ms=$audioMs " +
              "rtf=${"%.4f".format(Locale.US, rtf)} samples=${audio.samples.size} " +
              "sample_rate_hz=${audio.sampleRateHz} voice=${voice.voiceId} " +
              "locale=${voice.locale} play=$playState wav=${wavFile.absolutePath}"
          )
          runOnUiThread {
            setStatus(if (stopRequested) "Stopped — $summary" else "Done — $summary")
          }
        }
      } catch (t: CancelledException) {
        log("Cancelled.")
        runOnUiThread { setStatus("Stopped.") }
      } catch (t: CancellationException) {
        // Thrown by the JNI layer when tts.cancel() aborts an in-flight synthesize().
        log("Cancelled.")
        runOnUiThread { setStatus("Stopped.") }
      } catch (t: Throwable) {
        reportError("Synthesis failed", t)
      } finally {
        busy.set(false)
        runOnUiThread {
          speakButton.isEnabled = true
          streamButton.isEnabled = true
          stopButton.isEnabled = false
        }
      }
    }
  }

  private fun stop() {
    stopRequested = true
    tts?.cancel()
    activeTrack?.let { track ->
      try {
        track.pause()
        track.flush()
      } catch (_: IllegalStateException) {}
    }
    setStatus("Stopping…")
  }

  /** Creates a streaming [AudioTrack] for incremental chunk playback. */
  private fun createStreamingTrack(sampleRateHz: Int): AudioTrack {
    val minBufferBytes =
      AudioTrack.getMinBufferSize(
        sampleRateHz,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
      )
    val bufferBytes = max(minBufferBytes, sampleRateHz * Float.SIZE_BYTES / 2)
    return AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build()
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
          .setSampleRate(sampleRateHz)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build()
      )
      .setBufferSizeInBytes(bufferBytes)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .build()
  }

  /** Plays float PCM through a static [AudioTrack] and blocks until playback finishes or [stop]. */
  private fun playBlocking(samples: FloatArray, sampleRateHz: Int) {
    val track =
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRateHz)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    activeTrack = track
    try {
      val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
      check(written == samples.size) {
        "AudioTrack.write wrote $written of ${samples.size} samples"
      }
      track.play()
      val deadline = SystemClock.elapsedRealtime() + samples.size * 1000L / sampleRateHz + 100L
      while (!stopRequested && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
    } finally {
      activeTrack = null
      try {
        track.stop()
      } catch (_: IllegalStateException) {}
      track.release()
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Status / logging / errors
  // ---------------------------------------------------------------------------------------------

  private fun setStatus(text: String, isError: Boolean = false) {
    runOnUiThread {
      statusView.text = text
      statusView.setTextColor(if (isError) ERROR_COLOR else statusDefaultColor)
    }
  }

  private fun log(line: String) {
    Log.i(TAG, line)
    runOnUiThread {
      logView.append(line + "\n")
      logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
  }

  /** Shows [context] (plus the exception message) in red, in a dialog, and in logcat. */
  private fun reportError(context: String, t: Throwable?) {
    val detail = t?.let { ": ${it.message ?: it.javaClass.simpleName}" } ?: ""
    val message = context + detail
    if (t != null) Log.e(TAG, message, t)
    log("$RESULT_MARKER status=ERROR message=$message")
    setStatus("Error: $message", isError = true)
    runOnUiThread {
      if (!isFinishing && !isDestroyed) {
        AlertDialog.Builder(this)
          .setTitle("MediaPipe TTS error")
          .setMessage(message)
          .setPositiveButton(android.R.string.ok, null)
          .show()
      }
    }
  }

  private fun hideKeyboard() {
    (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
      ?.hideSoftInputFromWindow(textInput.windowToken, 0)
  }

  private class CancelledException : RuntimeException("Synthesis cancelled.")

  private data class Preset(val title: String, val languageTag: String, val text: String)

  companion object {
    const val TAG = "MediaPipeTtsDemo"
    const val RESULT_MARKER = "MEDIAPIPE_TTS_DEMO_RESULT"

    const val EXTRA_TEXT = "text"
    const val EXTRA_VOICE = "voice"
    const val EXTRA_NUM_THREADS = "num_threads"
    const val EXTRA_TARGET_BUCKET = "target_bucket"
    const val EXTRA_PLAY_AUDIO = "play_audio"
    const val EXTRA_STREAM = "stream"
    const val EXTRA_AUTORUN = "autorun"

    const val MODEL_FILE_NAME = "kokoro.litertlm"
    const val OUTPUT_WAV_NAME = "kokoro_android_demo.wav"
    const val DEFAULT_VOICE = "af_heart"
    private const val ERROR_COLOR = 0xFFB00020.toInt()

    private val PRESETS =
      listOf(
        Preset(
          "1 · Greeting",
          "en-US",
          "Hello from Kokoro 82M running in-process on Android via the MediaPipe TTS Kotlin SDK!",
        ),
        Preset(
          "2 · Numbers & dates",
          "en-US",
          "Your order of 3 items totals \$42.50 and will arrive on Friday, March 14th, " +
            "between 9 and 11 a.m.",
        ),
        Preset(
          "3 · Pangram",
          "en-US",
          "The quick brown fox jumps over the lazy dog, while the five boxing wizards " +
            "jump quickly.",
        ),
        Preset(
          "4 · Questions & emphasis",
          "en-US",
          "Did you know this voice is generated entirely on your phone? No internet, " +
            "no cloud — just on-device AI!",
        ),
        Preset(
          "5 · Longer paragraph",
          "en-US",
          "Text-to-speech converts written words into natural-sounding audio. Kokoro is a " +
            "compact 82-million-parameter model that runs locally through LiteRT-LM, so your " +
            "text never leaves the device and synthesis works even in airplane mode.",
        ),
      )
  }
}
