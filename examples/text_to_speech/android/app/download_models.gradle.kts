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

data class VerifiedTtsModelDownload(
    val url: String,
    val destFile: File,
    val expectedSha256: String?,
)

val defaultKokoroUrl =
    "https://huggingface.co/litert-community/Kokoro-82M/resolve/main/kokoro_int8_fp16.litertlm"
val overrideKokoroUrl =
    (project.findProperty("KOKORO_MODEL_URL") as String?)
        ?: System.getenv("KOKORO_MODEL_URL")
val resolvedKokoroUrl = overrideKokoroUrl?.takeIf { it.isNotBlank() } ?: defaultKokoroUrl

val kokoroModel =
    VerifiedTtsModelDownload(
        url = resolvedKokoroUrl,
        destFile = file("$assetDir/kokoro_int8_fp16.litertlm"),
        expectedSha256 =
            if (resolvedKokoroUrl == defaultKokoroUrl) {
                "e7b931ee5d4b7104012a838c9eee240d19ea69cb3ca269041a3274f83dbb7fa1"
            } else {
                null
            },
    )

val downloadKokoroModel =
    tasks.register("downloadKokoroModel") {
        outputs.file(kokoroModel.destFile)
        onlyIf { !kokoroModel.destFile.exists() }
        doLast {
            kokoroModel.destFile.parentFile?.mkdirs()
            val tmpFile = file("${kokoroModel.destFile.absolutePath}.tmp")
            try {
                URI(kokoroModel.url).toURL().openStream().use { input ->
                    Files.copy(input, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                val expected = kokoroModel.expectedSha256
                if (expected != null) {
                    val actual = tmpFile.sha256Hex()
                    if (actual != expected) {
                        throw GradleException(
                            "Checksum mismatch for ${kokoroModel.destFile.name}: expected $expected, got $actual"
                        )
                    }
                }
                Files.move(
                    tmpFile.toPath(),
                    kokoroModel.destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                tmpFile.delete()
            }
        }
    }

tasks.register("verifyModels") {
    dependsOn(downloadKokoroModel)
    doLast {
        if (!kokoroModel.destFile.exists() || kokoroModel.destFile.length() == 0L) {
            throw GradleException(
                "Model ${kokoroModel.destFile.name} is missing or empty (stubbed?). Delete it and re-run to download."
            )
        }
        val expected = kokoroModel.expectedSha256
        if (expected != null) {
            val actual = kokoroModel.destFile.sha256Hex()
            if (actual != expected) {
                throw GradleException(
                    "Checksum mismatch for ${kokoroModel.destFile.name}: expected $expected, got $actual"
                )
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadKokoroModel)
}
