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

val faceLandmarkerTask = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task",
    destFile = file("$assetDir/face_landmarker.task"),
    sha256 = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
)

val downloadTaskFile = tasks.register("downloadTaskFile") {
    outputs.file(faceLandmarkerTask.destFile)
    onlyIf { !faceLandmarkerTask.destFile.exists() }
    doLast {
        faceLandmarkerTask.destFile.parentFile?.mkdirs()
        val tmpFile = file("${faceLandmarkerTask.destFile.absolutePath}.tmp")
        try {
            URI(faceLandmarkerTask.url).toURL().openStream().use { input ->
                Files.copy(input, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val actual = tmpFile.sha256Hex()
            if (actual != faceLandmarkerTask.sha256) {
                throw GradleException(
                    "Checksum mismatch for ${faceLandmarkerTask.destFile.name}: expected ${faceLandmarkerTask.sha256}, got $actual"
                )
            }
            Files.move(
                tmpFile.toPath(),
                faceLandmarkerTask.destFile.toPath(),
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
        if (!faceLandmarkerTask.destFile.exists() || faceLandmarkerTask.destFile.length() == 0L) {
            throw GradleException(
                "Model ${faceLandmarkerTask.destFile.name} is missing or empty (stubbed?). Delete it and re-run to download."
            )
        }
        val actual = faceLandmarkerTask.destFile.sha256Hex()
        if (actual != faceLandmarkerTask.sha256) {
            throw GradleException(
                "Checksum mismatch for ${faceLandmarkerTask.destFile.name}: expected ${faceLandmarkerTask.sha256}, got $actual"
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadTaskFile)
}
