package com.example.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class LogDirection {
    SEND, RECEIVE, INFO, ERROR
}

data class LiveApiLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: LogDirection,
    val payload: String
)

object LiveApiLogStore {
    private val _logs = MutableStateFlow<List<LiveApiLog>>(emptyList())
    val logs = _logs.asStateFlow()

    fun addLog(direction: LogDirection, payload: String) {
        _logs.update { current ->
            (current + LiveApiLog(direction = direction, payload = payload)).takeLast(200)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
