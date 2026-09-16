package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey
    val phone: String, // Format: 2137XXXXXXXX
    val displayPhone: String, // Format: 07XXXXXXXX
    val token: String,
    val addedDate: Long = System.currentTimeMillis(),
    val lastActivation1Gb: Long = 0L,
    val lastActivation2Gb: Long = 0L,
    val lastActivation3Gb: Long = 0L,
    val lastActivationOffers: Long = 0L,
    val isActive: Boolean = true
)
