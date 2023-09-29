# MediaPipe Image Generation

This app is a demonstration and sample of using MediaPipe to generate new images based on a text input.

There are three primary ways that you can use this new demo and MediaPipe Task:

1.  Standard diffusion to generate new images based on a text prompt.

![Diffusion example](images/diffusion.gif?raw=true "Diffusion example")

    
2.  Diffusion with a plugin that works with other existing tasks and models to provide structure for your new generations.

![Plugin example](images/plugin.gif?raw=true "Plugin example")
    
3.  Diffusion with Low-Rank Adaptation (LoRA) weights that allow you to create images of specific concepts that you pre-define for your unique use-cases.

![LoRA example](images/lora.gif?raw=true "LoRA example")

## Build the demo using Android Studio

To perform image generation, you will need to download or build an image model that uses the Stable Diffusion v1.5 architecture. You can find a list of open models on the [official documentation page](https://developers.google.com/mediapipe/solutions/vision/image_generator#install_and_run_the_image_generator_demo_app).

After you have your model downloaded, you can run the conversion script listed in the official documentation to prepare it for use with this sample application. You will also need to copy this converted model to your Android device.

Optionally, you can create a new set of weights to use with the LoRA option, adding a new and desired bias to your image generations. These weights will need to be stored on your Android device, and you can find a link to an official set of LoRA weights in the Task's documentation.

### Building

When your models/weights are ready, copy them to your development device. For this example the files are loaded into the `/data/local/tmp/image_generator/bins` directory.

To use the face, edge, or depth plugins, you will need additional models stored in the app's `assets` directory. These will be automatically downloaded and installed with your APK through the `download_models.gradle` build script located in this project.

An example weights file can be found [here](https://storage.googleapis.com/mediapipe-models/image_generator/LoRA_weights/teapot_lora.task) for the key term 'monadikos teapot'.