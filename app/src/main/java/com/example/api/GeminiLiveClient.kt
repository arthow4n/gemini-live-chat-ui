package com.example.api

import android.util.Base64
import android.util.Log
import com.example.data.ChatMessage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred

data class LiveTurnResult(
    val responseText: String,
    val responseAudioBytes: ByteArray?,
    val promptTokens: Int,
    val candidatesTokens: Int,
    val totalTokens: Int,
    val cachedTokens: Int
)

object GeminiLiveClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun executeLiveTurn(
        apiKey: String,
        modelName: String,
        voiceName: String,
        systemPrompt: String,
        rawMessages: List<ChatMessage>,
        userAudioFile: File?
    ): LiveTurnResult {
        // ws URL for Gemini Live API
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        val deferredResult = CompletableDeferred<LiveTurnResult>()
        val textAccumulator = StringBuilder()
        val audioAccumulator = ByteArrayOutputStream()

        var promptTokens = 0
        var candidatesTokens = 0
        var totalTokens = 0
        var cachedTokens = 0

        Log.d("GeminiLiveClient", "Connecting to Gemini Live API WebSocket: models/gemini-3.1-flash-live-preview")
        LiveApiLogStore.addLog(LogDirection.INFO, "Connecting to Gemini Live API WebSocket...")

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("GeminiLiveClient", "WebSocket opened. Sending setup config...")
                LiveApiLogStore.addLog(LogDirection.INFO, "WebSocket Connection Opened successfully.")

                try {
                    // 1. Send Setup Configuration Message
                    val setupJson = JSONObject().apply {
                        put("setup", JSONObject().apply {
                            put("model", "models/gemini-3.1-flash-live-preview")
                            put("generationConfig", JSONObject().apply {
                                put("responseModalities", JSONArray(listOf("AUDIO")))
                                put("speechConfig", JSONObject().apply {
                                    put("voiceConfig", JSONObject().apply {
                                        put("prebuiltVoiceConfig", JSONObject().apply {
                                            put("voiceName", voiceName)
                                        })
                                    })
                                })
                            })
                            if (systemPrompt.isNotBlank()) {
                                put("systemInstruction", JSONObject().apply {
                                    put("parts", JSONArray().apply {
                                        put(JSONObject().apply {
                                            put("text", systemPrompt)
                                        })
                                    })
                                })
                            }
                            // Configure tools for Note-taking
                            put("tools", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("functionDeclarations", JSONArray().apply {
                                        put(JSONObject().apply {
                                            put("name", "take_note")
                                            put("description", "Writes down a note or memo. Call this tool when the user tells you to take a note, write down a thought, save an idea, or memoize some information. The content will be silently stored, and you do not need to repeat the text in your speech response.")
                                            put("parameters", JSONObject().apply {
                                                put("type", "OBJECT")
                                                put("properties", JSONObject().apply {
                                                    put("note_content", JSONObject().apply {
                                                        put("type", "STRING")
                                                        put("description", "The exact content of the note or info to write down.")
                                                    })
                                                })
                                                put("required", JSONArray(listOf("note_content")))
                                            })
                                        })
                                    })
                                })
                            })
                        })
                    }
                    val setupStr = setupJson.toString()
                    webSocket.send(setupStr)
                    Log.d("GeminiLiveClient", "Setup payload sent: $setupJson")
                    try {
                        val formattedSetup = JSONObject(setupStr).toString(4)
                        LiveApiLogStore.addLog(LogDirection.SEND, formattedSetup)
                    } catch (e: Exception) {
                        LiveApiLogStore.addLog(LogDirection.SEND, setupStr)
                    }

                    // 2. Send Client Content turn message
                    val turnsArray = JSONArray()
                    rawMessages.forEachIndexed { index, msg ->
                        val isLastMessage = index == rawMessages.lastIndex
                        val turnObj = JSONObject()
                        turnObj.put("role", msg.role)

                        val partsArray = JSONArray()
                        if (msg.role == "user") {
                            if (isLastMessage && userAudioFile != null) {
                                val audioBytes = userAudioFile.readBytes()
                                val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
                                partsArray.put(JSONObject().apply {
                                    put("text", "Respond to my voice input directly.")
                                })
                                partsArray.put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", "audio/mp4")
                                        put("data", base64Audio)
                                    })
                                })
                            } else {
                                partsArray.put(JSONObject().apply {
                                    put("text", msg.text ?: "[Voice]")
                                })
                            }
                        } else {
                            partsArray.put(JSONObject().apply {
                                    put("text", msg.text ?: "")
                            })
                        }
                        turnObj.put("parts", partsArray)
                        turnsArray.put(turnObj)
                    }

                    val clientContentJson = JSONObject().apply {
                        put("clientContent", JSONObject().apply {
                            put("turns", turnsArray)
                            put("turnComplete", true)
                        })
                    }
                    val clientContentStr = clientContentJson.toString()
                    webSocket.send(clientContentStr)
                    Log.d("GeminiLiveClient", "Client content with ${turnsArray.length()} turns sent.")
                    try {
                        val formattedClientContent = JSONObject(clientContentStr).toString(4)
                        LiveApiLogStore.addLog(LogDirection.SEND, formattedClientContent)
                    } catch (e: Exception) {
                        LiveApiLogStore.addLog(LogDirection.SEND, clientContentStr)
                    }

                } catch (e: Exception) {
                    Log.e("GeminiLiveClient", "Failed during setup/clientContent generation", e)
                    LiveApiLogStore.addLog(LogDirection.ERROR, "Failed setup: ${e.localizedMessage ?: e.message}")
                    deferredResult.completeExceptionally(e)
                    webSocket.close(1000, "Setup failed")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("GeminiLiveClient", "Received real-time text packet: $text")
                try {
                    val formattedMsg = try {
                        JSONObject(text).toString(4)
                    } catch (e: Exception) {
                        text
                    }
                    LiveApiLogStore.addLog(LogDirection.RECEIVE, formattedMsg)
                } catch (e: Exception) {
                    LiveApiLogStore.addLog(LogDirection.RECEIVE, text)
                }

                try {
                    val json = JSONObject(text)

                    // Check for toolCall from the model
                    if (json.has("toolCall")) {
                        val toolCall = json.getJSONObject("toolCall")
                        if (toolCall.has("functionCalls")) {
                            val functionCalls = toolCall.getJSONArray("functionCalls")
                            val functionResponses = JSONArray()
                            for (i in 0 until functionCalls.length()) {
                                val callObj = functionCalls.getJSONObject(i)
                                val callId = callObj.getString("id")
                                val callName = callObj.getString("name")
                                val callArgs = callObj.optJSONObject("args") ?: JSONObject()

                                Log.d("GeminiLiveClient", "Model requested toolCall: $callName (id: $callId) with args: $callArgs")

                                if (callName == "take_note") {
                                    val noteContent = callArgs.optString("note_content", "")

                                    // Add a clean informational log to the Live API log store so it shows on the UI!
                                    LiveApiLogStore.addLog(
                                        LogDirection.INFO,
                                        "📝 [LLM Tool Call] take_note invoked\n" +
                                        "ID: $callId\n" +
                                        "Content: \"$noteContent\""
                                    )

                                    // Return success response to LLM
                                    val responseObj = JSONObject().apply {
                                        put("output", JSONObject().apply {
                                            put("result", "Note successfully saved.")
                                        })
                                    }
                                    val funcResponse = JSONObject().apply {
                                        put("id", callId)
                                        put("name", callName)
                                        put("response", responseObj)
                                    }
                                    functionResponses.put(funcResponse)
                                }
                            }

                            if (functionResponses.length() > 0) {
                                val toolResponseJson = JSONObject().apply {
                                    put("toolResponse", JSONObject().apply {
                                        put("functionResponses", functionResponses)
                                    })
                                }
                                val responseStr = toolResponseJson.toString()
                                webSocket.send(responseStr)
                                Log.d("GeminiLiveClient", "Sent toolResponse: $responseStr")
                                try {
                                    val formattedResp = JSONObject(responseStr).toString(4)
                                    LiveApiLogStore.addLog(LogDirection.SEND, formattedResp)
                                } catch (e: Exception) {
                                    LiveApiLogStore.addLog(LogDirection.SEND, responseStr)
                                }
                            }
                        }
                    }

                    // Check for serverContent
                    if (json.has("serverContent")) {
                        val serverContent = json.getJSONObject("serverContent")

                        if (serverContent.has("modelTurn")) {
                            val modelTurn = serverContent.getJSONObject("modelTurn")
                            if (modelTurn.has("parts")) {
                                val parts = modelTurn.getJSONArray("parts")
                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    if (part.has("text")) {
                                        val pText = part.getString("text")
                                        textAccumulator.append(pText)
                                    }
                                    if (part.has("inlineData")) {
                                        val inlineData = part.getJSONObject("inlineData")
                                        val mimeType = inlineData.optString("mimeType", "audio/pcm")
                                        val dataBase64 = inlineData.getString("data")
                                        val audioBytes = Base64.decode(dataBase64, Base64.DEFAULT)
                                        audioAccumulator.write(audioBytes)
                                    }
                                }
                            }
                        }

                        if (serverContent.has("usageMetadata")) {
                            val usage = serverContent.getJSONObject("usageMetadata")
                            promptTokens = usage.optInt("promptTokenCount", promptTokens)
                            candidatesTokens = usage.optInt("candidatesTokenCount", candidatesTokens)
                            totalTokens = usage.optInt("totalTokenCount", totalTokens)
                            cachedTokens = usage.optInt("cachedContentTokenCount", cachedTokens)
                        }

                        if (serverContent.optBoolean("turnComplete", false)) {
                            Log.d("GeminiLiveClient", "Model response turnCompleted. Finalizing.")
                            webSocket.close(1000, "Turn completed")
                            
                            val rawAudio = if (audioAccumulator.size() > 0) audioAccumulator.toByteArray() else null
                            val wavAudio = rawAudio?.let { addWavHeader(it, 24000) }

                            deferredResult.complete(
                                LiveTurnResult(
                                    responseText = textAccumulator.toString(),
                                    responseAudioBytes = wavAudio,
                                    promptTokens = promptTokens,
                                    candidatesTokens = candidatesTokens,
                                    totalTokens = totalTokens,
                                    cachedTokens = cachedTokens
                                )
                            )
                        }
                    }

                    // Also check root level usageMetadata just in case
                    if (json.has("usageMetadata")) {
                        val usage = json.getJSONObject("usageMetadata")
                        promptTokens = usage.optInt("promptTokenCount", promptTokens)
                        candidatesTokens = usage.optInt("candidatesTokenCount", candidatesTokens)
                        totalTokens = usage.optInt("totalTokenCount", totalTokens)
                        cachedTokens = usage.optInt("cachedContentTokenCount", cachedTokens)
                    }

                } catch (e: Exception) {
                    Log.e("GeminiLiveClient", "Error parsing WebSocket packet", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("GeminiLiveClient", "WebSocket Closing: $code / $reason")
                LiveApiLogStore.addLog(LogDirection.INFO, "WebSocket Closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("GeminiLiveClient", "WebSocket Closed: $code / $reason")
                LiveApiLogStore.addLog(LogDirection.INFO, "WebSocket Closed: $code / $reason")
                if (!deferredResult.isCompleted) {
                    val rawAudio = if (audioAccumulator.size() > 0) audioAccumulator.toByteArray() else null
                    val wavAudio = rawAudio?.let { addWavHeader(it, 24000) }

                    deferredResult.complete(
                        LiveTurnResult(
                            responseText = textAccumulator.toString(),
                            responseAudioBytes = wavAudio,
                            promptTokens = promptTokens,
                            candidatesTokens = candidatesTokens,
                            totalTokens = totalTokens,
                            cachedTokens = cachedTokens
                        )
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("GeminiLiveClient", "WebSocket Failure: ${t.message}", t)
                LiveApiLogStore.addLog(LogDirection.ERROR, "WebSocket Failure: ${t.localizedMessage ?: t.message}")
                if (!deferredResult.isCompleted) {
                    deferredResult.completeExceptionally(t)
                }
            }
        })

        try {
            return deferredResult.await()
        } catch (e: Exception) {
            webSocket.close(1001, "Error occurred")
            throw e
        }
    }

    private fun addWavHeader(pcmBytes: ByteArray, sampleRate: Int): ByteArray {
        val totalAudioLen = pcmBytes.size
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * 2 * channels // 16-bit mono

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte() // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte() // 'fmt '
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16 // fmt chunk size
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1 // PCM
        header[21] = 0

        header[22] = channels.toByte()
        header[23] = 0

        val longSampleRate = sampleRate.toLong()
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()

        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = (2 * channels).toByte() // block align
        header[33] = 0

        header[34] = 16 // bits per sample
        header[35] = 0

        header[36] = 'd'.code.toByte() // 'data'
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val wavFileBytes = ByteArray(44 + pcmBytes.size)
        System.arraycopy(header, 0, wavFileBytes, 0, 44)
        System.arraycopy(pcmBytes, 0, wavFileBytes, 44, pcmBytes.size)
        return wavFileBytes
    }
}
