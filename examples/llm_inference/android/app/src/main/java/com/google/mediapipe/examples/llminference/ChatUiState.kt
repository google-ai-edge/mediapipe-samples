package com.google.mediapipe.examples.llminference

import androidx.compose.runtime.toMutableStateList
import java.util.UUID

const val START_TURN = "<start_of_turn>"
const val END_TURN = "<end_of_turn>"
const val USER_PREFIX = "user"
const val MODEL_PREFIX = "model"

/**
 * Used to represent a ChatMessage
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val message: String = "",
    val author: String,
    val isLoading: Boolean = false
) {
    val isFromUser: Boolean
        get() = author == USER_PREFIX
}

class ChatUiState(
    messages: List<ChatMessage> = emptyList()
) {
    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    val messages: List<ChatMessage> = _messages

    // Only using the last 4 messages to keep input + output short
    val fullPrompt: String
        get() = _messages.takeLast(4).joinToString(separator = "\n") { it.message }

    /**
     * Creates a new loading message.
     * Returns the id of that message.
     */
    fun createLoadingMessage(): String {
        val chatMessage = ChatMessage(
            author = MODEL_PREFIX,
            isLoading = true
        )
        _messages.add(chatMessage)
        return chatMessage.id
    }

    /**
     * Appends the specified text to the message with the specified ID
     */
    fun appendMessage(id: String, text: String) {
        val index = _messages.indexOfFirst { it.id == id }
        if (index != -1) {
            val newText = _messages[index].message + text
            _messages[index] = _messages[index].copy(message = newText, isLoading = false)
        }
    }

    fun addMessage(text: String, author: String): String {
        val chatMessage = ChatMessage(
            message = "$START_TURN$author\n$text$END_TURN",
            author = author
        )
        _messages.add(chatMessage)
        return chatMessage.id
    }
}
