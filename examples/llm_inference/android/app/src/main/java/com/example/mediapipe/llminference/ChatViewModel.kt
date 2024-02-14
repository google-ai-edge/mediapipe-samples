package com.example.mediapipe.llminference

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.launch

class ChatViewModel(
    private val inferenceModel: InferenceModel
) : ViewModel() {

    private val _uiState: MutableStateFlow<ChatUiState> =
        MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> =
        _uiState.asStateFlow()

    private val _textInputEnabled: MutableStateFlow<Boolean> =
        MutableStateFlow(true)
    val isTextInputEnabled: StateFlow<Boolean> =
        _textInputEnabled.asStateFlow()

    fun sendMessage(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.addMessage(userMessage, USER_PREFIX)
            setInputEnabled(false)
            try {
                val fullPrompt = _uiState.value.fullPrompt

                inferenceModel.generateResponseAsync(fullPrompt)
                var currentMessageId: String? = null
                inferenceModel.partialResults
                    .collectIndexed { index, (partialResult, isDone) ->
                        if (index == 0) {
                            currentMessageId =
                                _uiState.value.createNewModelMessage(partialResult)
                        } else {
                            currentMessageId?.let {
                                _uiState.value.appendMessage(currentMessageId!!, partialResult)
                                if (isDone) {
                                    // Append the Suffix to the complete message
                                    _uiState.value.appendMessage(currentMessageId!!, END_TURN)
                                    currentMessageId = null
                                    // Re-enable text input
                                    setInputEnabled(true)
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.value.addMessage(e.localizedMessage ?: "Unknown Error", MODEL_PREFIX)
                setInputEnabled(true)
            }
        }
    }

    private fun setInputEnabled(isEnabled: Boolean) {
        _textInputEnabled.value = isEnabled
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val inferenceModel = InferenceModel.getInstance(context)
                return ChatViewModel(inferenceModel) as T
            }
        }
    }
}