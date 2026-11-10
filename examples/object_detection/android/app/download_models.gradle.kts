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

val lite0Model = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite",
    destFile = file("$assetDir/efficientdet-lite0.tflite"),
    sha256 = "40338edf5ec70d43e318b0a716a84d4564cd1802759a7a07170c7e43796dbf58",
)

val lite2Model = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite2/float32/1/efficientdet_lite2.tflite",
    destFile = file("$assetDir/efficientdet-lite2.tflite"),
    sha256 = "ad2abbf2b4e10585e15176fd7b5ef03c28dda959ae26fc142549fdd1814db91d",
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

val downloadLite0Model = registerVerifiedDownload("downloadLite0Model", lite0Model)
val downloadLite2Model = registerVerifiedDownload("downloadLite2Model", lite2Model)

tasks.register("verifyModels") {
    dependsOn(downloadLite0Model, downloadLite2Model)
    doLast {
        listOf(lite0Model, lite2Model).forEach { spec ->
            if (!spec.destFile.exists() || spec.destFile.length() == 0L) {
                throw GradleException(
                    "Model ${spec.destFile.name} is missing or empty (stubbed?). Delete it and re-run to download."
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
    dependsOn(downloadLite0Model, downloadLite2Model)
}
