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

val languageDetectorModel = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/language_detector/language_detector/float32/1/language_detector.tflite",
    destFile = file("$assetDir/detection_model.tflite"),
    sha256 = "7db4f23dfe1ad8966b050b419a865da451143fd43eb6b606a256aadeeb1e5417",
)

val downloadModel =
    tasks.register("downloadModel") {
        outputs.file(languageDetectorModel.destFile)
        onlyIf { !languageDetectorModel.destFile.exists() }
        doLast {
            languageDetectorModel.destFile.parentFile?.mkdirs()
            val tmpFile = file("${languageDetectorModel.destFile.absolutePath}.tmp")
            try {
                URI(languageDetectorModel.url).toURL().openStream().use { input ->
                    Files.copy(input, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                val actual = tmpFile.sha256Hex()
                if (actual != languageDetectorModel.sha256) {
                    throw GradleException(
                        "Checksum mismatch for ${languageDetectorModel.destFile.name}: expected ${languageDetectorModel.sha256}, got $actual"
                    )
                }
                Files.move(
                    tmpFile.toPath(),
                    languageDetectorModel.destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                tmpFile.delete()
            }
        }
    }

tasks.register("verifyModels") {
    dependsOn(downloadModel)
    doLast {
        if (!languageDetectorModel.destFile.exists() || languageDetectorModel.destFile.length() == 0L) {
            throw GradleException(
                "Model ${languageDetectorModel.destFile.name} is missing or empty (stubbed?). Delete it and re-run to download."
            )
        }
        val actual = languageDetectorModel.destFile.sha256Hex()
        if (actual != languageDetectorModel.sha256) {
            throw GradleException(
                "Checksum mismatch for ${languageDetectorModel.destFile.name}: expected ${languageDetectorModel.sha256}, got $actual"
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadModel)
}
