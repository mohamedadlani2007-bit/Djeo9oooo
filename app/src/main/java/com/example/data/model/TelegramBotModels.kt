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
    WAITING_MGM_RECEIVER,
    WAITING_NEW_PHONE,
    WAITING_NEW_PHONE_OTP
}

data class SavedTelegramPhoneAccount(
    val phone: String,
    var token: String,
    val addedAt: Long = System.currentTimeMillis(),
    var mgmInvitesSent: Int = 0,
    var last1GbActivatedAt: Long = 0L,
    var last2GbActivatedAt: Long = 0L,
    var last3GbActivatedAt: Long = 0L
)

data class TelegramUserSession(
    val chatId: Long,
    val username: String = "",
    val firstName: String = "",
    var state: TelegramUserState = TelegramUserState.IDLE,
    var pendingPhone: String = "",
    var activePhone: String = "",
    var activeToken: String = "",
    val savedAccounts: MutableList<SavedTelegramPhoneAccount> = mutableListOf(),
    var lastActivity: Long = System.currentTimeMillis()
)

