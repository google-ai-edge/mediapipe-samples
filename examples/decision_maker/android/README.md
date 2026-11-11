# MediaPipe Decision Maker — Android Sample

This sample app uses the MediaPipe Tasks Decision library (`com.google.mediapipe:tasks-decision`) with Compose Material 3 (`:android-common:ui`) to evaluate text against Boolean, Choice, and Score questions entirely on device.

## UI

* **Segmented question tabs:**
  * **Boolean:** Yes/No condition with an interactive decision threshold slider (`BooleanQuestion`).
  * **Choice:** Multi-option classification with optional per-option descriptions (`ChoiceQuestion.withDescriptions`).
  * **Score:** Ordered rubric evaluation computing expected score and level probabilities (`ScoreQuestion`).
* **Preserved per-tab drafts:** Switching between Boolean, Choice, and Score preserves your edits.
* **Settings sheet:** Switch between **CPU / GPU** (with automatic CPU fallback when GPU is unavailable) and **ML Model** (`EmbeddingGemma 270M`, `Laya S256`, or sideloaded `GLiNER S256`).

![Decision Maker Demo](decision_maker.png?raw=true "Decision Maker Demo")

## Models

Model downloads are verified via SHA-256 in `app/download_models.gradle.kts`:

| Model | Status | Source |
|---|---|---|
| EmbeddingGemma 2 270M (`embeddinggemma-2-text-270m.litertlm`) | **Default.** Bundled (~165 MB) via `downloadEmbeddingGemmaModel`. | https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm |
| Laya S256 (`laya_s256.task`) | Bundled (~678 MB) via `downloadLayaModel`. | https://storage.googleapis.com/mediapipe-models/decision_maker/laya/float32/laya_s256/latest/laya_s256.task |
| GLiNER S256 (`gliner_s256.task`) | Optional (~980 MB). Push to device storage (see below). | https://storage.googleapis.com/mediapipe-models/decision_maker/gliner/float16/gliner_s256/latest/gliner_s256.task |

To push GLiNER S256 without rebuilding:

```bash
adb push gliner_s256.task \
  /sdcard/Android/data/com.google.mediapipe.examples.decisionmaker/files/
```

## Build and Test

```bash
cd examples/decision_maker/android
./gradlew :app:verifyModels :android-common:testing:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```
