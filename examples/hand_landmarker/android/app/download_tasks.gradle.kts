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

val handLandmarkerTask = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task",
    destFile = file("$assetDir/hand_landmarker.task"),
    sha256 = "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1",
)

val downloadTaskFile = tasks.register("downloadTaskFile") {
    outputs.file(handLandmarkerTask.destFile)
    onlyIf { !handLandmarkerTask.destFile.exists() }
    doLast {
        handLandmarkerTask.destFile.parentFile?.mkdirs()
        val tmpFile = file("${handLandmarkerTask.destFile.absolutePath}.tmp")
        try {
            URI(handLandmarkerTask.url).toURL().openStream().use { input ->
                Files.copy(input, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val actual = tmpFile.sha256Hex()
            if (actual != handLandmarkerTask.sha256) {
                throw GradleException(
                    "Checksum mismatch for ${handLandmarkerTask.destFile.name}: expected ${handLandmarkerTask.sha256}, got $actual"
                )
            }
            Files.move(
                tmpFile.toPath(),
                handLandmarkerTask.destFile.toPath(),
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
        if (!handLandmarkerTask.destFile.exists() || handLandmarkerTask.destFile.length() == 0L) {
            throw GradleException(
                "Model ${handLandmarkerTask.destFile.name} is missing or empty (stubbed?). Delete it and re-run to download."
            )
        }
        val actual = handLandmarkerTask.destFile.sha256Hex()
        if (actual != handLandmarkerTask.sha256) {
            throw GradleException(
                "Checksum mismatch for ${handLandmarkerTask.destFile.name}: expected ${handLandmarkerTask.sha256}, got $actual"
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadTaskFile)
}
