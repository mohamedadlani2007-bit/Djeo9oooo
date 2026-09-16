package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activation_history")
data class ActivationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val phone: String,
    val offerCode: String,
    val offerName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // SUCCESS, FAILED, LIMIT, EXPIRED
    val message: String
)
