package com.example.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.repository.AiService
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private const val TAG = "ChatViewModel"

data class ChatUiState(
    val currentSessionId: String? = null,
    val currentSessionTitle: String = "New Chat",
    val selectedLanguage: String = "ur", // "ur" or "en"
    val messages: List<ChatMessage> = emptyList(),
    val streamingText: String? = null,
    val isGenerating: Boolean = false,
    val sessions: List<ChatSession> = emptyList(),
    val errorMessage: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)
    private val aiService = AiService()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messageListenerJob: Job? = null
    private var streamJob: Job? = null

    init {
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            repository.observeSessions()
                .catch { e ->
                    Log.e(TAG, "Failed to observe sessions", e)
                }
                .collect { sessionList ->
                    _uiState.value = _uiState.value.copy(sessions = sessionList)
                    // If no session is selected and sessions exist, load the first one
                    if (_uiState.value.currentSessionId == null && sessionList.isNotEmpty()) {
                        selectSession(sessionList.first())
                    }
                }
        }
    }

    fun setLanguage(lang: String) {
        _uiState.value = _uiState.value.copy(selectedLanguage = lang)
    }

    fun startNewChat() {
        streamJob?.cancel()
        messageListenerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            currentSessionId = null,
            currentSessionTitle = if (_uiState.value.selectedLanguage == "ur") "نئی گفتگو" else "New Chat",
            messages = emptyList(),
            streamingText = null,
            isGenerating = false,
            errorMessage = null
        )
    }

    fun selectSession(session: ChatSession) {
        if (_uiState.value.currentSessionId == session.id) return
        streamJob?.cancel()
        messageListenerJob?.cancel()

        _uiState.value = _uiState.value.copy(
            currentSessionId = session.id,
            currentSessionTitle = session.title,
            selectedLanguage = session.language,
            streamingText = null,
            isGenerating = false,
            errorMessage = null
        )

        messageListenerJob = viewModelScope.launch {
            repository.observeMessages(session.id)
                .catch { e ->
                    Log.e(TAG, "Failed to observe messages for session ${session.id}", e)
                }
                .collect { messageList ->
                    _uiState.value = _uiState.value.copy(messages = messageList)
                }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_uiState.value.currentSessionId == sessionId) {
                startNewChat()
            }
        }
    }

    fun sendMessage(inputText: String) {
        val text = inputText.trim()
        if (text.isEmpty() || _uiState.value.isGenerating) return

        viewModelScope.launch {
            var sessionId = _uiState.value.currentSessionId
            val lang = _uiState.value.selectedLanguage

            // Create new session in Firestore if none active
            if (sessionId == null) {
                val sessionTitle = if (text.length > 35) text.take(35) + "..." else text
                val createResult = repository.createSession(sessionTitle, lang)
                if (createResult.isSuccess) {
                    sessionId = createResult.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        currentSessionId = sessionId,
                        currentSessionTitle = sessionTitle
                    )
                    // Start observing messages for newly created session
                    messageListenerJob?.cancel()
                    messageListenerJob = viewModelScope.launch {
                        repository.observeMessages(sessionId)
                            .catch { e -> Log.e(TAG, "Error observing new session messages", e) }
                            .collect { messageList ->
                                _uiState.value = _uiState.value.copy(messages = messageList)
                            }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to create chat session: ${createResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }
            }

            // Save user message to Firestore
            val userMsgResult = repository.addMessage(sessionId, "user", text, lang)
            if (userMsgResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save message: ${userMsgResult.exceptionOrNull()?.message}"
                )
                return@launch
            }

            // Generate AI response with streaming
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                streamingText = "",
                errorMessage = null
            )

            // Compile conversation history for multi-turn context
            val history = _uiState.value.messages.map { it.role to it.content }
            val accumulatedResponse = StringBuilder()

            streamJob = viewModelScope.launch {
                try {
                    aiService.generateChatStream(text, history).collect { chunk ->
                        accumulatedResponse.append(chunk)
                        _uiState.value = _uiState.value.copy(streamingText = accumulatedResponse.toString())
                    }

                    val finalAiText = accumulatedResponse.toString()
                    if (finalAiText.isNotBlank()) {
                        repository.addMessage(sessionId, "model", finalAiText, lang)
                    }
                    _uiState.value = _uiState.value.copy(
                        streamingText = null,
                        isGenerating = false
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "AI streaming failed", e)
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        streamingText = null,
                        errorMessage = e.localizedMessage ?: "AI response generation failed."
                    )
                }
            }
        }
    }
}
