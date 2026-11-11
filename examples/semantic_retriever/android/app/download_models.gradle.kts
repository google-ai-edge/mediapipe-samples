/*
 * Copyright 2026 The MediaPipe Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

val retrieverModel = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/main/embeddinggemma-2-text-vision-440m.litertlm",
    destFile = file("$assetDir/embeddinggemma-2-text-vision-440m.litertlm"),
    sha256 = "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0",
)

val downloadSemanticRetrieverModel = tasks.register("downloadSemanticRetrieverModel") {
    outputs.file(retrieverModel.destFile)
    onlyIf { !retrieverModel.destFile.exists() }
    doLast {
        retrieverModel.destFile.parentFile?.mkdirs()
        val tmpFile = file("${retrieverModel.destFile.absolutePath}.tmp")
        try {
            URI(retrieverModel.url).toURL().openStream().use { input ->
                Files.copy(input, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val actual = tmpFile.sha256Hex()
            if (actual != retrieverModel.sha256) {
                throw GradleException(
                    "Checksum mismatch for ${retrieverModel.destFile.name}: expected ${retrieverModel.sha256}, got $actual"
                )
            }
            Files.move(
                tmpFile.toPath(),
                retrieverModel.destFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            tmpFile.delete()
        }
    }
}

tasks.register("verifyModels") {
    dependsOn(downloadSemanticRetrieverModel)
    doLast {
        if (!retrieverModel.destFile.exists() || retrieverModel.destFile.length() == 0L) {
            throw GradleException(
                "Model ${retrieverModel.destFile.name} is missing or empty (stubbed?). Delete it and re-run to download."
            )
        }
        val actual = retrieverModel.destFile.sha256Hex()
        if (actual != retrieverModel.sha256) {
            throw GradleException(
                "Checksum mismatch for ${retrieverModel.destFile.name}: expected ${retrieverModel.sha256}, got $actual"
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadSemanticRetrieverModel)
}
