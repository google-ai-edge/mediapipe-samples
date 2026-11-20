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

val deeplabV3Model = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-ImageSegmenter-DeepLab-V3/resolve/739202fd92e4a822ba439afe91bdf69254227d94/deeplab_v3.tflite",
    destFile = file("$assetDir/deeplabv3.tflite"),
    sha256 = "ff36e24d40547fe9e645e2f4e8745d1876d6e38b332d39a82f0bf0f5d1d561b3",
)

val hairSegmenterModel = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-ImageSegmenter-Hair/resolve/636ae705ff62db7fcdb8af27b19abb8ffc168f34/hair_segmenter.tflite",
    destFile = file("$assetDir/hair_segmenter.tflite"),
    sha256 = "2628cf3ce5f695f604cbea2841e00befcaa3624bf80caf3664bef2656d59bf84",
)

val selfieMulticlassModel = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-ImageSegmenter-Selfie-Multiclass/resolve/c7b7e9e0f1e73c10a12114379077b5257e60c25c/selfie_multiclass_256x256.tflite",
    destFile = file("$assetDir/selfie_multiclass.tflite"),
    sha256 = "c6748b1253a99067ef71f7e26ca71096cd449baefa8f101900ea23016507e0e0",
)

val selfieSegmenterModel = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-ImageSegmenter-Selfie-Square/resolve/051d3732aad8fd018433520029e00ade7463aaea/selfie_segmenter.tflite",
    destFile = file("$assetDir/selfie_segmenter.tflite"),
    sha256 = "191ac9529ae506ee0beefa6b2c945a172dab9d07d1e802a290a4e4038226658b",
)

val selfieSegmenterLandscapeModel = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-ImageSegmenter-Selfie-Landscape/resolve/f04c07a5598479d0b03c796c0806329e9fab4082/selfie_segmenter_landscape.tflite",
    destFile = file("$assetDir/selfie_segmenter_landscape.tflite"),
    sha256 = "490e9ea734313e0de10fa0cd9e3c6133e36ea4db2b7a49bde9ef019f72796b8e",
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

val downloadDeeplabV3 = registerVerifiedDownload("downloadModelFile", deeplabV3Model)
val downloadHairSegmenter = registerVerifiedDownload("downloadHairSegmenterModelFile", hairSegmenterModel)
val downloadSelfieMulticlass = registerVerifiedDownload("downloadSelfieMulticlassSegmenterModelFile", selfieMulticlassModel)
val downloadSelfieSegmenter = registerVerifiedDownload("downloadSelfieSegmenterModelFile", selfieSegmenterModel)
val downloadSelfieLandscapeSegmenter = registerVerifiedDownload("downloadSelfieLandscapeSegmenterModelFile", selfieSegmenterLandscapeModel)

tasks.register("verifyModels") {
    dependsOn(
        downloadDeeplabV3,
        downloadHairSegmenter,
        downloadSelfieMulticlass,
        downloadSelfieSegmenter,
        downloadSelfieLandscapeSegmenter,
    )
    doLast {
        listOf(
            deeplabV3Model,
            hairSegmenterModel,
            selfieMulticlassModel,
            selfieSegmenterModel,
            selfieSegmenterLandscapeModel,
        ).forEach { spec ->
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
    dependsOn(
        downloadDeeplabV3,
        downloadHairSegmenter,
        downloadSelfieMulticlass,
        downloadSelfieSegmenter,
        downloadSelfieLandscapeSegmenter,
    )
}
