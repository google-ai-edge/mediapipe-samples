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

import groovy.json.JsonSlurper

// One app that contains every Android sample of the validation spec. This file decides which
// samples are part of the build and with which versions; build.gradle.kts turns each of them
// into a library module that compiles the sources of the sample where they are.
//
//   -Ptasks=object_detector,text_classifier   only these tasks of the spec (default: all)
//   -PmediapipeVersion=1.3.0                  this MediaPipe version instead of the samples'
//   -PmediapipeRepo=/path/or/url              a Maven repository with that version

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

val mediapipeRepo = providers.gradleProperty("mediapipeRepo").orNull
val mediapipeVersion = providers.gradleProperty("mediapipeVersion").orNull

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (mediapipeRepo != null) {
            maven { url = uri(if ("://" in mediapipeRepo) mediapipeRepo else file(mediapipeRepo)) }
        }
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "MediaPipe Validation"

val repoRoot: File = settingsDir.parentFile
val specDir = File(repoRoot, ".github/scripts/release_validate/spec")

@Suppress("UNCHECKED_CAST")
fun json(file: File) = JsonSlurper().parse(file) as Map<String, Any?>

// ---------------------------------------------------------------------------------------------
// The samples: every task of the spec that names an Android sample.
// ---------------------------------------------------------------------------------------------

val requested =
    providers.gradleProperty("tasks").orNull?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

@Suppress("UNCHECKED_CAST")
val specTasks = (json(File(specDir, "index.json"))["tasks"] as List<String>).map { json(File(specDir, "$it.json")) }

val unknown = requested.orEmpty() - specTasks.map { it["task"] as String }.toSet()
if (unknown.isNotEmpty()) {
    throw GradleException("-Ptasks names tasks the spec does not have: $unknown")
}

val samples = mutableListOf<Map<String, String>>()
for (task in specTasks) {
    val name = task["task"] as String
    if (requested != null && name !in requested) continue
    @Suppress("UNCHECKED_CAST")
    val path = (task["samples"] as Map<String, String>)["android"] ?: continue
    val dir = File(repoRoot, path)
    val buildFile = File(dir, "app/build.gradle.kts")
    if (!buildFile.isFile) {
        throw GradleException("The spec names $path as the Android sample of $name, but $buildFile does not exist.")
    }
    val namespace =
        Regex("""namespace\s*=\s*"([^"]+)"""").find(buildFile.readText())?.groupValues?.get(1)
            ?: throw GradleException("No namespace in $buildFile")
    samples +=
        mapOf(
            "task" to name,
            "domain" to task["domain"] as String,
            "heading" to task["heading"] as String,
            "path" to path,
            "dir" to dir.absolutePath,
            "namespace" to namespace,
        )
}
gradle.extra["validationSamples"] = samples

include(":app")
include(":runner")
include(":android-common:ui")
project(":android-common:ui").projectDir = File(repoRoot, "android-common/ui")
for (sample in samples) {
    val projectPath = ":samples:${sample.getValue("task")}"
    include(projectPath)
    project(projectPath).projectDir = File(settingsDir, "samples/${sample.getValue("task")}").apply { mkdirs() }
}

// ---------------------------------------------------------------------------------------------
// The versions: the union of the version catalogs of the samples, so that this app is built
// with exactly the versions the samples are built with. Samples that disagree cannot be built
// into one app, and the build says so.
// ---------------------------------------------------------------------------------------------

fun mergedCatalog(): File {
    val sections = linkedMapOf<String, MutableMap<String, String>>()
    val origin = mutableMapOf<String, String>()
    val catalogs = samples.map { File(it.getValue("dir"), "gradle/libs.versions.toml") } +
        File(repoRoot, "android-common/gradle/libs.versions.toml")
    for (catalog in catalogs) {
        var section = ""
        for (raw in catalog.readLines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[")) {
                section = line
                continue
            }
            val key = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim()
            val known = sections.getOrPut(section) { sortedMapOf() }
            val previous = known.put(key, value)
            if (previous != null && previous != value) {
                throw GradleException(
                    "The samples disagree on $section $key: $previous (${origin["$section/$key"]}) " +
                        "and $value ($catalog). They cannot be built into one app."
                )
            }
            origin["$section/$key"] = catalog.path
        }
    }
    val text = buildString {
        appendLine("# Generated by settings.gradle.kts from the version catalogs of the samples. Do not edit.")
        for ((section, entries) in sections) {
            appendLine(section)
            for ((key, value) in entries) appendLine("$key = $value")
            appendLine()
        }
    }
    val out = File(settingsDir, "samples/libs.versions.toml")
    out.parentFile.mkdirs()
    if (!out.exists() || out.readText() != text) out.writeText(text)
    return out
}

val catalogFile = mergedCatalog()
val mediapipeVersionKeys =
    catalogFile.readLines().map { it.substringBefore("=").trim() }.filter { it.startsWith("mediapipeTasks") }

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files(catalogFile))
            if (mediapipeVersion != null) {
                for (key in mediapipeVersionKeys) version(key, mediapipeVersion)
            }
        }
    }
}
