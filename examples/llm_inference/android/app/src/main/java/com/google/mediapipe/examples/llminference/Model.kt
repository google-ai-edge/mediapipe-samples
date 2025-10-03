package com.google.mediapipe.examples.llminference


import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend

// NB: Make sure the filename is *unique* per model you use!
// Weight caching is currently based on filename alone.
enum class Model(
    val path: String,
    val url: String,
    val licenseUrl: String,
    val needsAuth: Boolean,
    val preferredBackend: Backend?,
    val thinking: Boolean,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
) {
    GEMMA3_1B_IT_CPU(
        path = "/data/local/tmp/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        licenseUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
        needsAuth = true,
        preferredBackend = Backend.CPU,
        thinking = false,
        temperature = 1.0f,
        topK = 64,
        topP = 0.95f
    ),
    GEMMA_3_1B_IT_GPU(
        path = "/data/local/tmp/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        licenseUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
        needsAuth = true,
        preferredBackend = Backend.GPU,
        thinking = false,
        temperature = 1.0f,
        topK = 64,
        topP = 0.95f
    ),
    GEMMA_2_2B_IT_CPU(
        path = "/data/local/tmp/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT",
        needsAuth = true,
        preferredBackend = Backend.CPU,
        thinking = false,
        temperature = 0.6f,
        topK = 50,
        topP = 0.9f
    ),
    DEEPSEEK_R1_DISTILL_QWEN_1_5_B(
        path = "/data/local/tmp/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "",
        needsAuth = false,
        preferredBackend = Backend.CPU,
        thinking = true,
        temperature = 0.6f,
        topK = 40,
        topP = 0.7f
    ),
    LLAMA_3_2_1B_INSTRUCT(
        path = "/data/local/tmp/Llama-3.2-1B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Llama-3.2-1B-Instruct/resolve/main/Llama-3.2-1B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "https://huggingface.co/litert-community/Llama-3.2-1B-Instruct",
        needsAuth = true,
        preferredBackend = Backend.CPU,
        thinking = false,
        temperature = 0.6f,
        topK = 64,
        topP = 0.9f
    ),
    LLAMA_3_2_3B_INSTRUCT(
        path = "/data/local/tmp/Llama-3.2-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Llama-3.2-3B-Instruct/resolve/main/Llama-3.2-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "https://huggingface.co/litert-community/Llama-3.2-3B-Instruct",
        needsAuth = true,
        preferredBackend = Backend.CPU,
        thinking = false,
        temperature = 0.6f,
        topK = 64,
        topP = 0.9f,
    ),
    PHI_4_MINI_INSTRUCT(
        path = "/data/local/tmp/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "",
        needsAuth = false,
        preferredBackend = Backend.CPU,
        thinking = false,
        temperature = 0.6f,
        topK = 40,
        topP = 1.0f
    ),
    QWEN2_0_5B_INSTRUCT(
        path = "/data/local/tmp/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "",
        needsAuth = false,
        preferredBackend = Backend.CPU,
        thinking = false,
        temperature = 0.95f,
        topK = 40,
        topP = 1.0f
    ),
    QWEN2_1_5B_INSTRUCT(
        path = "/data/local/tmp/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "",
        needsAuth = false,
        preferredBackend = Backend.CPU,
        thinking = false,
        temperature = 0.95f,
        topK = 40,
        topP = 1.0f
    ),
    QWEN2_5_3B_INSTRUCT(
        path = "/data/local/tmp/Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-3B-Instruct/resolve/main/Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "",
        needsAuth = false,
        preferredBackend = Backend.CPU,
        thinking = false,
        temperature = 0.95f,
        topK = 40,
        topP = 1.0f
    ),
    SMOLLM_135M_INSTRUCT(
        path = "/data/local/tmp/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "",
        needsAuth = false,
        preferredBackend = Backend.CPU,
        thinking = false,
        temperature = 0.95f,
        topK = 40,
        topP = 1.0f
    ),
    TINYLLAMA_1_1B_CHAT_V1_0(
        path = "/data/local/tmp/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "",
        needsAuth = false,
        preferredBackend = Backend.CPU,
        thinking = false,
        temperature = 0.95f,
        topK = 40,
        topP = 1.0f
    ),
}
