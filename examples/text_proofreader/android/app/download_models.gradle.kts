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

val proofreaderModel = VerifiedModelDownload(
    url = "https://storage.googleapis.com/mediapipe-models/text_proofreader/gemma_200m/latest/proofread_quant_200m.litertlm",
    destFile = file("$assetDir/proofread_quant_200m.litertlm"),
    sha256 = "2caa317d5a6f951af6e437edce3bb3a9fdedc85a7a8c2a8fcaec96318d7708cc",
)

val downloadTextProofreaderModel =
    tasks.register("downloadTextProofreaderModel") {
        outputs.file(proofreaderModel.destFile)
        onlyIf { !proofreaderModel.destFile.exists() }
        doLast {
            proofreaderModel.destFile.parentFile?.mkdirs()
            val tmpFile = file("${proofreaderModel.destFile.absolutePath}.tmp")
            try {
                URI(proofreaderModel.url).toURL().openStream().use { input ->
                    Files.copy(input, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                val actual = tmpFile.sha256Hex()
                if (actual != proofreaderModel.sha256) {
                    throw GradleException(
                        "Checksum mismatch for ${proofreaderModel.destFile.name}: expected ${proofreaderModel.sha256}, got $actual"
                    )
                }
                Files.move(
                    tmpFile.toPath(),
                    proofreaderModel.destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                tmpFile.delete()
            }
        }
    }

tasks.register("verifyModels") {
    dependsOn(downloadTextProofreaderModel)
    doLast {
        if (!proofreaderModel.destFile.exists() || proofreaderModel.destFile.length() == 0L) {
            throw GradleException(
                "Model ${proofreaderModel.destFile.name} is missing or empty (stubbed?). Delete it and re-run to download."
            )
        }
        val actual = proofreaderModel.destFile.sha256Hex()
        if (actual != proofreaderModel.sha256) {
            throw GradleException(
                "Checksum mismatch for ${proofreaderModel.destFile.name}: expected ${proofreaderModel.sha256}, got $actual"
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadTextProofreaderModel)
}
