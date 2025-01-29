package com.google.mediapipe.examples.llminference

// NB: Make sure the filename is *unique* per model you use!
// Weight caching is currently based on filename alone.
enum class Model(val path: String) {
    GEMMA_CPU("/data/local/tmp/llm/gemma-2b-it-cpu-int4.bin"),
    GEMMA_GPU("/data/local/tmp/llm/gemma-2b-it-gpu-int4.bin"),
}
