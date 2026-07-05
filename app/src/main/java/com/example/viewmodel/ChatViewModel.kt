package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.*
import com.example.data.ChatMessage
import com.example.data.ChatRepository
import com.example.data.ChatThread
import com.example.data.SettingsStore
import com.example.service.GeminiLiveService
import com.example.utils.AudioPlayer
import com.example.utils.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)
    private val audioRecorder = AudioRecorder(application)
    private val audioPlayer = AudioPlayer()

    // UI States
    val allThreads: StateFlow<List<ChatThread>> = repository.allThreads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeThreadId = MutableStateFlow<Long?>(null)
    val activeThreadId: StateFlow<Long?> = _activeThreadId.asStateFlow()

    val activeThread: StateFlow<ChatThread?> = combine(_activeThreadId, allThreads) { id, threads ->
        if (id != null) threads.find { it.id == id } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Recorder and Player status
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _playingMessageId = MutableStateFlow<Long?>(null)
    val playingMessageId: StateFlow<Long?> = _playingMessageId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Live API Logging
    val liveLogs: StateFlow<List<LiveApiLog>> = LiveApiLogStore.logs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearLiveLogs() {
        LiveApiLogStore.clearLogs()
    }

    // Settings States
    private val _customApiKey = MutableStateFlow(repository.settingsStore.apiKey)
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _defaultSystemPrompt = MutableStateFlow(repository.settingsStore.systemPrompt)
    val defaultSystemPrompt: StateFlow<String> = _defaultSystemPrompt.asStateFlow()

    private val _defaultModel = MutableStateFlow(repository.settingsStore.modelName)
    val defaultModel: StateFlow<String> = _defaultModel.asStateFlow()

    private val _defaultVoice = MutableStateFlow(repository.settingsStore.voiceName)
    val defaultVoice: StateFlow<String> = _defaultVoice.asStateFlow()

    private val _handsFreeMode = MutableStateFlow(repository.settingsStore.handsFreeMode)
    val handsFreeMode: StateFlow<Boolean> = _handsFreeMode.asStateFlow()

    // Temporary user recording file
    private var tempRecordFile: File? = null

    init {
        // Collect background service states to keep UI completely synchronized in real time!
        viewModelScope.launch {
            GeminiLiveService.isRecording.collect {
                _isRecording.value = it
            }
        }
        viewModelScope.launch {
            GeminiLiveService.playingMessageId.collect {
                _playingMessageId.value = it
            }
        }
        viewModelScope.launch {
            GeminiLiveService.isLoading.collect {
                _isLoading.value = it
            }
        }
        viewModelScope.launch {
            GeminiLiveService.errorMessage.collect {
                _errorMessage.value = it
            }
        }
        viewModelScope.launch {
            GeminiLiveService.activeThreadId.collect { serviceThreadId ->
                if (serviceThreadId != null && serviceThreadId != _activeThreadId.value) {
                    _activeThreadId.value = serviceThreadId
                }
            }
        }

        // Collect messages automatically when thread selection changes
        viewModelScope.launch {
            _activeThreadId.collectLatest { threadId ->
                if (threadId != null) {
                    repository.getMessagesForThread(threadId).collect {
                        _messages.value = it
                    }
                } else {
                    _messages.value = emptyList()
                }
            }
        }

        // Initialize first thread if none exists
        viewModelScope.launch {
            allThreads.firstOrNull { it.isNotEmpty() }?.let { threads ->
                if (_activeThreadId.value == null) {
                    selectThread(threads.first().id)
                }
            }
        }
    }

    // Settings actions
    fun saveApiKey(key: String) {
        repository.settingsStore.apiKey = key
        _customApiKey.value = key
    }

    fun saveSystemPrompt(prompt: String) {
        repository.settingsStore.systemPrompt = prompt
        _defaultSystemPrompt.value = prompt
    }

    fun saveModelName(model: String) {
        repository.settingsStore.modelName = model
        _defaultModel.value = model
    }

    fun saveVoiceName(voice: String) {
        repository.settingsStore.voiceName = voice
        _defaultVoice.value = voice
    }

    fun saveHandsFreeMode(enabled: Boolean) {
        repository.settingsStore.handsFreeMode = enabled
        _handsFreeMode.value = enabled
        Log.d("ChatViewModel", "Hands-Free Mode set to $enabled")
    }

    // Thread actions
    fun createThread(title: String, customPrompt: String? = null, customModel: String? = null, customVoice: String? = null) {
        viewModelScope.launch {
            val id = repository.createThread(
                title = title.ifBlank { "Chat ${System.currentTimeMillis() % 1000}" },
                systemPrompt = customPrompt,
                modelName = customModel,
                voiceName = customVoice
            )
            selectThread(id)
        }
    }

    fun selectThread(threadId: Long) {
        audioPlayer.stopAudio()
        _playingMessageId.value = null
        _activeThreadId.value = threadId
        sendServiceAction(GeminiLiveService.ACTION_SET_THREAD, threadId)
    }

    fun deleteThread(thread: ChatThread) {
        viewModelScope.launch {
            if (_activeThreadId.value == thread.id) {
                sendServiceAction(GeminiLiveService.ACTION_STOP_SERVICE)
                _activeThreadId.value = null
            }
            repository.deleteThread(thread)
            // Switch to another thread if available
            val threads = repository.allThreads.first()
            if (threads.isNotEmpty()) {
                selectThread(threads.first().id)
            }
        }
    }

    fun updateThreadPrompt(threadId: Long, newPrompt: String) {
        viewModelScope.launch {
            val current = repository.getThreadById(threadId)
            if (current != null) {
                val updated = current.copy(systemPrompt = newPrompt)
                repository.updateThread(updated)
            }
        }
    }

    // Voice actions
    fun startRecordingVoice() {
        val threadId = _activeThreadId.value ?: return
        sendServiceAction(GeminiLiveService.ACTION_START_RECORDING, threadId)
    }

    fun stopRecordingVoice() {
        sendServiceAction(GeminiLiveService.ACTION_STOP_RECORDING)
    }

    // Send a text message (optional fallback)
    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        val threadId = _activeThreadId.value ?: return

        viewModelScope.launch {
            // Save User Text message
            repository.saveMessage(threadId, "user", text, null)
            executeGeminiQuery(threadId)
        }
    }

    // Send an audio message
    private fun sendMessageWithAudio(audioFile: File) {
        val threadId = _activeThreadId.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // First transcribe or save user message in list (or we can just show "Voice Message")
            val messageId = repository.saveMessage(threadId, "user", "[Voice Message]", audioFile.absolutePath)

            try {
                // Execute API request
                executeGeminiQuery(threadId, audioFile)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in Gemini query", e)
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Central API calling logic
    private suspend fun executeGeminiQuery(threadId: Long, userAudioFile: File? = null) {
        _isLoading.value = true
        _errorMessage.value = null

        val thread = repository.getThreadById(threadId) ?: return
        val apiKey = _customApiKey.value.ifBlank { BuildConfig.GEMINI_API_KEY }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _errorMessage.value = "API key not configured. Please add your key in settings."
            _isLoading.value = false
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val rawMessages = _messages.value
                val modelName = thread.modelName.ifBlank { SettingsStore.DEFAULT_MODEL }
                val voiceName = thread.voiceName.ifBlank { SettingsStore.DEFAULT_VOICE }
                val systemPrompt = thread.systemPrompt.ifBlank { "" }

                val result = GeminiLiveClient.executeLiveTurn(
                    apiKey = apiKey,
                    modelName = modelName,
                    voiceName = voiceName,
                    systemPrompt = systemPrompt,
                    rawMessages = rawMessages,
                    userAudioFile = userAudioFile
                )

                // Save audio file if present
                var responseAudioPath: String? = null
                if (result.responseAudioBytes != null) {
                    val context = getApplication<Application>()
                    val audioFile = File(context.cacheDir, "gemini_voice_${System.currentTimeMillis()}.wav")
                    audioFile.writeBytes(result.responseAudioBytes)
                    responseAudioPath = audioFile.absolutePath
                }

                // Save model message in Database
                val modelMsgId = repository.saveMessage(
                    threadId = threadId,
                    role = "model",
                    text = result.responseText.ifBlank { "[Voice Response]" },
                    audioPath = responseAudioPath
                )

                // Save token usage stats
                val currentThread = repository.getThreadById(threadId)
                if (currentThread != null) {
                    val updatedThread = currentThread.copy(
                        lastPromptTokens = result.promptTokens,
                        lastCandidatesTokens = result.candidatesTokens,
                        lastTotalTokens = result.totalTokens,
                        lastCachedTokens = result.cachedTokens,
                        cumulativeInputTokens = currentThread.cumulativeInputTokens + result.promptTokens,
                        cumulativeOutputTokens = currentThread.cumulativeOutputTokens + result.candidatesTokens
                    )
                    repository.updateThread(updatedThread)
                }

                // Automatically play returned audio for fluid real-time experience!
                if (responseAudioPath != null) {
                    withContext(Dispatchers.Main) {
                        playVoiceMessage(modelMsgId, File(responseAudioPath))
                    }
                }

            } catch (e: Exception) {
                Log.e("ChatViewModel", "WebSocket Live turn query failure: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Gemini Live API Error: ${e.localizedMessage ?: "WebSocket connection failed"}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    // Playback logic
    fun playVoiceMessage(messageId: Long, audioFile: File) {
        sendServiceAction(GeminiLiveService.ACTION_STOP_PLAYBACK)
        if (_playingMessageId.value == messageId) {
            // Already playing this message, stop it
            audioPlayer.stopAudio()
            _playingMessageId.value = null
        } else {
            _playingMessageId.value = messageId
            audioPlayer.playAudio(audioFile) {
                _playingMessageId.value = null
            }
        }
    }

    fun stopPlayback() {
        sendServiceAction(GeminiLiveService.ACTION_STOP_PLAYBACK)
        audioPlayer.stopAudio()
        _playingMessageId.value = null
    }

    private fun sendServiceAction(action: String, threadId: Long? = null) {
        val context = getApplication<Application>()
        val intent = Intent(context, GeminiLiveService::class.java).apply {
            this.action = action
            if (threadId != null) {
                putExtra(GeminiLiveService.EXTRA_THREAD_ID, threadId)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to send action $action to GeminiLiveService", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stopAudio()
        audioRecorder.stopRecording()
    }
}
