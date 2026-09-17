package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.TelegramUserSession
import com.example.data.model.TelegramUserState
import org.json.JSONObject

/**
 * Persistent storage for Telegram user sessions using JSON in SharedPreferences.
 * Ensures users in Telegram stay logged in even after the app restarts or the device reboots.
 */
class TelegramSessionStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "telegram_sessions_prefs"
        private const val KEY_PREFIX = "session_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(session: TelegramUserSession) {
        try {
            val accountsArray = org.json.JSONArray()
            for (acc in session.savedAccounts) {
                accountsArray.put(JSONObject().apply {
                    put("phone", acc.phone)
                    put("token", acc.token)
                    put("addedAt", acc.addedAt)
                    put("mgmInvitesSent", acc.mgmInvitesSent)
                    put("last1GbActivatedAt", acc.last1GbActivatedAt)
                    put("last2GbActivatedAt", acc.last2GbActivatedAt)
                    put("last3GbActivatedAt", acc.last3GbActivatedAt)
                })
            }

            val json = JSONObject().apply {
                put("chatId", session.chatId)
                put("username", session.username)
                put("firstName", session.firstName)
                put("state", session.state.name)
                put("pendingPhone", session.pendingPhone)
                put("activePhone", session.activePhone)
                put("activeToken", session.activeToken)
                put("lastActivity", session.lastActivity)
                put("savedAccounts", accountsArray)
            }
            prefs.edit().putString("$KEY_PREFIX${session.chatId}", json.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getSession(chatId: Long): TelegramUserSession? {
        val raw = prefs.getString("$KEY_PREFIX$chatId", null) ?: return null
        return try {
            val json = JSONObject(raw)
            val stateName = json.optString("state", TelegramUserState.IDLE.name)
            val state = try {
                TelegramUserState.valueOf(stateName)
            } catch (_: Exception) {
                TelegramUserState.IDLE
            }

            val activePhone = json.optString("activePhone", "")
            val activeToken = json.optString("activeToken", "")
            val accountsList = mutableListOf<com.example.data.model.SavedTelegramPhoneAccount>()
            val accountsArray = json.optJSONArray("savedAccounts")
            if (accountsArray != null) {
                for (i in 0 until accountsArray.length()) {
                    val accObj = accountsArray.getJSONObject(i)
                    accountsList.add(
                        com.example.data.model.SavedTelegramPhoneAccount(
                            phone = accObj.optString("phone", ""),
                            token = accObj.optString("token", ""),
                            addedAt = accObj.optLong("addedAt", System.currentTimeMillis()),
                            mgmInvitesSent = accObj.optInt("mgmInvitesSent", 0),
                            last1GbActivatedAt = accObj.optLong("last1GbActivatedAt", 0L),
                            last2GbActivatedAt = accObj.optLong("last2GbActivatedAt", 0L),
                            last3GbActivatedAt = accObj.optLong("last3GbActivatedAt", 0L)
                        )
                    )
                }
            }
            if (accountsList.isEmpty() && activePhone.isNotBlank()) {
                accountsList.add(
                    com.example.data.model.SavedTelegramPhoneAccount(
                        phone = activePhone,
                        token = activeToken
                    )
                )
            }

            TelegramUserSession(
                chatId = json.getLong("chatId"),
                username = json.optString("username", ""),
                firstName = json.optString("firstName", ""),
                state = state,
                pendingPhone = json.optString("pendingPhone", ""),
                activePhone = activePhone,
                activeToken = activeToken,
                savedAccounts = accountsList,
                lastActivity = json.optLong("lastActivity", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getAllSessions(): Map<Long, TelegramUserSession> {
        val result = mutableMapOf<Long, TelegramUserSession>()
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key.startsWith(KEY_PREFIX) && value is String) {
                try {
                    val json = JSONObject(value)
                    val chatId = json.getLong("chatId")
                    val stateName = json.optString("state", TelegramUserState.IDLE.name)
                    val state = try {
                        TelegramUserState.valueOf(stateName)
                    } catch (_: Exception) {
                        TelegramUserState.IDLE
                    }

                    val activePhone = json.optString("activePhone", "")
                    val activeToken = json.optString("activeToken", "")
                    val accountsList = mutableListOf<com.example.data.model.SavedTelegramPhoneAccount>()
                    val accountsArray = json.optJSONArray("savedAccounts")
                    if (accountsArray != null) {
                        for (i in 0 until accountsArray.length()) {
                            val accObj = accountsArray.getJSONObject(i)
                            accountsList.add(
                                com.example.data.model.SavedTelegramPhoneAccount(
                                    phone = accObj.optString("phone", ""),
                                    token = accObj.optString("token", ""),
                                    addedAt = accObj.optLong("addedAt", System.currentTimeMillis()),
                                    mgmInvitesSent = accObj.optInt("mgmInvitesSent", 0),
                                    last1GbActivatedAt = accObj.optLong("last1GbActivatedAt", 0L),
                                    last2GbActivatedAt = accObj.optLong("last2GbActivatedAt", 0L),
                                    last3GbActivatedAt = accObj.optLong("last3GbActivatedAt", 0L)
                                )
                            )
                        }
                    }
                    if (accountsList.isEmpty() && activePhone.isNotBlank()) {
                        accountsList.add(
                            com.example.data.model.SavedTelegramPhoneAccount(
                                phone = activePhone,
                                token = activeToken
                            )
                        )
                    }

                    result[chatId] = TelegramUserSession(
                        chatId = chatId,
                        username = json.optString("username", ""),
                        firstName = json.optString("firstName", ""),
                        state = state,
                        pendingPhone = json.optString("pendingPhone", ""),
                        activePhone = activePhone,
                        activeToken = activeToken,
                        savedAccounts = accountsList,
                        lastActivity = json.optLong("lastActivity", System.currentTimeMillis())
                    )
                } catch (_: Exception) {}
            }
        }
        return result
    }

    fun removeSession(chatId: Long) {
        prefs.edit().remove("$KEY_PREFIX$chatId").apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
