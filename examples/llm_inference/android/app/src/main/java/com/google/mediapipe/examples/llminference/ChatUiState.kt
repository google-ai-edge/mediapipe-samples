package com.google.mediapipe.examples.llminference

import androidx.compose.runtime.toMutableStateList

const val USER_PREFIX = "user"
const val MODEL_PREFIX = "model"
const val THINKING_MARKER_END = "</think>"


/** Management of the message queue. */
class UiState(
    private val supportsThinking: Boolean = false,
    messages: List<ChatMessage> = emptyList()
)  {
    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    val messages: List<ChatMessage> = _messages.asReversed()
    private var _currentMessageId = ""

    /** Creates a new loading message. */
    fun createLoadingMessage() {
        val chatMessage = ChatMessage(author = MODEL_PREFIX, isLoading = true, isThinking = supportsThinking)
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    /**
     * Appends the specified text to the message with the specified ID.
     * The underlying implementations may split the re-use messages or create new ones. The method
     * always returns the ID of the message used.
     */
    fun appendMessage(text: String) {
        val index = _messages.indexOfFirst { it.id == _currentMessageId }

        if (text.contains(THINKING_MARKER_END)) { // The model is done thinking, we add a new bubble
            val thinkingEnd = text.indexOf(THINKING_MARKER_END) + THINKING_MARKER_END.length

            // Add text to current "thinking" bubble
            val prefix = text.substring(0, thinkingEnd);
            val suffix = text.substring(thinkingEnd);

            appendToMessage(_currentMessageId, prefix)

            if (_messages[index].isEmpty) {
                // There are no thoughts from the model. We can just re-use the current bubble
                _messages[index] = _messages[index].copy(
                    isThinking = false
                )
                appendToMessage(_currentMessageId, suffix)
            } else {
                // Create a new bubble for the remainder of the model response
                val message = ChatMessage(
                    rawMessage = suffix,
                    author = MODEL_PREFIX,
                    isLoading = true,
                    isThinking = false
                )
                _messages.add(message)
                _currentMessageId = message.id
            }
        } else {
            appendToMessage(_currentMessageId, text)
        }
    }

    private fun appendToMessage(id: String, suffix: String) : Int {
        val index = _messages.indexOfFirst { it.id == id }
        val newText =  suffix.replace(THINKING_MARKER_END, "")
        _messages[index] = _messages[index].copy(
            rawMessage = _messages[index].rawMessage + newText,
            isLoading = false
        )
        return index
    }

    /** Creates a new message with the specified text and author. */
    fun addMessage(text: String, author: String) {
        val chatMessage = ChatMessage(
            rawMessage = text,
            author = author
        )
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    /** Clear all messages. */
    fun clearMessages() {
        _messages.clear()
    }
}
