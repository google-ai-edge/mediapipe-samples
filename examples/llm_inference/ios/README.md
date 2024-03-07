Setup Instructions
=====

1. Clone the mediapipe repository at https://github.com/google/mediapipe.
1. Build the `MediaPipeTasksGenAI` and `MediaPipeTasksGenAIC` libraries by
   `cd`ing into the root of the mediapipe repository and running:
   ```
   FRAMEWORK_NAME=MediaPipeTasksGenAIC MPP_BUILD_VERSION=0.10.11 mediapipe/tasks/ios/build_ios_framework.sh && FRAMEWORK_NAME=MediaPipeTasksGenAI MPP_BUILD_VERSION=0.10.11 mediapipe/tasks/ios/build_ios_framework.sh
   ```
1. Unzip the resulting files in your home directory and copy them into this
   directory alongside the README.
1. Download the `model_cpu.tflite` model from (link TBD) into this directory.
1. Open and run the project.