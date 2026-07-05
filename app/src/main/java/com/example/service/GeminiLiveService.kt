package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.BuildConfig
import com.example.api.GeminiLiveClient
import com.example.api.LiveApiLogStore
import com.example.api.LogDirection
import com.example.data.ChatMessage
import com.example.data.ChatRepository
import com.example.data.SettingsStore
import com.example.utils.AudioPlayer
import com.example.utils.AudioRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.File

class GeminiLiveService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repository: ChatRepository
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioPlayer: AudioPlayer
    private var tempRecordFile: File? = null
    private var silenceCheckJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_ID = "gemini_live_voice_channel"

        const val ACTION_START_SERVICE = "com.example.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
        const val ACTION_START_RECORDING = "com.example.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.action.STOP_RECORDING"
        const val ACTION_STOP_PLAYBACK = "com.example.action.STOP_PLAYBACK"
        const val ACTION_SET_THREAD = "com.example.action.SET_THREAD"

        const val EXTRA_THREAD_ID = "com.example.extra.THREAD_ID"

        // Global states shared with ViewModel/UI
        val isServiceRunning = MutableStateFlow(false)
        val activeThreadId = MutableStateFlow<Long?>(null)
        val isRecording = MutableStateFlow(false)
        val playingMessageId = MutableStateFlow<Long?>(null)
        val isLoading = MutableStateFlow(false)
        val errorMessage = MutableStateFlow<String?>(null)
    }

    override fun onCreate() {
        super.onCreate()
        repository = ChatRepository(applicationContext)
        audioRecorder = AudioRecorder(applicationContext)
        audioPlayer = AudioPlayer()
        createNotificationChannel()
        isServiceRunning.value = true
        Log.d("GeminiLiveService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE
        Log.d("GeminiLiveService", "Received action: $action")

        when (action) {
            ACTION_START_SERVICE -> {
                val threadId = intent?.getLongExtra(EXTRA_THREAD_ID, -1L) ?: -1L
                if (threadId != -1L) {
                    activeThreadId.value = threadId
                }
                showForegroundNotification()
            }
            ACTION_SET_THREAD -> {
                val threadId = intent?.getLongExtra(EXTRA_THREAD_ID, -1L) ?: -1L
                if (threadId != -1L && threadId != activeThreadId.value) {
                    // Stop current recording or playback if switching threads
                    stopRecordingInternal(sendData = false)
                    stopPlaybackInternal()
                    activeThreadId.value = threadId
                    errorMessage.value = null
                    Log.d("GeminiLiveService", "Active thread switched to $threadId")
                }
                showForegroundNotification()
            }
            ACTION_START_RECORDING -> {
                startRecordingInternal()
            }
            ACTION_STOP_RECORDING -> {
                stopRecordingInternal(sendData = true)
            }
            ACTION_STOP_PLAYBACK -> {
                stopPlaybackInternal()
            }
            ACTION_STOP_SERVICE -> {
                stopForegroundAndService()
            }
        }

        return START_NOT_STICKY
    }

    private fun startRecordingInternal() {
        val threadId = activeThreadId.value ?: return
        errorMessage.value = null
        stopPlaybackInternal()

        val recordFile = File(cacheDir, "user_recording_${System.currentTimeMillis()}.mp4")
        tempRecordFile = recordFile

        val success = audioRecorder.startRecording(recordFile)
        if (success) {
            isRecording.value = true
            Log.d("GeminiLiveService", "Started voice recording")
            showForegroundNotification()

            if (repository.settingsStore.handsFreeMode) {
                startSilenceDetection()
            }
        } else {
            errorMessage.value = "Failed to access microphone. Please check permissions."
            showForegroundNotification()
        }
    }

    private fun stopRecordingInternal(sendData: Boolean) {
        if (!isRecording.value) return
        isRecording.value = false
        audioRecorder.stopRecording()
        Log.d("GeminiLiveService", "Stopped voice recording")

        silenceCheckJob?.cancel()
        silenceCheckJob = null

        val recordFile = tempRecordFile
        if (sendData && recordFile != null && recordFile.exists() && recordFile.length() > 0) {
            sendMessageWithAudio(recordFile)
        } else {
            if (sendData) {
                errorMessage.value = "Recording failed or was too short."
            }
            showForegroundNotification()
        }
    }

    private fun startSilenceDetection() {
        silenceCheckJob?.cancel()
        silenceCheckJob = serviceScope.launch {
            var consecutiveSilenceMs = 0L
            val checkIntervalMs = 200L
            val silenceThreshold = 1800L // 1.8 seconds of silence to auto-send
            val speakingThreshold = 1200  // Mic amplitude threshold for speech detection

            var hasDetectedSpeech = false
            var initialSilenceMs = 0L
            val maxInitialSilenceMs = 5000L // 5 seconds maximum of silence before auto-stopping

            while (isActive && isRecording.value) {
                delay(checkIntervalMs)
                val amplitude = audioRecorder.getMaxAmplitude()
                Log.d("GeminiLiveService", "VAD Check: amplitude=$amplitude, speaking=$hasDetectedSpeech, silenceMs=$consecutiveSilenceMs")

                if (amplitude >= speakingThreshold) {
                    hasDetectedSpeech = true
                    consecutiveSilenceMs = 0L
                } else {
                    if (hasDetectedSpeech) {
                        consecutiveSilenceMs += checkIntervalMs
                        if (consecutiveSilenceMs >= silenceThreshold) {
                            Log.d("GeminiLiveService", "VAD: Silence detected. Auto-submitting speech.")
                            stopRecordingInternal(sendData = true)
                            break
                        }
                    } else {
                        initialSilenceMs += checkIntervalMs
                        if (initialSilenceMs >= maxInitialSilenceMs) {
                            Log.d("GeminiLiveService", "VAD: No initial speech detected. Auto-stopping.")
                            stopRecordingInternal(sendData = false)
                            break
                        }
                    }
                }
            }
        }
    }

    private fun stopPlaybackInternal() {
        audioPlayer.stopAudio()
        playingMessageId.value = null
        showForegroundNotification()
    }

    private fun sendMessageWithAudio(audioFile: File) {
        val threadId = activeThreadId.value ?: return
        isLoading.value = true
        errorMessage.value = null
        showForegroundNotification()

        serviceScope.launch {
            try {
                // Save user voice message in background
                val messageId = repository.saveMessage(threadId, "user", "[Voice Message]", audioFile.absolutePath)
                Log.d("GeminiLiveService", "Saved user voice message (id: $messageId)")

                // Execute Gemini Query
                executeGeminiQuery(threadId, audioFile)
            } catch (e: Exception) {
                Log.e("GeminiLiveService", "Failed to send audio message", e)
                errorMessage.value = "Error: ${e.message}"
            } finally {
                isLoading.value = false
                showForegroundNotification()
            }
        }
    }

    private suspend fun executeGeminiQuery(threadId: Long, userAudioFile: File) {
        val thread = repository.getThreadById(threadId) ?: return
        val apiKey = repository.settingsStore.apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            errorMessage.value = "API key not configured. Please add your key in settings."
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val messagesFlow = repository.getMessagesForThread(threadId)
                val rawMessages = messagesFlow.first()
                val modelName = thread.modelName.ifBlank { SettingsStore.DEFAULT_MODEL }
                val voiceName = thread.voiceName.ifBlank { SettingsStore.DEFAULT_VOICE }
                val systemPrompt = thread.systemPrompt.ifBlank { "" }
                val reasoningEffort = repository.settingsStore.reasoningEffort

                val result = GeminiLiveClient.executeLiveTurn(
                    apiKey = apiKey,
                    modelName = modelName,
                    voiceName = voiceName,
                    systemPrompt = systemPrompt,
                    rawMessages = rawMessages,
                    userAudioFile = userAudioFile,
                    reasoningEffort = reasoningEffort
                )

                // Save model audio file
                var responseAudioPath: String? = null
                if (result.responseAudioBytes != null) {
                    val audioFile = File(cacheDir, "gemini_voice_${System.currentTimeMillis()}.wav")
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

                // Update token stats
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

                // Automatically play returned audio
                if (responseAudioPath != null) {
                    withContext(Dispatchers.Main) {
                        playVoiceMessage(modelMsgId, File(responseAudioPath))
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiLiveService", "Gemini turn failed", e)
                withContext(Dispatchers.Main) {
                    errorMessage.value = "Gemini Live API Error: ${e.localizedMessage ?: "WebSocket connection failed"}"
                }
            }
        }
    }

    private fun playVoiceMessage(messageId: Long, audioFile: File) {
        playingMessageId.value = messageId
        showForegroundNotification()

        audioPlayer.playAudio(audioFile) {
            playingMessageId.value = null
            showForegroundNotification()

            if (repository.settingsStore.handsFreeMode) {
                serviceScope.launch {
                    delay(400) // Small delay to avoid click sound feedback or prompt overlap
                    if (!isRecording.value && playingMessageId.value == null && isLoading.value == false) {
                        Log.d("GeminiLiveService", "Hands-Free Mode: Auto-starting recording.")
                        startRecordingInternal()
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Gemini Live Voice Session"
            val descriptionText = "Keeps voice assistant active in background and locked screen"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showForegroundNotification() {
        // Fetch thread title
        serviceScope.launch {
            val title = activeThreadId.value?.let { repository.getThreadById(it)?.title } ?: "Voice Session"
            
            val intent = Intent(this@GeminiLiveService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(this@GeminiLiveService, 0, intent, pendingIntentFlags)

            val notificationBuilder = NotificationCompat.Builder(this@GeminiLiveService, CHANNEL_ID)
                .setSmallIcon(com.example.R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            // Dynamic title and message content based on current states
            val statusTitle: String
            val statusText: String

            when {
                isRecording.value -> {
                    statusTitle = "🎙️ Gemini Live: Listening..."
                    statusText = "Recording: $title"
                    
                    // Stop & Send action
                    val stopIntent = Intent(this@GeminiLiveService, GeminiLiveService::class.java).apply {
                        action = ACTION_STOP_RECORDING
                    }
                    val stopPendingIntent = PendingIntent.getService(this@GeminiLiveService, 1, stopIntent, pendingIntentFlags)
                    notificationBuilder.addAction(android.R.drawable.ic_media_play, "Stop & Send", stopPendingIntent)
                }
                isLoading.value -> {
                    statusTitle = "⏳ Gemini Live: Thinking..."
                    statusText = "Gemini is processing response..."
                }
                playingMessageId.value != null -> {
                    statusTitle = "🔊 Gemini Live: Speaking..."
                    statusText = "Playing voice response for: $title"

                    // Stop Playback action
                    val muteIntent = Intent(this@GeminiLiveService, GeminiLiveService::class.java).apply {
                        action = ACTION_STOP_PLAYBACK
                    }
                    val mutePendingIntent = PendingIntent.getService(this@GeminiLiveService, 2, muteIntent, pendingIntentFlags)
                    notificationBuilder.addAction(android.R.drawable.ic_media_pause, "Mute", mutePendingIntent)
                }
                else -> {
                    statusTitle = "🟢 Gemini Live Active"
                    statusText = "Ready to speak: $title"

                    // Start Recording action
                    val recordIntent = Intent(this@GeminiLiveService, GeminiLiveService::class.java).apply {
                        action = ACTION_START_RECORDING
                    }
                    val recordPendingIntent = PendingIntent.getService(this@GeminiLiveService, 3, recordIntent, pendingIntentFlags)
                    notificationBuilder.addAction(android.R.drawable.ic_btn_speak_now, "Speak", recordPendingIntent)
                }
            }

            // Always add Stop Service button
            val exitIntent = Intent(this@GeminiLiveService, GeminiLiveService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            val exitPendingIntent = PendingIntent.getService(this@GeminiLiveService, 4, exitIntent, pendingIntentFlags)
            notificationBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", exitPendingIntent)

            notificationBuilder
                .setContentTitle(statusTitle)
                .setContentText(statusText)

            val notification = notificationBuilder.build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun stopForegroundAndService() {
        Log.d("GeminiLiveService", "Stopping service and foreground")
        stopRecordingInternal(sendData = false)
        stopPlaybackInternal()
        isServiceRunning.value = false
        activeThreadId.value = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        audioPlayer.stopAudio()
        audioRecorder.stopRecording()
        isServiceRunning.value = false
        activeThreadId.value = null
        Log.d("GeminiLiveService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
