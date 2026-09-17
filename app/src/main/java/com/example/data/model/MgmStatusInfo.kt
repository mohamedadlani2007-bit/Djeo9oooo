package com.example.data.model

data class MgmOfferItem(
    val code: String = "",
    val name: String = "",
    val status: String = "",
    val description: String = "",
    val isAvailable: Boolean = true,
    val remaining: Int? = null,
    val consumed: Int? = null
)

data class MgmStatusInfo(
    val isSuccess: Boolean = true,
    val totalAllowed: Int = 5,
    val usedInvites: Int = 0,
    val remainingInvites: Int = 5,
    val isAllConsumed: Boolean = false,
    val hasRemaining: Boolean = true,
    val statusSummary: String = "",
    val offers: List<MgmOfferItem> = emptyList(),
    val rawJson: String = ""
)
