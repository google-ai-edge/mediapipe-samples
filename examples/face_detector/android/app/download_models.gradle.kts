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

val shortRangeModel = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite",
    destFile = file("$assetDir/face_detection_short_range.tflite"),
    sha256 = "b4578f35940bf5a1a655214a1cce5cab13eba73c1297cd78e1a04c2380b0152f",
)

val fullRangeModel = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_full_range/float16/1/blaze_face_full_range.tflite",
    destFile = file("$assetDir/face_detection_full_range.tflite"),
    sha256 = "3698b18f063835bc609069ef052228fbe86d9c9a6dc8dcb7c7c2d69aed2b181b",
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

val downloadShortRangeModel = registerVerifiedDownload("downloadShortRangeModel", shortRangeModel)
val downloadFullRangeModel = registerVerifiedDownload("downloadFullRangeModel", fullRangeModel)

tasks.register("verifyModels") {
    dependsOn(downloadShortRangeModel, downloadFullRangeModel)
    doLast {
        listOf(shortRangeModel, fullRangeModel).forEach { spec ->
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
    dependsOn(downloadShortRangeModel, downloadFullRangeModel)
}
