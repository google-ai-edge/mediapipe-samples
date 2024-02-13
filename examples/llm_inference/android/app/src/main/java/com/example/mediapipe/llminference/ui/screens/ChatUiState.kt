package com.example.mediapipe.llminference.ui.screens

import androidx.compose.runtime.toMutableStateList

/**
 * Used to represent a ChatMessage
 */
data class ChatMessage(
    val message: String,
    val isFromUser: Boolean = false
)

class ChatUiState(
    messages: List<ChatMessage> = emptyList()
) {
    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    val messages: List<ChatMessage> = _messages

    fun addUserMessage(text: String) {
        _messages.add(ChatMessage(text, true))
    }

    fun addModelMessage(text: String) {
        _messages.add(ChatMessage(text, false))
    }
}