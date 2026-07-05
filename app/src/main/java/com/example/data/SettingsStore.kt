package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gemini_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_KEY = "custom_api_key"
        private const val KEY_SYSTEM_PROMPT = "default_system_prompt"
        private const val KEY_MODEL_NAME = "default_model_name"
        private const val KEY_VOICE_NAME = "default_voice_name"
        private const val KEY_HANDS_FREE_MODE = "hands_free_mode"
        private const val KEY_REASONING_EFFORT = "reasoning_effort"

        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful and expressive voice assistant. Respond concisely and speak naturally. You have a 'take_note' tool that you can use to write down notes, thoughts, and memos silently for the user without repeating the text aloud."
        const val DEFAULT_MODEL = "models/gemini-3.1-flash-live-preview"
        const val DEFAULT_VOICE = "Puck" // Options: Puck, Charon, Kore, Fenrir, Aoede
        const val DEFAULT_REASONING_EFFORT = "none" // Options: none, low, medium, high
    }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()

    var modelName: String
        get() = prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL_NAME, value).apply()

    var voiceName: String
        get() = prefs.getString(KEY_VOICE_NAME, DEFAULT_VOICE) ?: DEFAULT_VOICE
        set(value) = prefs.edit().putString(KEY_VOICE_NAME, value).apply()

    var handsFreeMode: Boolean
        get() = prefs.getBoolean(KEY_HANDS_FREE_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_HANDS_FREE_MODE, value).apply()

    var reasoningEffort: String
        get() = prefs.getString(KEY_REASONING_EFFORT, DEFAULT_REASONING_EFFORT) ?: DEFAULT_REASONING_EFFORT
        set(value) = prefs.edit().putString(KEY_REASONING_EFFORT, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
