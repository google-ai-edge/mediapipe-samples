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

val interactiveSegmentationTask = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/interactive_segmenter_v2/magic_touch/int8/1/interactive_segmentation.task",
    destFile = file("$assetDir/interactive_segmentation.task"),
    sha256 = "38431bc66b883404e8397f74c3579404315b9b52b04a46c6346fe906a7309b03",
)

val downloadTaskFile = tasks.register("downloadTaskFile") {
    outputs.file(interactiveSegmentationTask.destFile)
    onlyIf { !interactiveSegmentationTask.destFile.exists() }
    doLast {
        interactiveSegmentationTask.destFile.parentFile?.mkdirs()
        val tmpFile = file("${interactiveSegmentationTask.destFile.absolutePath}.tmp")
        try {
            URI(interactiveSegmentationTask.url).toURL().openStream().use { input ->
                Files.copy(input, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val actual = tmpFile.sha256Hex()
            if (actual != interactiveSegmentationTask.sha256) {
                throw GradleException(
                    "Checksum mismatch for ${interactiveSegmentationTask.destFile.name}: expected ${interactiveSegmentationTask.sha256}, got $actual"
                )
            }
            Files.move(
                tmpFile.toPath(),
                interactiveSegmentationTask.destFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            tmpFile.delete()
        }
    }
}

tasks.register("verifyModels") {
    dependsOn(downloadTaskFile)
    doLast {
        if (
            !interactiveSegmentationTask.destFile.exists() ||
                interactiveSegmentationTask.destFile.length() == 0L
        ) {
            throw GradleException(
                "Model ${interactiveSegmentationTask.destFile.name} is missing or empty (stubbed?). Delete it and re-run to download."
            )
        }
        val actual = interactiveSegmentationTask.destFile.sha256Hex()
        if (actual != interactiveSegmentationTask.sha256) {
            throw GradleException(
                "Checksum mismatch for ${interactiveSegmentationTask.destFile.name}: expected ${interactiveSegmentationTask.sha256}, got $actual"
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadTaskFile)
}
