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
        val buf = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            digest.update(buf, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

data class VerifiedTtsModelDownload(
    val taskSuffix: String,
    val url: String,
    val destFile: File,
    val expectedSha256: String?,
)

val defaultKokoroCpuUrl =
    "https://huggingface.co/litert-community/Kokoro-82M/resolve/bf1c2405642aa310bd210205c22d3ae1abc253fb/kokoro_int8_fp16.litertlm"
val overrideKokoroUrl =
    (project.findProperty("KOKORO_MODEL_URL") as String?)
        ?: System.getenv("KOKORO_MODEL_URL")
val resolvedKokoroCpuUrl = overrideKokoroUrl?.takeIf { it.isNotBlank() } ?: defaultKokoroCpuUrl

val ttsModels =
    listOf(
        VerifiedTtsModelDownload(
            taskSuffix = "KokoroModel",
            url = resolvedKokoroCpuUrl,
            destFile = file("$assetDir/kokoro_int8_fp16.litertlm"),
            expectedSha256 =
                if (resolvedKokoroCpuUrl == defaultKokoroCpuUrl) {
                    "e7b931ee5d4b7104012a838c9eee240d19ea69cb3ca269041a3274f83dbb7fa1"
                } else {
                    null
                },
        ),
        VerifiedTtsModelDownload(
            taskSuffix = "KokoroGpuModel",
            url =
                "https://huggingface.co/litert-community/Kokoro-82M/resolve/bf1c2405642aa310bd210205c22d3ae1abc253fb/kokoro_int8_gpu_fp16.litertlm",
            destFile = file("$assetDir/kokoro_int8_gpu_fp16.litertlm"),
            expectedSha256 = "1ef178b127ec48c737daf17eabb4cea5f6b3ffbc510aced270fe5c267bbeac52",
        ),
        VerifiedTtsModelDownload(
            taskSuffix = "PocketTtsModel",
            url =
                "https://huggingface.co/litert-community/pocket-tts/resolve/03f949ec418ccd4d153008f7ef9c58c754cc0616/pocket_tts_en_int8.litertlm",
            destFile = file("$assetDir/pocket_tts_en_int8.litertlm"),
            expectedSha256 = "465e0083cd61188772aed76160b07af0dcf59057497d7659f43f3f2b13a174a1",
        ),
        VerifiedTtsModelDownload(
            taskSuffix = "PhononModel",
            url =
                "https://huggingface.co/litert-community/phonon-31m-en/resolve/63a6170895a34600560f80dce4c70f7c90e32956/phonon_int8.litertlm",
            destFile = file("$assetDir/phonon_int8.litertlm"),
            expectedSha256 = "b33a9a0d65acec36762db38a2bff6d899f4aab34b196bb56d89fab5665eabebe",
        ),
    )

val repoValidationCacheDir: File? =
    generateSequence(file(assetDir)) { it.parentFile }
        .map { File(it, ".release_validation/assets") }
        .firstOrNull { it.isDirectory }

val downloadTasks =
    ttsModels.map { spec ->
        tasks.register("download${spec.taskSuffix}") {
            outputs.file(spec.destFile)
            onlyIf {
                !spec.destFile.exists() ||
                    (spec.expectedSha256 != null && spec.destFile.sha256Hex() != spec.expectedSha256)
            }
            doLast {
                spec.destFile.parentFile?.mkdirs()
                val tmpFile = file("${spec.destFile.absolutePath}.tmp")
                try {
                    val cached =
                        repoValidationCacheDir
                            ?.let { File(it, spec.destFile.name) }
                            ?.takeIf {
                                it.isFile &&
                                    (spec.expectedSha256 == null || it.sha256Hex() == spec.expectedSha256)
                            }
                    if (cached != null) {
                        Files.copy(cached.toPath(), tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    } else {
                        URI(spec.url).toURL().openStream().use { input ->
                            Files.copy(input, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                    val expected = spec.expectedSha256
                    if (expected != null) {
                        val actual = tmpFile.sha256Hex()
                        if (actual != expected) {
                            throw GradleException(
                                "Checksum mismatch for ${spec.destFile.name}: expected $expected, got $actual"
                            )
                        }
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
    }

tasks.register("verifyModels") {
    dependsOn(downloadTasks)
    doLast {
        for (spec in ttsModels) {
            if (!spec.destFile.exists() || spec.destFile.length() == 0L) {
                throw GradleException(
                    "Model ${spec.destFile.name} is missing or empty (stubbed?). Delete it and re-run to download."
                )
            }
            val expected = spec.expectedSha256
            if (expected != null) {
                val actual = spec.destFile.sha256Hex()
                if (actual != expected) {
                    throw GradleException(
                        "Checksum mismatch for ${spec.destFile.name}: expected $expected, got $actual"
                    )
                }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadTasks)
}
