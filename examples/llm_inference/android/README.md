# MediaPipe LLM Inference Android Demo

### Overview

This is a sample app that demonstrates how to use the LLM Inference API to run common text-to-text generation tasks like information retrieval, email drafting, and document summarization.

This application must be run on a physical Android device to take advantage of the device GPU.

![LLM Inference Demo](llm_inference.png)

## Build the demo using Android Studio

### Download the code

To download the demo code, clone the git repository using the following command:

```
git clone https://github.com/google-ai-edge/mediapipe-samples
```

After downloading the demo code, you can import the project into Android Studio and run the app with the following instructions.

### Prerequisites

*   The **[Android Studio](https://developer.android.com/studio)**
    IDE. This demo has been tested on Android Studio Hedgehog.

*   A physical Android device with a minimum OS version of SDK 24 (Android 7.0 -
    Nougat) with developer mode enabled.

### Build and run

To import and build the demo app:

1. Start [Android Studio](https://developer.android.com/studio).

1. From the Android Studio, select **File > New > Import Project**.

1. Navigate to the demo app `android` directory and select that directory, for example: `.../mediapipe-samples/examples/llm_inference/android`

1. If Android Studio requests a Gradle Sync, choose **OK**.

1. Build the project by selecting **Build > Make Project**.

   When the build completes, the Android Studio displays a `BUILD SUCCESSFUL` message in the Build Output status panel.

To run the demo app:

1. Ensure that your Android device is connected to your computer and developer mode is enabled.

1. From Android Studio, run the app by selecting **Run > Run 'app'**.

### Models used

You can download compatible models from [LiteRT on Hugging Face](https://huggingface.co/litert-community), e.g. [deepseek_q8_ekv1280.task](https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/deepseek_q8_ekv1280.task?download=true) for DeepSeek model.

Once you download it, place it under the path defined as `Model.path` in [Model.kt](app/src/main/java/com/google/mediapipe/examples/llminference/Model.kt) on the Android device
 (eg. /data/local/tmp/llm/model.bin).

You could use either Android Studio's [Device Explorer](https://developer.android.com/studio/debug/device-file-explorer) or `adb` to push the model to the Android device like below:

```
$ adb shell mkdir -p /data/local/tmp/llm/
$ adb push YOUR_MODEL_PATH /data/local/tmp/llm/YOUR_MODEL_VERSION.bin
```

For more details, see the [models section](https://developers.google.com/mediapipe/solutions/genai/llm_inference/android#model) in the LLM Inference guide for Android.
