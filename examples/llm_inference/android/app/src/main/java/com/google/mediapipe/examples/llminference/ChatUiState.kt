package com.google.mediapipe.examples.llminference

import androidx.compose.runtime.toMutableStateList

const val USER_PREFIX = "user"
const val MODEL_PREFIX = "model"

interface UiState {
    val messages: List<ChatMessage>

    /**
     * Creates a new loading message.
     * Returns the id of that message.
     */
    fun createLoadingMessage(): String

    /**
     * Appends the specified text to the message with the specified ID.
     * THe underlying implementations may split the re-use messages or create new ones. The method
     * always returns the ID of the message used.
     * @param done - indicates whether the model has finished generating the message.
     * @return the id of the message that was used.
     */
    fun appendMessage(id: String, text: String, done: Boolean = false):  String

    /**
     * Creates a new message with the specified text and author.
     * Return the id of that message.
     */
    fun addMessage(text: String, author: String): String

    /** Formats a messages from the user into the prompt format of the model. */
    fun formatPrompt(text:String) : String
}

/**
 * A sample implementation of [UiState] that can be used with any model.
 */
class ChatUiState(
    messages: List<ChatMessage> = emptyList()
) : UiState {
    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    override val messages: List<ChatMessage> = _messages.reversed()

    override fun createLoadingMessage(): String {
        val chatMessage = ChatMessage(author = MODEL_PREFIX, isLoading = true)
        _messages.add(chatMessage)
        return chatMessage.id
    }
    
    override fun appendMessage(id: String, text: String, done: Boolean) :  String{
        val index = _messages.indexOfFirst { it.id == id }
        if (index != -1) {
            val newText = _messages[index].rawMessage + text
            _messages[index] = _messages[index].copy(rawMessage = newText, isLoading = false)
        }
        return id
    }

    override fun addMessage(text: String, author: String): String {
        val chatMessage = ChatMessage(
            rawMessage = text,
            author = author
        )
        _messages.add(chatMessage)
        return chatMessage.id
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

    override fun createLoadingMessage(): String {
        val chatMessage = ChatMessage(author = MODEL_PREFIX, isLoading = true)
        _messages.add(chatMessage)
        return chatMessage.id
    }

    override fun appendMessage(id: String, text: String, done: Boolean): String {
        val index = _messages.indexOfFirst { it.id == id }
        if (index != -1) {
            val newText =  _messages[index].rawMessage + text
            _messages[index] = _messages[index].copy(rawMessage = newText, isLoading = false)
        }
        return id
    }

    override fun addMessage(text: String, author: String): String {
        val chatMessage = ChatMessage(
            rawMessage = text,
            author = author
        )
        _messages.add(chatMessage)
        return chatMessage.id
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

    override fun createLoadingMessage(): String {
        val chatMessage = ChatMessage(author = MODEL_PREFIX, isLoading = true, isThinking = false)
        _messages.add(chatMessage)
        return chatMessage.id
    }

    override fun appendMessage( id: String, text: String, done: Boolean): String {
        val index = _messages.indexOfFirst { it.id == id }

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

            appendToMessage(id, prefix)

            if (_messages[index].isEmpty) {
                // There are no thoughts from the model. We can just re-use the current bubble
                _messages[index] = _messages[index].copy(
                    isThinking = false
                )
                appendToMessage(id, suffix)
            } else {
                // Create a new bubble for the remainder of the model response
                val message = ChatMessage(
                    rawMessage = suffix,
                    author = MODEL_PREFIX,
                    isLoading = true,
                    isThinking = false
                )
                _messages.add(message)
                return message.id
            }
        } else {
            appendToMessage(id, text)
        }

        return id
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

    override fun addMessage(text: String, author: String): String {
        val chatMessage = ChatMessage(
            rawMessage = text,
            author = author
        )
        _messages.add(chatMessage)
        return chatMessage.id
    }

    override fun formatPrompt(text: String): String {
       return "$START_TOKEN$PROMPT_PREFIX$text$PROMPT_SUFFIX"
    }
}
