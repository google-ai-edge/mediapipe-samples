package com.google.mediapipe.examples.llminference

import androidx.compose.runtime.toMutableStateList

const val USER_PREFIX = "user"
const val MODEL_PREFIX = "model"

interface UiState {
    val messages: List<ChatMessage>

    /** Creates a new loading message. */
    fun createLoadingMessage()

    /**
     * Appends the specified text to the message with the specified ID.
     * THe underlying implementations may split the re-use messages or create new ones. The method
     * always returns the ID of the message used.
     * @param done - indicates whether the model has finished generating the message.
     */
    fun appendMessage(text: String, done: Boolean = false)

    /** Creates a new message with the specified text and author. */
    fun addMessage(text: String, author: String)

    /** Clear all messages. */
    fun clearMessages()

    /** Formats a messages from the user into the prompt format of the model. */
    fun formatPrompt(text:String) : String
}

/**
 * A sample implementation of [UiState] that can be used with any model.
 */
class GenericUiState(
    messages: List<ChatMessage> = emptyList()
) : UiState {
    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    override val messages: List<ChatMessage> = _messages.asReversed()
    private var _currentMessageId = ""

    override fun createLoadingMessage() {
        val chatMessage = ChatMessage(author = MODEL_PREFIX, isLoading = true)
        _messages.add(chatMessage)
        _currentMessageId= chatMessage.id
    }
    
    override fun appendMessage(text: String, done: Boolean){
        val index = _messages.indexOfFirst { it.id == _currentMessageId }
        if (index != -1) {
            val newText = _messages[index].rawMessage + text
            _messages[index] = _messages[index].copy(rawMessage = newText, isLoading = false)
        }
    }

    override fun addMessage(text: String, author: String) {
        val chatMessage = ChatMessage(
            rawMessage = text,
            author = author
        )
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    override fun clearMessages() {
        _messages.clear()
    }

    override fun formatPrompt(text: String): String {
        return text
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
    override val messages: List<ChatMessage> = _messages.asReversed()
    private var _currentMessageId = ""

    override fun createLoadingMessage() {
        val chatMessage = ChatMessage(author = MODEL_PREFIX, isLoading = true)
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    override fun appendMessage(text: String, done: Boolean) {
        val newText =  text.replace(END_TURN, "")
        val index = _messages.indexOfFirst { it.id == _currentMessageId }
        if (index != -1) {
            val newMessage =  _messages[index].rawMessage + newText
            _messages[index] = _messages[index].copy(rawMessage = newMessage, isLoading = false)
        }
    }

    override fun addMessage(text: String, author: String) {
        val chatMessage = ChatMessage(
            rawMessage = text,
            author = author
        )
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    override fun clearMessages() {
        _messages.clear()
    }

    override fun formatPrompt(text: String): String {
        return "$START_TURN$USER_PREFIX\n$text$END_TURN$START_TURN$MODEL_PREFIX"
    }
}


/** An implementation of [UiState] to be used with the DeepSeek model. */
class DeepSeekUiState(
    messages: List<ChatMessage> = emptyList()
) : UiState {
    private var START_TOKEN = "<｜begin▁of▁sentence｜>"
    private var PROMPT_PREFIX = "<｜User｜>"
    private var PROMPT_SUFFIX = "<｜Assistant｜>"
    private var THINKING_MARKER_START = "<think>"
    private var THINKING_MARKER_END = "</think>"

    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    override val messages: List<ChatMessage> = _messages.asReversed()
    private var _currentMessageId = ""

    override fun createLoadingMessage() {
        val chatMessage = ChatMessage(author = MODEL_PREFIX, isLoading = true, isThinking = false)
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    override fun appendMessage(text: String, done: Boolean) {
        val index = _messages.indexOfFirst { it.id == _currentMessageId }

        if (text.contains(THINKING_MARKER_START)) {
            _messages[index] = _messages[index].copy(
                isThinking = true
            )
        }

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
        val newText =  suffix.replace(THINKING_MARKER_START, "").replace(THINKING_MARKER_END, "")
        _messages[index] = _messages[index].copy(
            rawMessage = _messages[index].rawMessage + newText,
            isLoading = false
        )
        return index
    }

    override fun addMessage(text: String, author: String) {
        val chatMessage = ChatMessage(
            rawMessage = text,
            author = author
        )
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    override fun clearMessages() {
        _messages.clear()
    }

    override fun formatPrompt(text: String): String {
       return "$START_TOKEN$PROMPT_PREFIX$text$PROMPT_SUFFIX"
    }
}
