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

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Optional canary / release-verification override:
//   ./gradlew assembleDebug -PmediapipeVersion=1.3.0
//   ./gradlew connectedDebugAndroidTest -PmediapipeVersion=1.3.0-rc1 -PrequireLocalMediapipeRepo=true
val overrideMediapipeVersion = providers.gradleProperty("mediapipeVersion").orNull
val requireLocalMediapipeRepo =
    providers.gradleProperty("requireLocalMediapipeRepo").map { it.toBoolean() }.getOrElse(false)

if (overrideMediapipeVersion != null || requireLocalMediapipeRepo) {
    val localM2Root =
        file(System.getProperty("user.home")).resolve(".m2/repository").toPath().toAbsolutePath().normalize()

    subprojects {
        configurations.configureEach {
            if (overrideMediapipeVersion != null) {
                resolutionStrategy.eachDependency {
                    if (requested.group == "com.google.mediapipe" && requested.name.startsWith("tasks-")) {
                        useVersion(overrideMediapipeVersion)
                        because("Overridden via -PmediapipeVersion=$overrideMediapipeVersion")
                    }
                }
            }
            if (requireLocalMediapipeRepo && isCanBeResolved) {
                incoming.afterResolve {
                    val violations =
                        resolutionResult.allComponents
                            .mapNotNull { component ->
                                val mod = component.moduleVersion ?: return@mapNotNull null
                                if (mod.group != "com.google.mediapipe") return@mapNotNull null
                                val pomPath =
                                    localM2Root
                                        .resolve(
                                            "com/google/mediapipe/${mod.name}/${mod.version}/${mod.name}-${mod.version}.pom"
                                        )
                                        .toFile()
                                if (!pomPath.exists()) "$mod (missing in $localM2Root)" else null
                            }
                            .distinct()
                    if (violations.isNotEmpty()) {
                        throw GradleException(
                            "requireLocalMediapipeRepo=true failed for configuration '$name': " +
                                "artifacts not found in ~/.m2/repository: ${violations.joinToString(", ")}"
                        )
                    }
                }
            }
        }
    }
}
