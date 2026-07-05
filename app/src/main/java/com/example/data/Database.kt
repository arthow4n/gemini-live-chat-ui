package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_threads")
data class ChatThread(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val systemPrompt: String,
    val modelName: String,
    val voiceName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPromptTokens: Int = 0,
    val lastCandidatesTokens: Int = 0,
    val lastTotalTokens: Int = 0,
    val lastCachedTokens: Int = 0,
    val cumulativeInputTokens: Int = 0,
    val cumulativeOutputTokens: Int = 0
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThread::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("threadId")]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val role: String, // "user" or "model"
    val text: String?,
    val audioPath: String?, // Local file path of the audio file if any
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_threads ORDER BY createdAt DESC")
    fun getAllThreads(): Flow<List<ChatThread>>

    @Query("SELECT * FROM chat_threads WHERE id = :id")
    suspend fun getThreadById(id: Long): ChatThread?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThread): Long

    @Update
    suspend fun updateThread(thread: ChatThread)

    @Delete
    suspend fun deleteThread(thread: ChatThread)

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesForThread(threadId: Long): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: Long)
}

@Database(entities = [ChatThread::class, ChatMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gemini_voice_chat_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
