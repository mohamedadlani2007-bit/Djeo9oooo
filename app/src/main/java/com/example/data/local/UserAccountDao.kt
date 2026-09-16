package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_accounts ORDER BY addedDate DESC")
    fun getAllAccounts(): Flow<List<UserAccountEntity>>

    @Query("SELECT * FROM user_accounts WHERE isActive = 1 LIMIT 1")
    fun getActiveAccount(): Flow<UserAccountEntity?>

    @Query("SELECT * FROM user_accounts WHERE phone = :phone LIMIT 1")
    suspend fun getAccountByPhone(phone: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAccount(account: UserAccountEntity)

    @Update
    suspend fun updateAccount(account: UserAccountEntity)

    @Query("UPDATE user_accounts SET isActive = CASE WHEN phone = :phone THEN 1 ELSE 0 END")
    suspend fun setActiveAccount(phone: String)

    @Query("UPDATE user_accounts SET lastActivation1Gb = :time WHERE phone = :phone")
    suspend fun updateLastActivation1Gb(phone: String, time: Long)

    @Query("UPDATE user_accounts SET lastActivation2Gb = :time WHERE phone = :phone")
    suspend fun updateLastActivation2Gb(phone: String, time: Long)

    @Query("UPDATE user_accounts SET lastActivation3Gb = :time WHERE phone = :phone")
    suspend fun updateLastActivation3Gb(phone: String, time: Long)

    @Query("UPDATE user_accounts SET lastActivationOffers = :time WHERE phone = :phone")
    suspend fun updateLastActivationOffers(phone: String, time: Long)

    @Query("UPDATE user_accounts SET token = 'EXPIRED' WHERE phone = :phone")
    suspend fun markTokenExpired(phone: String)

    @Query("DELETE FROM user_accounts WHERE phone = :phone")
    suspend fun deleteAccount(phone: String)

    @Query("SELECT COUNT(*) FROM user_accounts")
    fun getAccountsCount(): Flow<Int>
}
