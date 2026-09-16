package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivationHistoryDao {
    @Query("SELECT * FROM activation_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ActivationHistoryEntity>>

    @Query("SELECT * FROM activation_history WHERE phone = :phone ORDER BY timestamp DESC")
    fun getHistoryForPhone(phone: String): Flow<List<ActivationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ActivationHistoryEntity)

    @Query("SELECT COUNT(*) FROM activation_history WHERE status = 'SUCCESS'")
    fun getSuccessfulCount(): Flow<Int>

    @Query("DELETE FROM activation_history")
    suspend fun clearHistory()
}
