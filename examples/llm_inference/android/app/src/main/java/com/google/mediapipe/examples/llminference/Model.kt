package com.google.mediapipe.examples.llminference

// NB: Make sure the filename is *unique* per model you use!
// Weight caching is currently based on filename alone.
enum class Model(
    val path: String,
    val url: String,
    val licenseUrl: String,
    val needsAuth: Boolean,
    val uiState: UiState,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
) {
    GEMMA_CPU(
        path = "/data/local/tmp/llm/gemma-2b-it-cpu-int4.bin",
        url = "",
        licenseUrl = "",
        needsAuth = true,
        GemmaUiState(),
        temperature = 0.8f,
        topK = 40,
        topP = 1.0f
    ),
    GEMMA_GPU(
        path = "/data/local/tmp/llm/gemma-2b-it-gpu-int4.bin",
        url = "",
        licenseUrl = "",
        needsAuth = true,
        GemmaUiState(),
        temperature = 0.8f,
        topK = 40,
        topP = 1.0f
    ),
    DEEPSEEK_CPU(
        path = "/data/local/tmp/llm/deepseek3k_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/deepseek_q8_ekv1280.task",
        licenseUrl = "",
        needsAuth = true,
        DeepSeeUiState(),
        temperature = 0.6f,
        topK = 40,
        topP = 0.7f
    ),
}
