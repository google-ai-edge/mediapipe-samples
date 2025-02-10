package com.google.mediapipe.examples.llminference

// NB: Make sure the filename is *unique* per model you use!
// Weight caching is currently based on filename alone.
enum class Model(val path: String, val uiState: UiState, val temperature: Float, val topK: Int, val topP: Float) {
    GEMMA_CPU("/data/local/tmp/llm/gemma-2b-it-cpu-int4.bin", GemmaUiState(), temperature = 0.8f, topK = 40, topP = 1.0f),
    GEMMA_GPU("/data/local/tmp/llm/gemma-2b-it-gpu-int4.bin", GemmaUiState(), temperature = 0.8f, topK = 40, topP = 1.0f),
    DEEPSEEK_CPU("/data/local/tmp/llm/deepseek3k_q8_ekv1280.task", DeepSeekUiState(), temperature = 0.6f, topK = 40, topP = 0.7f),
}
