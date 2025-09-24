import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.android.tools.build:gradle:8.12.3")
}

// Add this block to register the plugin
gradlePlugin {
    plugins {
        register("mediapipe-npu") {
            id = "com.google.mediapipe.npu"
            implementationClass = "com.google.mediapipe.tasks.npu.NpuProvider"
        }
    }
}