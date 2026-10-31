# How to Build the APK

This project uses Gradle to build the application. You can build the APK from the command line using the Gradle wrapper (`gradlew`).

## Prerequisites

- Java Development Kit (JDK) version 8 or higher installed.
- Android SDK installed and the `ANDROID_HOME` environment variable set.

## Build Steps

1.  **Open a terminal or command prompt.**

2.  **Navigate to the project directory:**
    ```bash
    cd AdminPanelApp
    ```

3.  **On macOS or Linux, make the Gradle wrapper executable:**
    Before you can run the Gradle wrapper, you need to make it executable.
    ```bash
    chmod +x ./gradlew
    ```
    (This step is not needed on Windows.)

4.  **Run the build command:**
    Now, you can start the build process. This command will download the required Gradle version and build the debug APK.

    -   On **macOS or Linux**:
        ```bash
        ./gradlew assembleDebug
        ```
    -   On **Windows**:
        ```bash
        gradlew.bat assembleDebug
        ```

5.  **Find the APK:**
    After the build finishes successfully, the APK file will be located in the following directory:
    `app/build/outputs/apk/debug/app-debug.apk`

    You can now install this APK file on an Android device or emulator.