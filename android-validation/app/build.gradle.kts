/*
 * Copyright 2026 The MediaPipe Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

@Suppress("UNCHECKED_CAST")
val sampleModules = rootProject.extra["validationModules"] as List<Map<String, String>>
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

// What the app tells its user and the runner about itself: the samples it was built with, and
// the versions it was built against.
val generatedAssets = layout.buildDirectory.dir("generated/validation/assets").get().asFile
val mergedSampleAssets = layout.buildDirectory.dir("generated/validation/sample-assets").get().asFile
val versions =
    catalog.versionAliases.filter { it.startsWith("mediapipe") || it.startsWith("litertlm") }.sorted().associateWith {
        catalog.findVersion(it).get().requiredVersion
    }
File(generatedAssets, "validation/samples.json").apply {
    val text = JsonOutput.prettyPrint(JsonOutput.toJson(mapOf("samples" to sampleModules, "versions" to versions)))
    parentFile.mkdirs()
    if (!exists() || readText() != text) writeText(text)
}

android {
    namespace = "com.google.mediapipe.examples.validation"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.google.mediapipe.examples.validation"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        // One APK of the debug build holds the native libraries of every ABI by default, which
        // is four copies of every MediaPipe library. -Pabis=arm64-v8a,x86_64 adds the emulator.
        ndk {
            abiFilters += (providers.gradleProperty("abis").orNull ?: "arm64-v8a").split(",").map { it.trim() }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // The union of what the samples set.
    androidResources {
        noCompress += listOf("tflite", "task", "litertlm", "wav")
    }
    packaging {
        jniLibs {
            pickFirsts += listOf("**/libicing.so", "**/libfilterframework_jni.so")
        }
    }
    sourceSets.getByName("main") {
        assets.srcDir(generatedAssets)
        assets.srcDir(mergedSampleAssets)
    }
}

