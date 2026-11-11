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

data class VerifiedModelDownload(
    val url: String,
    val destFile: File,
    val sha256: String,
)

val yamnetModel = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite",
    destFile = file("$assetDir/yamnet.tflite"),
    sha256 = "4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf",
)

val sampleSpeechWav = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-assets/speech_16000_hz_mono.wav",
    destFile = file("$assetDir/speech_16000_hz_mono.wav"),
    sha256 = "71caf50b8757d6ab9cad5eae4d36669d3c20c225a51660afd7fe0dc44cdb74f6",
)

fun registerVerifiedDownload(taskName: String, spec: VerifiedModelDownload) =
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
                val actual = tmpFile.sha256Hex()
                if (actual != spec.sha256) {
                    throw GradleException(
                        "Checksum mismatch for ${spec.destFile.name}: expected ${spec.sha256}, got $actual"
                    )
                }
                Files.move(tmpFile.toPath(), spec.destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                tmpFile.delete()
            }
        }
    }

val downloadYamnetModel = registerVerifiedDownload("downloadYamnetModel", yamnetModel)
val downloadSampleSpeechWav = registerVerifiedDownload("downloadSampleSpeechWav", sampleSpeechWav)

tasks.register("verifyModels") {
    dependsOn(downloadYamnetModel, downloadSampleSpeechWav)
    doLast {
        listOf(yamnetModel, sampleSpeechWav).forEach { spec ->
            if (!spec.destFile.exists() || spec.destFile.length() == 0L) {
                throw GradleException(
                    "Asset ${spec.destFile.name} is missing or empty (stubbed?). Delete it and re-run to download."
                )
            }
            val actual = spec.destFile.sha256Hex()
            if (actual != spec.sha256) {
                throw GradleException(
                    "Checksum mismatch for ${spec.destFile.name}: expected ${spec.sha256}, got $actual"
                )
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadYamnetModel, downloadSampleSpeechWav)
}
