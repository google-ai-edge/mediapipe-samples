package com.google.mediapipe.tasks.npu

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import com.android.build.api.dsl.ApplicationExtension

class NpuProvider : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<CodeGenExtension>("buildInfo", CodeGenExtension::class.java )

        val generateCodeTask = project.tasks.register<GenerateCodeTask>("generateBuildInfo") {
            packageName.set(extension.packageName)
            className.set(extension.className)
            projectVersion.set(project.version.toString())
            outputDir.set(project.layout.buildDirectory.dir("generated/source/buildInfo/kotlin"))
        }

        project.afterEvaluate {
            project.extensions.getByType(com.android.build.api.dsl.ApplicationExtension::class.java).sourceSets.getByName("main") {
                kotlin.srcDir(generateCodeTask.get().outputDir)
            }
            generateCodeTask.get().generate()
        }
    }
}