dependencies {
    implementation(project(":runner"))
    implementation(project(":android-common:ui"))
    for (module in sampleModules) implementation(project(":samples:${module.getValue("task")}"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

// The cases the app runs are part of the app, with their input files: the app needs nothing
// pushed to the device, and no network. spec.py resolves the spec into one flat file for the
// tasks of this build (-Ppython=... names another interpreter; it needs no packages).
val releaseValidateDir = File(rootDir.parentFile, ".github/scripts/release_validate")
val casesFile = File(generatedAssets, "validation/cases.json")
val exportValidationCases by
    tasks.registering(Exec::class) {
        val taskFilter = sampleModules.joinToString(",") { it.getValue("task") }
        inputs.dir(File(releaseValidateDir, "spec"))
        inputs.file(File(releaseValidateDir, "spec.py"))
        inputs.property("tasks", taskFilter)
        outputs.file(casesFile)
        commandLine(
            providers.gradleProperty("python").orNull ?: "python3",
            File(releaseValidateDir, "spec.py").path,
            "export", "--platform", "android", "--tasks", taskFilter, "--out", casesFile.path,
        )
    }

fun downloadPinnedAsset(name: String, entry: Map<String, Any?>, dest: File) {
    if (dest.isFile && sha256(dest) == entry["sha256"]) return
    val url = entry["url"] ?: "https://huggingface.co/${entry["repo"]}/resolve/${entry["revision"]}/${entry["file"]}"
    logger.lifecycle("Downloading $url")
    dest.parentFile?.mkdirs()
    val tmp = File(dest.parentFile, "$name.tmp")
    URI(url.toString()).toURL().openStream().use { Files.copy(it, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    val actual = sha256(tmp)
    if (actual != entry["sha256"]) {
        tmp.delete()
        throw GradleException("$url has SHA-256 $actual, but the spec pins ${entry["sha256"]}.")
    }
    Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

// The pinned input files of those cases, downloaded and checked like the models of the samples.
val downloadValidationInputs by
    tasks.registering {
        dependsOn(exportValidationCases)
        val inputsDir = File(generatedAssets, "validation/inputs")
        inputs.file(casesFile)
        outputs.dir(inputsDir)
        doLast {
            @Suppress("UNCHECKED_CAST")
            val assets = (JsonSlurper().parse(casesFile) as Map<String, Any?>)["assets"] as Map<String, Map<String, Any?>>
            val wanted = assets.filterValues { it["kind"] == "input" }
            inputsDir.mkdirs()
            inputsDir.listFiles().orEmpty().filter { it.name !in wanted }.forEach { it.delete() }
            for ((name, entry) in wanted) {
                downloadPinnedAsset(name, entry, File(inputsDir, name))
            }
        }
    }

// The assets of all samples end up in one directory of the app via symlinks so shared files
// (such as the multimodal EmbeddingGemma model and gallery photos shared by Universal Embedder
// and Semantic Retriever) are packaged once without duplicating gigabytes on disk, while two
// samples that bundle different contents under the same asset name fail the build.
val prepareSampleAssets by
    tasks.registering {
        val assetDirs = sampleModules.associate { it.getValue("task") to File(rootDir.parentFile, "${it.getValue("path")}/app/src/main/assets") }
        dependsOn(exportValidationCases)
        for (module in sampleModules) dependsOn(":samples:${module.getValue("task")}:preBuild")
        doLast {
            val owners = linkedMapOf<String, MutableList<Pair<String, File>>>()
            for ((task, dir) in assetDirs) {
                dir.walkTopDown().filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".tmp") }.forEach { file ->
                    owners.getOrPut(file.relativeTo(dir).path.replace(File.separatorChar, '/')) { mutableListOf() } += task to file
                }
            }
            val clashes =
                owners.filterValues { files -> files.size > 1 && files.map { sha256(it.second) }.toSet().size > 1 }
            if (clashes.isNotEmpty()) {
                throw GradleException(
                    "Samples bundle different files under the same asset name, which one app cannot hold: " +
                        clashes.map { (name, files) -> "$name (${files.joinToString { it.first }})" }.joinToString("; ")
                )
            }

            // Auxiliary verifier models referenced by cases (such as `listenWith` for TTS speech
            // round-trip verification) when building a subset of tasks that omits their primary sample.
            @Suppress("UNCHECKED_CAST")
            val parsed = JsonSlurper().parse(casesFile) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val exportedCases = parsed["cases"] as List<Map<String, Any?>>
            @Suppress("UNCHECKED_CAST")
            val exportedAssets = parsed["assets"] as Map<String, Map<String, Any?>>
            for (listenWith in exportedCases.mapNotNull { it["listenWith"] as? String }.toSet()) {
                if (listenWith in owners) continue
                val entry = exportedAssets.getValue(listenWith)
                val candidates = listOf(
                    File(rootDir.parentFile, "examples/audio_transcriber/android/app/src/main/assets/$listenWith"),
                    File(rootDir.parentFile, ".release_validation/assets/$listenWith"),
                )
                val existing = candidates.firstOrNull { it.isFile && sha256(it) == entry["sha256"] }
                val resolved = existing ?: File(layout.buildDirectory.dir("generated/validation/aux-models").get().asFile, listenWith).also {
                    downloadPinnedAsset(listenWith, entry, it)
                }
                owners[listenWith] = mutableListOf("listenWith" to resolved)
            }

            if (mergedSampleAssets.exists()) mergedSampleAssets.deleteRecursively()
            mergedSampleAssets.mkdirs()
            for ((relPath, files) in owners) {
                val link = File(mergedSampleAssets, relPath)
                link.parentFile?.mkdirs()
                Files.createSymbolicLink(link.toPath(), files.first().second.canonicalFile.toPath())
            }
        }
    }
tasks.named("preBuild") { dependsOn(prepareSampleAssets, downloadValidationInputs) }
