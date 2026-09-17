package com.example.data.model

data class TelegramLogItem(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val isError: Boolean = false,
    val isSuccess: Boolean = false
)

enum class TelegramUserState {
    IDLE,
    WAITING_OTP,
    LOGGED_IN,
    WAITING_MGM_RECEIVER
}

data class TelegramUserSession(
    val chatId: Long,
    val username: String = "",
    val firstName: String = "",
    var state: TelegramUserState = TelegramUserState.IDLE,
    var pendingPhone: String = "",
    var activePhone: String = "",
    var activeToken: String = "",
    var lastActivity: Long = System.currentTimeMillis()
)
