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

val poseLiteTask = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-PoseLandmarker-Lite/resolve/105f65fd3484e46a25fe517a3722932d4392083c/pose_landmarker_lite.task",
    destFile = file("$assetDir/pose_landmarker_lite.task"),
    sha256 = "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a",
)

val poseFullTask = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-PoseLandmarker-Full/resolve/e356612f3ba14ec42a14f14d0d7d0a14d450de59/pose_landmarker_full.task",
    destFile = file("$assetDir/pose_landmarker_full.task"),
    sha256 = "4eaa5eb7a98365221087693fcc286334cf0858e2eb6e15b506aa4a7ecdcec4ad",
)

val poseHeavyTask = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-PoseLandmarker-Heavy/resolve/8a9deee6a176119dbaf22006b4a1db71fd889d82/pose_landmarker_heavy.task",
    destFile = file("$assetDir/pose_landmarker_heavy.task"),
    sha256 = "64437af838a65d18e5ba7a0d39b465540069bc8aae8308de3e318aad31fcbc7b",
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

val downloadPoseLiteTask = registerVerifiedDownload("downloadPoseLiteTask", poseLiteTask)
val downloadPoseFullTask = registerVerifiedDownload("downloadPoseFullTask", poseFullTask)
val downloadPoseHeavyTask = registerVerifiedDownload("downloadPoseHeavyTask", poseHeavyTask)

tasks.register("verifyModels") {
    dependsOn(downloadPoseLiteTask, downloadPoseFullTask, downloadPoseHeavyTask)
    doLast {
        listOf(poseLiteTask, poseFullTask, poseHeavyTask).forEach { spec ->
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
    dependsOn(downloadPoseLiteTask, downloadPoseFullTask, downloadPoseHeavyTask)
}
