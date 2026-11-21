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

val lite0Model = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-ObjectDetector-EfficientDet-Lite0/resolve/2c3b3e2fc45b9fddf8ef8ad03a63ca874458add6/efficientdet_lite0_float32.tflite",
    destFile = file("$assetDir/efficientdet-lite0.tflite"),
    sha256 = "40338edf5ec70d43e318b0a716a84d4564cd1802759a7a07170c7e43796dbf58",
)

val lite0Int8Model = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-ObjectDetector-EfficientDet-Lite0/resolve/2c3b3e2fc45b9fddf8ef8ad03a63ca874458add6/efficientdet_lite0_int8.tflite",
    destFile = file("$assetDir/efficientdet_lite0_int8.tflite"),
    sha256 = "0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb",
)

val lite2Model = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-ObjectDetector-EfficientDet-Lite2/resolve/50785e6df46b2549e3e3608fa9f0d238f345f482/efficientdet_lite2_float32.tflite",
    destFile = file("$assetDir/efficientdet-lite2.tflite"),
    sha256 = "ad2abbf2b4e10585e15176fd7b5ef03c28dda959ae26fc142549fdd1814db91d",
)

val ssdMobileNetV2Fp32Model = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-ObjectDetector-SSD-MobileNet-V2/resolve/a9d10f5380cec69294aca48e1ddf350fce3adefe/ssd_mobilenet_v2_float32.tflite",
    destFile = file("$assetDir/ssd_mobilenet_v2_float32.tflite"),
    sha256 = "b8ccb1a25d45455ba52e85f26531948e1cb75efeb94c7c3d456d54fd4d6fbdd2",
)

val ssdMobileNetV2Fp16Model = VerifiedModelDownload(
    url = "https://huggingface.co/litert-community/MediaPipe-ObjectDetector-SSD-MobileNet-V2/resolve/a9d10f5380cec69294aca48e1ddf350fce3adefe/ssd_mobilenet_v2_float16.tflite",
    destFile = file("$assetDir/ssd_mobilenet_v2_float16.tflite"),
    sha256 = "ee21e12bfdc464100c6008c865ab8294f6a04162e6ed2e11986f1175b02cfab2",
)

val allModels =
    listOf(
        lite0Model,
        lite0Int8Model,
        lite2Model,
        ssdMobileNetV2Fp32Model,
        ssdMobileNetV2Fp16Model,
    )

fun registerVerifiedDownload(taskName: String, spec: VerifiedModelDownload) =
    tasks.register(taskName) {
        outputs.file(spec.destFile)
        onlyIf { !spec.destFile.exists() || spec.destFile.sha256Hex() != spec.sha256 }
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

val downloadLite0Model = registerVerifiedDownload("downloadLite0Model", lite0Model)
val downloadLite0Int8Model = registerVerifiedDownload("downloadLite0Int8Model", lite0Int8Model)
val downloadLite2Model = registerVerifiedDownload("downloadLite2Model", lite2Model)
val downloadSsdMobileNetV2Fp32Model = registerVerifiedDownload("downloadSsdMobileNetV2Fp32Model", ssdMobileNetV2Fp32Model)
val downloadSsdMobileNetV2Fp16Model = registerVerifiedDownload("downloadSsdMobileNetV2Fp16Model", ssdMobileNetV2Fp16Model)

val allDownloadTasks =
    listOf(
        downloadLite0Model,
        downloadLite0Int8Model,
        downloadLite2Model,
        downloadSsdMobileNetV2Fp32Model,
        downloadSsdMobileNetV2Fp16Model,
    )

tasks.register("verifyModels") {
    dependsOn(allDownloadTasks)
    doLast {
        allModels.forEach { spec ->
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
    dependsOn(allDownloadTasks)
}
