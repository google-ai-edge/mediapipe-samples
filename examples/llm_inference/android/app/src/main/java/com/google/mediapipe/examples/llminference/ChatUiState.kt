package com.google.mediapipe.examples.llminference

import androidx.compose.runtime.toMutableStateList

const val USER_PREFIX = "user"
const val MODEL_PREFIX = "model"

interface UiState {
    val messages: List<ChatMessage>
    val fullPrompt: String

    /**
     * Creates a new loading message.
     * Returns the id of that message.
     */
    fun createLoadingMessage(): String

    /**
     * Appends the specified text to the message with the specified ID.
     * @param done - indicates whether the model has finished generating the message.
     */
    fun appendMessage(id: String, text: String, done: Boolean = false)

    /**
     * Creates a new message with the specified text and author.
     * Return the id of that message.
     */
    fun addMessage(text: String, author: String): String
}

/**
 * A sample implementation of [UiState] that can be used with any model.
 */
class ChatUiState(
    messages: List<ChatMessage> = emptyList()
) : UiState {
    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    override val messages: List<ChatMessage> = _messages.reversed()

    // Prompt the model with the current chat history
    override val fullPrompt: String
        get() = _messages.joinToString(separator = "\n") { it.message }

    override fun createLoadingMessage(): String {
        val chatMessage = ChatMessage(author = MODEL_PREFIX, isLoading = true)
        _messages.add(chatMessage)
        return chatMessage.id
    }

    override fun appendMessage(id: String, text: String, done: Boolean) {
        val index = _messages.indexOfFirst { it.id == id }
        if (index != -1) {
            val newText = _messages[index].message + text
            _messages[index] = _messages[index].copy(message = newText, isLoading = false)
        }
    }

    override fun addMessage(text: String, author: String): String {
        val chatMessage = ChatMessage(
            message = text,
            author = author
        )
        _messages.add(chatMessage)
        return chatMessage.id
    }
}

/**
 * An implementation of [UiState] to be used with the Gemma model.
 */
class GemmaUiState(
    messages: List<ChatMessage> = emptyList()
) : UiState {
    private val START_TURN = "<start_of_turn>"
    private val END_TURN = "<end_of_turn>"

    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    override val messages: List<ChatMessage>
        get() = _messages
            .map {
                // Remove the prefix and suffix before showing a message in the UI
                it.copy(
                    message = it.message.replace(START_TURN + it.author + "\n", "")
                        .replace(END_TURN, "")
                )
            }.reversed()

    // Only using the last 4 messages to keep input + output short
    override val fullPrompt: String
        get() = _messages.takeLast(4).joinToString(separator = "\n") { it.message }

    override fun createLoadingMessage(): String {
        val chatMessage = ChatMessage(author = MODEL_PREFIX, isLoading = true)
        _messages.add(chatMessage)
        return chatMessage.id
    }

    fun appendFirstMessage(id: String, text: String) {
        appendMessage(id, "$START_TURN$MODEL_PREFIX\n$text", false)
    }

    override fun appendMessage(id: String, text: String, done: Boolean) {
        val index = _messages.indexOfFirst { it.id == id }
        if (index != -1) {
            val newText = if (done) {
                // Append the Suffix when model is done generating the response
                _messages[index].message + text + END_TURN
            } else {
                // Append the text
                _messages[index].message + text
            }
            _messages[index] = _messages[index].copy(message = newText, isLoading = false)
        }
    }

    override fun addMessage(text: String, author: String): String {
        val chatMessage = ChatMessage(
            message = "$START_TURN$author\n$text$END_TURN",
            author = author
        )
        _messages.add(chatMessage)
        return chatMessage.id
    }
}
