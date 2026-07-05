package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ChatRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val chatDao = database.chatDao()
    val settingsStore = SettingsStore(context)

    val allThreads: Flow<List<ChatThread>> = chatDao.getAllThreads()

    suspend fun getThreadById(id: Long): ChatThread? {
        return chatDao.getThreadById(id)
    }

    suspend fun createThread(
        title: String,
        systemPrompt: String? = null,
        modelName: String? = null,
        voiceName: String? = null
    ): Long {
        val thread = ChatThread(
            title = title,
            systemPrompt = systemPrompt ?: settingsStore.systemPrompt.ifBlank { SettingsStore.DEFAULT_SYSTEM_PROMPT },
            modelName = modelName ?: settingsStore.modelName.ifBlank { SettingsStore.DEFAULT_MODEL },
            voiceName = voiceName ?: settingsStore.voiceName.ifBlank { SettingsStore.DEFAULT_VOICE }
        )
        return chatDao.insertThread(thread)
    }

    suspend fun updateThread(thread: ChatThread) {
        chatDao.updateThread(thread)
    }

    suspend fun deleteThread(thread: ChatThread) {
        chatDao.deleteThread(thread)
    }

    suspend fun deleteAllThreads() {
        chatDao.deleteAllThreads()
    }

    fun getMessagesForThread(threadId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForThread(threadId)
    }

    suspend fun saveMessage(threadId: Long, role: String, text: String?, audioPath: String?): Long {
        val message = ChatMessage(
            threadId = threadId,
            role = role,
            text = text,
            audioPath = audioPath
        )
        return chatDao.insertMessage(message)
    }
}
