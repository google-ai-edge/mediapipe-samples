package com.example.mediapipe.llminference

import androidx.compose.runtime.toMutableStateList

const val TURN_PREFIX = "<start_of_turn>"
const val TURN_SUFFIX = "<end_of_turn>"
const val USER_PREFIX = "user"
const val MODEL_PREFIX = "model"

/**
 * Used to represent a ChatMessage
 */
data class ChatMessage(
    val message: String,
    val author: String
) {
    val isFromUser: Boolean
        get() = author == USER_PREFIX
}

class ChatUiState(
    messages: List<ChatMessage> = emptyList()
) {
    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    val messages: List<ChatMessage> = _messages

    val fullPrompt: String
        get() = _messages.joinToString(separator = "\n") { it.message }

    fun addUserMessage(text: String) {
        _messages.add(ChatMessage(
            formatMessage(text, USER_PREFIX),
            USER_PREFIX
        ))
    }

    fun addModelMessage(text: String) {
        _messages.add(ChatMessage(
            formatMessage(text, MODEL_PREFIX),
            MODEL_PREFIX
        ))
    }

    private fun formatMessage(
        originalMessage: String,
        prefix: String
    ) = "$TURN_PREFIX$prefix\n$originalMessage$TURN_SUFFIX"
}