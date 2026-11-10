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

val smallModel = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/1/mobilenet_v3_small.tflite",
    destFile = file("$assetDir/mobilenet_v3_small.tflite"),
    sha256 = "bbbb4c51a55a53905af1daec995ca1aae355046f8839bb8c9f5ce9271394bc40",
)

val largeModel = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_large/float32/1/mobilenet_v3_large.tflite",
    destFile = file("$assetDir/mobilenet_v3_large.tflite"),
    sha256 = "11af3c560dfeed7737cb4c03c23bf52a8403020784192d4dea0b74862a12828d",
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

val downloadSmallModel = registerVerifiedDownload("downloadSmallModel", smallModel)
val downloadLargeModel = registerVerifiedDownload("downloadLargeModel", largeModel)

tasks.register("verifyModels") {
    dependsOn(downloadSmallModel, downloadLargeModel)
    doLast {
        listOf(smallModel, largeModel).forEach { spec ->
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
    dependsOn(downloadSmallModel, downloadLargeModel)
}
