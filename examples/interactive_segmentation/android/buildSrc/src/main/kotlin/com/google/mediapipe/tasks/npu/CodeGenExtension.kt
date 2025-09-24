package com.google.mediapipe.tasks.npu
import org.gradle.api.provider.Property

// Defines the configurable block for our plugin
abstract class CodeGenExtension {
    // The package name for the generated file
    abstract val packageName: Property<String>

    // The class name for the generated file
    abstract val className: Property<String>
}