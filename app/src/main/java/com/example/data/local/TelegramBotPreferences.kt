package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class TelegramBotPreferences(context: Context) {
    companion object {
        private const val PREFS_NAME = "telegram_bot_prefs"
        private const val KEY_BOT_TOKEN = "key_bot_token"
        private const val KEY_AUTO_START = "key_auto_start"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBotToken(): String = prefs.getString(KEY_BOT_TOKEN, "").orEmpty()

    fun saveBotToken(token: String) {
        prefs.edit().putString(KEY_BOT_TOKEN, token.trim()).apply()
    }

    fun isAutoStart(): Boolean = prefs.getBoolean(KEY_AUTO_START, false)

    fun setAutoStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }
}
