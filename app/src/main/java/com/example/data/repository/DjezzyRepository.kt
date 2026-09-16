package com.example.data.repository

import com.example.data.local.ActivationHistoryDao
import com.example.data.local.ActivationHistoryEntity
import com.example.data.local.UserAccountDao
import com.example.data.local.UserAccountEntity
import com.example.data.model.Offer
import com.example.data.model.ProxyConfig
import com.example.data.remote.ActivationResult
import com.example.data.remote.DjezzyApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class DjezzyRepository(
    private val userAccountDao: UserAccountDao,
    private val activationHistoryDao: ActivationHistoryDao,
    private val apiClient: DjezzyApiClient = DjezzyApiClient()
) {

    companion object {
        const val COOLDOWN_1GB_MS = 24 * 60 * 60 * 1000L // 24 hours
        const val COOLDOWN_2GB_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
        const val COOLDOWN_3GB_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }

    val allAccounts: Flow<List<UserAccountEntity>> = userAccountDao.getAllAccounts()
    val activeAccount: Flow<UserAccountEntity?> = userAccountDao.getActiveAccount()
    val allHistory: Flow<List<ActivationHistoryEntity>> = activationHistoryDao.getAllHistory()

    fun updateProxyConfig(config: ProxyConfig) {
        apiClient.updateProxy(config)
    }

    suspend fun testConnection(): Pair<Boolean, String> {
        return apiClient.testConnection()
    }

    suspend fun requestOtp(phone: String): Result<Boolean> {
        return apiClient.requestOtp(phone)
    }

    suspend fun verifyOtpAndSaveAccount(phone: String, otp: String): Result<UserAccountEntity> {
        val result = apiClient.verifyOtp(phone, otp)
        return if (result.isSuccess) {
            val token = result.getOrThrow()
            val cleanPhone = apiClient.formatPhoneNumber(phone)
            val displayPhone = apiClient.formatDisplayPhone(phone)

            val existing = userAccountDao.getAccountByPhone(cleanPhone)
            val account = existing?.copy(
                token = token,
                isActive = true,
                displayPhone = displayPhone
            ) ?: UserAccountEntity(
                phone = cleanPhone,
                displayPhone = displayPhone,
                token = token,
                isActive = true
            )

            userAccountDao.insertOrUpdateAccount(account)
            userAccountDao.setActiveAccount(cleanPhone)
            Result.success(account)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("فشل التحقق من الرمز"))
        }
    }

    suspend fun switchAccount(phone: String) {
        userAccountDao.setActiveAccount(phone)
    }

    suspend fun logoutAccount(phone: String) {
        userAccountDao.deleteAccount(phone)
        val remaining = userAccountDao.getAllAccounts().firstOrNull()
        if (!remaining.isNullOrEmpty()) {
            userAccountDao.setActiveAccount(remaining.first().phone)
        }
    }

    suspend fun activateOffer(
        offer: Offer,
        onProgress: (step: String) -> Unit = {}
    ): ActivationResult {
        val current = activeAccount.firstOrNull()
            ?: return ActivationResult.Failed("يرجى تسجيل الدخول برقم جيزي أولاً")

        if (current.token == "EXPIRED" || current.token.isBlank()) {
            return ActivationResult.Expired("انتهت صلاحية الجلسة، يرجى تسجيل الدخول مجدداً")
        }

        val currentTime = System.currentTimeMillis()

        val result: ActivationResult = when (offer.code) {
            "FREE_1GB" -> {
                // Check 24h cooldown
                val elapsed = currentTime - current.lastActivation1Gb
                if (current.lastActivation1Gb > 0 && elapsed < COOLDOWN_1GB_MS) {
                    val remainingMs = COOLDOWN_1GB_MS - elapsed
                    val hours = remainingMs / (1000 * 60 * 60)
                    val minutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60)
                    return ActivationResult.Limit("لم تكمل 24 ساعة بعد. المتبقي: $hours ساعة و $minutes دقيقة")
                }
                apiClient.activate1Gb(current.token, current.phone) { step, currentAttempt, maxAttempts ->
                    onProgress(step)
                }
            }
            "FREE_2GB" -> {
                val elapsed = currentTime - current.lastActivation2Gb
                if (current.lastActivation2Gb > 0 && elapsed < COOLDOWN_2GB_MS) {
                    val remainingMs = COOLDOWN_2GB_MS - elapsed
                    val days = remainingMs / (1000 * 60 * 60 * 24)
                    val hours = (remainingMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                    return ActivationResult.Limit("لم تكمل أسبوعاً بعد. المتبقي: $days يوم و $hours ساعة")
                }
                onProgress("جاري طلب تفعيل 2 جيجا...")
                apiClient.activate2Gb(current.token, current.phone)
            }
            "FREE_3GB" -> {
                val elapsed = currentTime - current.lastActivation3Gb
                if (current.lastActivation3Gb > 0 && elapsed < COOLDOWN_3GB_MS) {
                    val remainingMs = COOLDOWN_3GB_MS - elapsed
                    val days = remainingMs / (1000 * 60 * 60 * 24)
                    val hours = (remainingMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                    return ActivationResult.Limit("لم يكمل الوقت المطلوب. المتبقي: $days يوم و $hours ساعة")
                }
                apiClient.activate3Gb(current.token, current.phone, onProgress)
            }
            else -> {
                onProgress("جاري تفعيل العرض ${offer.name}...")
                apiClient.activateOffer(current.token, current.phone, offer.code)
            }
        }

        // Process status & DB update
        when (result) {
            is ActivationResult.Success -> {
                when (offer.code) {
                    "FREE_1GB" -> userAccountDao.updateLastActivation1Gb(current.phone, currentTime)
                    "FREE_2GB" -> userAccountDao.updateLastActivation2Gb(current.phone, currentTime)
                    "FREE_3GB" -> userAccountDao.updateLastActivation3Gb(current.phone, currentTime)
                    else -> userAccountDao.updateLastActivationOffers(current.phone, currentTime)
                }
                activationHistoryDao.insertHistory(
                    ActivationHistoryEntity(
                        phone = current.displayPhone,
                        offerCode = offer.code,
                        offerName = offer.name,
                        status = "SUCCESS",
                        message = result.message
                    )
                )
            }
            is ActivationResult.Expired -> {
                userAccountDao.markTokenExpired(current.phone)
                activationHistoryDao.insertHistory(
                    ActivationHistoryEntity(
                        phone = current.displayPhone,
                        offerCode = offer.code,
                        offerName = offer.name,
                        status = "EXPIRED",
                        message = result.message
                    )
                )
            }
            is ActivationResult.Limit -> {
                activationHistoryDao.insertHistory(
                    ActivationHistoryEntity(
                        phone = current.displayPhone,
                        offerCode = offer.code,
                        offerName = offer.name,
                        status = "LIMIT",
                        message = result.message
                    )
                )
            }
            is ActivationResult.Failed -> {
                activationHistoryDao.insertHistory(
                    ActivationHistoryEntity(
                        phone = current.displayPhone,
                        offerCode = offer.code,
                        offerName = offer.name,
                        status = "FAILED",
                        message = result.message
                    )
                )
            }
        }

        return result
    }

    fun formatDisplayPhone(phone: String): String = apiClient.formatDisplayPhone(phone)
}
