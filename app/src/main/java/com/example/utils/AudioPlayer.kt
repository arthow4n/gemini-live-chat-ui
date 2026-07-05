package com.example.utils

import android.media.MediaPlayer
import android.util.Log
import java.io.File

class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun playAudio(audioFile: File, onComplete: () -> Unit) {
        stopAudio()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    onComplete()
                    stopAudio()
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to play audio: ${e.message}", e)
            onComplete()
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to stop/release media player: ${e.message}", e)
        } finally {
            mediaPlayer = null
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            false
        }
    }
}
