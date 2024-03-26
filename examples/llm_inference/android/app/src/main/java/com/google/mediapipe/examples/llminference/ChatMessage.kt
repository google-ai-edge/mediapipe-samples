package com.google.mediapipe.examples.llminference

import java.util.UUID

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
