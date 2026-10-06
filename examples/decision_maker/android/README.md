# MediaPipe Decision Maker — Android Sample

This sample app uses the MediaPipe Tasks Decision library
(`com.google.mediapipe:tasks-decision:1.1.0`). It evaluates a piece of text
against a question, entirely on device.

## UI

The app is a single screen:

- **Tabs** pick the question type:
  - **Boolean**: a yes/no *Condition* and a *Threshold* slider (`BooleanQuestion`).
    The answer is Yes when the Yes probability is at least the threshold;
    releasing the slider re-runs the question.
  - **Choice**: a list of options, each with a key and an optional description
    (`ChoiceQuestion.withDescriptions`).
  - **Score**: a list of rubric levels, lowest (1) first, each with a name and
    an optional description (`ScoreQuestion`). Each level is sent as
    `name: description`.
- **Options / rubric editor** (Choice and Score): every item is a card. Use
  **Add option** / **Add level** to add one and **✕** to remove it. At least
  two named items are required. Choice and Score also take optional
  *Instructions*.
- Each tab keeps its own edits when you switch tabs.
- **Evaluate** runs the decision. The result card shows the answer (or the
  expected score for Score) and a probability bar per option or level.
- **Settings sheet**: tap or drag the *Settings* header to expand it. It shows
  the last inference time, a **CPU / GPU** delegate toggle and the **ML Model**
  menu. If the GPU delegate fails, the app falls back to CPU automatically.

## Models

| Model | Status | Source |
|---|---|---|
| EmbeddingGemma 2 270M (`embeddinggemma-2-text-270m.litertlm`) | **Default.** Bundled (~165 MB). The app downloads it into `app/src/main/assets` before each build (`downloadEmbeddingGemmaModel`). | https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm |
| Laya S256 (`laya_s256.task`) | Bundled. The app downloads it into `app/src/main/assets` before each build (`downloadLayaModel`). | https://storage.googleapis.com/mediapipe-models/decision_maker/laya/float32/laya_s256/latest/laya_s256.task |
| GLiNER S256 (`gliner_s256.task`) | Not bundled (~980 MB). Push it to the device (see below), or uncomment `downloadGlinerModel` in `app/download_models.gradle` to bundle it. | https://storage.googleapis.com/mediapipe-models/decision_maker/gliner/float16/gliner_s256/latest/gliner_s256.task |

When the app loads a model, it searches these locations in order:

1. `/data/local/tmp/decision_models/`
2. The app's external files directory
3. The bundled assets

To push a model file without rebuilding:

```bash
adb push gliner_s256.task \
  /sdcard/Android/data/com.google.mediapipe.examples.decisionmaker/files/
```

## Build

Requires JDK 17 or newer (for example, the JDK bundled with Android Studio).
Set `JAVA_HOME` and `ANDROID_HOME`, or build from Android Studio.

```bash
./gradlew installDebug
```

> **Note:** The Tasks Decision AAR is resolved from `mavenLocal()`.
