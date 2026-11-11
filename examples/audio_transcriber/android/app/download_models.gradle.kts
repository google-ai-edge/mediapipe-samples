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

import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

val assetDir = extra["ASSET_DIR"] as String

fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buf = ByteArray(8192)
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            digest.update(buf, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

data class VerifiedAssetDownload(
    val url: String,
    val destFile: File,
    val expectedSha256: String?,
)

val defaultParakeetUrl =
    "https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3/resolve/main/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm"
val overrideParakeetUrl =
    (project.findProperty("PARAKEET_MODEL_URL") as String?)
        ?: System.getenv("PARAKEET_MODEL_URL")
val resolvedParakeetUrl = overrideParakeetUrl?.takeIf { it.isNotBlank() } ?: defaultParakeetUrl

val parakeetModel =
    VerifiedAssetDownload(
        url = resolvedParakeetUrl,
        destFile = file("$assetDir/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm"),
        expectedSha256 =
            if (resolvedParakeetUrl == defaultParakeetUrl) {
                "66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504"
            } else {
                null
            },
    )

val sampleSpeechAudio =
    VerifiedAssetDownload(
        url = "https://storage.googleapis.com/mediapipe-assets/speech_16000_hz_mono.wav",
        destFile = file("$assetDir/speech_16000_hz_mono.wav"),
        expectedSha256 = "71caf50b8757d6ab9cad5eae4d36669d3c20c225a51660afd7fe0dc44cdb74f6",
    )

fun registerVerifiedDownload(taskName: String, spec: VerifiedAssetDownload) =
    tasks.register(taskName) {
        outputs.file(spec.destFile)
        onlyIf { !spec.destFile.exists() }
        doLast {
            spec.destFile.parentFile?.mkdirs()
            val tmpFile = file("${spec.destFile.absolutePath}.tmp")
            try {
                URI(spec.url).toURL().openStream().use { input ->
                    Files.copy(input, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                val expected = spec.expectedSha256
                if (expected != null) {
                    val actual = tmpFile.sha256Hex()
                    if (actual != expected) {
                        throw GradleException(
                            "Checksum mismatch for ${spec.destFile.name}: expected $expected, got $actual"
                        )
                    }
                }
                Files.move(tmpFile.toPath(), spec.destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                tmpFile.delete()
            }
        }
    }

val downloadParakeetModel =
    registerVerifiedDownload("downloadParakeetModel", parakeetModel)
val downloadSampleSpeechAudio =
    registerVerifiedDownload("downloadSampleSpeechAudio", sampleSpeechAudio)

tasks.register("verifyModels") {
    dependsOn(downloadParakeetModel, downloadSampleSpeechAudio)
    doLast {
        listOf(parakeetModel, sampleSpeechAudio).forEach { spec ->
            if (!spec.destFile.exists() || spec.destFile.length() == 0L) {
                throw GradleException(
                    "Asset ${spec.destFile.name} is missing or empty (stubbed?). Delete it and re-run to download."
                )
            }
            val expected = spec.expectedSha256
            if (expected != null) {
                val actual = spec.destFile.sha256Hex()
                if (actual != expected) {
                    throw GradleException(
                        "Checksum mismatch for ${spec.destFile.name}: expected $expected, got $actual"
                    )
                }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadParakeetModel, downloadSampleSpeechAudio)
}
