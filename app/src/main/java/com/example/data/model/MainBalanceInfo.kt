package com.example.data.model

data class MainBalanceInfo(
    val amount: String = "0.00 دج",
    val rawAmount: Double = 0.0,
    val currency: String = "DZD",
    val expirationDate: String? = null,
    val isSuccess: Boolean = true,
    val rawJson: String = ""
)
