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

// EmbeddingGemma 2 270M (~165 MB) is bundled into the APK assets on every build.
val embeddingGemmaModel = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/main/embeddinggemma-2-text-270m.litertlm",
    destFile = file("$assetDir/embeddinggemma-2-text-270m.litertlm"),
    sha256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb",
)

// Laya S256 (~678 MB) is bundled into the APK assets on every build.
val layaModel = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/decision_maker/laya/float32/laya_s256/latest/laya_s256.task",
    destFile = file("$assetDir/laya_s256.task"),
    sha256 = "8cb730b89ef99cb5c98cdbc7936de79deab77623ce25dc0031eec9e25c64c24e",
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
                Files.move(
                    tmpFile.toPath(),
                    spec.destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                tmpFile.delete()
            }
        }
    }

val downloadEmbeddingGemmaModel =
    registerVerifiedDownload("downloadEmbeddingGemmaModel", embeddingGemmaModel)
val downloadLayaModel = registerVerifiedDownload("downloadLayaModel", layaModel)

// GLiNER S256 (~980 MB) is not bundled, to keep the APK small: push it to the device instead
// (see README):
// https://storage.googleapis.com/mediapipe-models/decision_maker/gliner/float16/gliner_s256/latest/gliner_s256.task

tasks.register("verifyModels") {
    dependsOn(downloadEmbeddingGemmaModel, downloadLayaModel)
    doLast {
        listOf(embeddingGemmaModel, layaModel).forEach { spec ->
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
    dependsOn(downloadEmbeddingGemmaModel, downloadLayaModel)
}
