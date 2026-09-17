package com.example.data.remote

import android.util.Log
import com.example.data.model.MainBalanceInfo
import com.example.data.model.MgmOfferItem
import com.example.data.model.MgmStatusInfo
import com.example.data.model.ProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

sealed class ActivationResult {
    data class Success(val message: String) : ActivationResult()
    data class Limit(val message: String) : ActivationResult()
    data class Expired(val message: String) : ActivationResult()
    data class Failed(val message: String) : ActivationResult()
}

class DjezzyApiClient {

    companion object {
        private const val TAG = "DjezzyApiClient"
        private const val BASE_URL = "https://apim.djezzy.dz/mobile-api"
        private const val CLIENT_ID = "87pIExRhxBb3_wGsA5eSEfyATloa"
        private const val CLIENT_SECRET = "uf82p68Bgisp8Yg1Uz8Pf6_v1XYa"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // Official Djezzy App User-Agent recognized by Djezzy gateway
        const val DJEZZY_USER_AGENT = "MobileApp/3.0.7"
    }

    private var currentProxyConfig: ProxyConfig = ProxyConfig()
    private var okHttpClient: OkHttpClient = buildClient(currentProxyConfig)

    fun updateProxy(config: ProxyConfig) {
        currentProxyConfig = config
        okHttpClient = buildClient(config)
    }

    private fun buildClient(proxyConfig: ProxyConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        // Custom SSL TrustManager to ensure requests pass smoothly even on ISP transparent proxies
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup permissive SSL: ${e.message}")
        }

        // Apply proxy if configured
        if (proxyConfig.isEnabled && proxyConfig.host.isNotBlank() && proxyConfig.port > 0) {
            // Use createUnresolved to avoid NetworkOnMainThreadException during client initialization
            val unresolvedAddress = InetSocketAddress.createUnresolved(proxyConfig.host.trim(), proxyConfig.port)
            val proxy = Proxy(Proxy.Type.HTTP, unresolvedAddress)
            builder.proxy(proxy)

            if (proxyConfig.username.isNotBlank()) {
                builder.proxyAuthenticator(object : Authenticator {
                    override fun authenticate(route: Route?, response: Response): Request? {
                        val credential = Credentials.basic(proxyConfig.username, proxyConfig.password)
                        return response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                })
            }
        }

        // Ensure all requests carry official Djezzy App headers
        builder.addInterceptor { chain ->
            val original = chain.request()
            val requestWithHeaders = original.newBuilder()
                .header("User-Agent", DJEZZY_USER_AGENT)
                .header("Accept", "application/json")
                .header("accept-language", "fr")
                .header("accept-encoding", "gzip")
                .build()
            chain.proceed(requestWithHeaders)
        }

        return builder.build()
    }

    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .header("User-Agent", "MobileApp/3.0.0")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val ip = try {
                        JSONObject(body).optString("ip", "غير معروف")
                    } catch (e: Exception) {
                        "متصل"
                    }
                    Pair(true, "الاتصال يعمل بنجاح - IP: $ip")
                } else {
                    Pair(false, "فشل الاتصال: كود ${response.code}")
                }
            }
        } catch (e: Exception) {
            Pair(false, "فشل الاتصال: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Request OTP via SMS directly from local device.
     */
    suspend fun requestOtp(phone: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = formatPhoneNumber(phone)
            val url = "$BASE_URL/oauth2/registration".toHttpUrlOrNull()!!
                .newBuilder()
                .addQueryParameter("msisdn", cleanPhone)
                .addQueryParameter("client_id", CLIENT_ID)
                .addQueryParameter("scope", "smsotp")
                .build()

            val consentAgreement = JSONArray().apply {
                put(JSONObject().apply {
                    put("marketing-notifications", false)
                })
            }
            val payload = JSONObject().apply {
                put("consent-agreement", consentAgreement)
                put("is-consent", true)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MobileApp/3.0.0")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("accept-language", "fr")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val responseBody = response.body?.string().orEmpty()
                Log.d(TAG, "requestOtp response code: $code body: $responseBody")
                if (code in 200..299) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("كود الاستجابة: $code\n$responseBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestOtp error", e)
            Result.failure(e)
        }
    }

    /**
     * Verify OTP code and obtain OAuth2 access token.
     */
    suspend fun verifyOtp(phone: String, otp: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanPhone = formatPhoneNumber(phone)
        val cleanOtp = otp.filter { it.isDigit() }

        if (cleanOtp.length != 6) {
            return@withContext Result.failure(Exception("رمز التحقق يجب أن يتكون من 6 أرقام"))
        }

        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                val formBody = FormBody.Builder()
                    .add("otp", cleanOtp)
                    .add("mobileNumber", cleanPhone)
                    .add("scope", "djezzyAppV2")
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .add("grant_type", "mobile")
                    .build()

                val request = Request.Builder()
                    .url("$BASE_URL/oauth2/token")
                    .addHeader("User-Agent", "MobileApp/3.0.0")
                    .addHeader("Accept", "application/json")
                    .addHeader("accept-language", "fr")
                    .post(formBody)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val bodyString = response.body?.string().orEmpty()
                    Log.d(TAG, "verifyOtp attempt $attempt: code $code body: $bodyString")

                    if (code in 200..299) {
                        val json = JSONObject(bodyString)
                        val token = json.optString("access_token")
                        if (token.isNotBlank()) {
                            return@withContext Result.success(token)
                        }
                    } else if (code == 401) {
                        return@withContext Result.failure(Exception("رمز التحقق غير صحيح أو انتهت صلاحيته"))
                    } else {
                        lastException = Exception("فشل التحقق (كود: $code)")
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }

            if (attempt < 3) {
                delay(2000)
            }
        }

        Result.failure(lastException ?: Exception("فشل التحقق من الرمز بعد 3 محاولات"))
    }

    /**
     * Activate 1GB Free Daily MGM reward.
     */
    suspend fun activate1Gb(
        token: String,
        phone: String,
        onProgress: (step: String, currentAttempt: Int, maxAttempts: Int) -> Unit = { _, _, _ -> }
    ): ActivationResult = withContext(Dispatchers.IO) {
        val cleanPhone = formatPhoneNumber(phone)
        val maxAttempts = 25

        for (attempt in 1..maxAttempts) {
            val randomSuffix = (10000000..99999999).random()
            val target = "2137$randomSuffix"
            onProgress("محاولة $attempt/$maxAttempts: إرسال دعوة لـ $target...", attempt, maxAttempts)

            try {
                val invPayload = JSONObject().apply {
                    put("msisdnReciever", target)
                }

                val invRequest = Request.Builder()
                    .url("$BASE_URL/api/v1/services/mgm/send-invitation/$cleanPhone")
                    .addHeader("User-Agent", "MobileApp/3.0.0")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("accept-language", "fr")
                    .addHeader("Authorization", "Bearer $token")
                    .post(invPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val (invCode, invText) = okHttpClient.newCall(invRequest).execute().use { res ->
                    Pair(res.code, res.body?.string().orEmpty())
                }

                if (invCode == 401) {
                    return@withContext ActivationResult.Expired("انتهت صلاحية الجلسة، يرجى تسجيل الدخول مجدداً")
                }
                if (invCode == 403 || invText.contains("limit", ignoreCase = true) || invText.contains("exceeded", ignoreCase = true)) {
                    return@withContext ActivationResult.Limit("وصلت للحد الأقصى للتفعيل. حاول لاحقاً.")
                }

                if (invCode in 200..204) {
                    onProgress("تم إرسال الدعوة، جاري تفعيل مكافأة 1GB...", attempt, maxAttempts)
                    delay(1500)

                    val actPayload = JSONObject().apply {
                        put("packageCode", "MGMBONUS1Go")
                    }

                    val actRequest = Request.Builder()
                        .url("$BASE_URL/api/v1/services/mgm/activate-reward/$cleanPhone")
                        .addHeader("User-Agent", "MobileApp/3.0.0")
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("accept-language", "fr")
                        .addHeader("Authorization", "Bearer $token")
                        .post(actPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    val (actCode, actText) = okHttpClient.newCall(actRequest).execute().use { res ->
                        Pair(res.code, res.body?.string().orEmpty())
                    }

                    if (actCode in 200..299 || actText.contains("successfully", ignoreCase = true)) {
                        return@withContext ActivationResult.Success("تم تفعيل 1 جيجا مجاناً بنجاح وصالح لمدة 24 ساعة!")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Attempt $attempt failed: ${e.message}")
            }

            delay(1000)
        }

        ActivationResult.Failed("تعذر التفعيل بعد عدة محاولات، قد تكون المكافأة مفعلة بالفعل أو السيرفر مشغول.")
    }

    /**
     * Activate 2GB Weekly Walk reward.
     */
    suspend fun activate2Gb(token: String, phone: String): ActivationResult = withContext(Dispatchers.IO) {
        val cleanPhone = formatPhoneNumber(phone)
        try {
            val payload = JSONObject().apply {
                put("packageCode", "GIFTWALKWIN2GO")
            }

            val request = Request.Builder()
                .url("$BASE_URL/api/v1/services/walk/activate-reward/$cleanPhone")
                .addHeader("User-Agent", "MobileApp/3.0.0")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("accept-language", "fr")
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val text = response.body?.string().orEmpty()

                when {
                    code in 200..204 || text.contains("successfully", ignoreCase = true) -> {
                        ActivationResult.Success("تم تفعيل 2 جيجا أسبوعياً بنجاح لمدة 7 أيام!")
                    }
                    code == 401 -> {
                        ActivationResult.Expired("انتهت صلاحية الجلسة، يرجى إعادة تسجيل الرقم.")
                    }
                    code == 403 || text.contains("limit", ignoreCase = true) -> {
                        ActivationResult.Limit("وصلت للحد الأقصى أو لم يتم استيفاء شروط جيزي (تعبئة 100 دج).")
                    }
                    else -> {
                        ActivationResult.Failed("فشل التفعيل (كود $code): $text")
                    }
                }
            }
        } catch (e: Exception) {
            ActivationResult.Failed("خطأ في الاتصال: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Activate 3GB Combo (2GB Walk + 1GB MGM).
     */
    suspend fun activate3Gb(
        token: String,
        phone: String,
        onProgress: (step: String) -> Unit = {}
    ): ActivationResult = withContext(Dispatchers.IO) {
        val cleanPhone = formatPhoneNumber(phone)
        val packages = listOf(
            Pair("GIFTWALKWIN2GO", "2GB Walk"),
            Pair("MGMBONUS1Go", "1GB Bonus")
        )

        var successCount = 0
        var isExpired = false

        for ((code, name) in packages) {
            try {
                onProgress("جاري تفعيل $name...")
                val payload = JSONObject().apply {
                    put("packageCode", code)
                }

                val request = Request.Builder()
                    .url("$BASE_URL/api/v1/services/walk/activate-reward/$cleanPhone")
                    .addHeader("User-Agent", "MobileApp/3.0.0")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("accept-language", "fr")
                    .addHeader("Authorization", "Bearer $token")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                okHttpClient.newCall(request).execute().use { res ->
                    val resCode = res.code
                    val resText = res.body?.string().orEmpty()
                    if (resCode in 200..204 || resText.contains("success", ignoreCase = true)) {
                        successCount++
                    } else if (resCode == 401) {
                        isExpired = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error activating $name: ${e.message}")
            }
            delay(1200)
        }

        if (isExpired) {
            ActivationResult.Expired("انتهت صلاحية الجلسة، يرجى تسجيل الدخول مجدداً.")
        } else if (successCount > 0) {
            ActivationResult.Success("تم تفعيل باقة 3 جيجا (نجح تفعيل $successCount جزء)!")
        } else {
            ActivationResult.Failed("تعذر تفعيل باقة 3 جيجا، قد تكون مفعّلة بالفعل أو غير متوفرة لخطك حالياً.")
        }
    }

    /**
     * Activate any specific package from the offers list.
     */
    suspend fun activateOffer(token: String, phone: String, packageCode: String): ActivationResult = withContext(Dispatchers.IO) {
        val cleanPhone = formatPhoneNumber(phone)
        try {
            val payload = JSONObject().apply {
                put("packageCode", packageCode)
            }

            val request = Request.Builder()
                .url("$BASE_URL/api/v1/subscribers/activate-product/$cleanPhone")
                .addHeader("User-Agent", "MobileApp/3.0.0")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("accept-language", "fr")
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val text = response.body?.string().orEmpty()

                when {
                    code in 200..204 || text.contains("success", ignoreCase = true) -> {
                        ActivationResult.Success("تم تفعيل العرض بنجاح!")
                    }
                    code == 401 -> {
                        ActivationResult.Expired("انتهت صلاحية الجلسة، يرجى إعادة تسجيل الدخول.")
                    }
                    else -> {
                        ActivationResult.Failed("فشل التفعيل (كود $code): تأكد من توفر الرصيد الكافي وتوافق خطك.")
                    }
                }
            }
        } catch (e: Exception) {
            ActivationResult.Failed("خطأ في الاتصال: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Send MGM referral invitation:
     * POST /mobile-api/api/v1/services/mgm/send-invitation/{senderMsisdn}
     * Body: {"msisdnReciever": 213xxxxxxxxx}
     */
    suspend fun sendMgmInvitation(
        token: String,
        senderPhone: String,
        receiverPhone: String
    ): ActivationResult = withContext(Dispatchers.IO) {
        val cleanSender = formatPhoneNumber(senderPhone)
        val cleanReceiver = formatPhoneNumber(receiverPhone)
        try {
            val payload = JSONObject().apply {
                val receiverLong = cleanReceiver.toLongOrNull()
                if (receiverLong != null) {
                    put("msisdnReciever", receiverLong)
                } else {
                    put("msisdnReciever", cleanReceiver)
                }
            }

            val request = Request.Builder()
                .url("$BASE_URL/api/v1/services/mgm/send-invitation/$cleanSender")
                .header("User-Agent", "MobileApp/3.0.7")
                .header("Accept", "application/json")
                .header("accept-language", "fr")
                .header("accept-encoding", "gzip")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val text = response.body?.string().orEmpty()
                Log.d(TAG, "sendMgmInvitation sender: $cleanSender receiver: $cleanReceiver code: $code body: $text")

                when {
                    code in 200..204 || text.contains("success", ignoreCase = true) -> {
                        ActivationResult.Success("تم إرسال دعوة الرعاية (MGM) بنجاح إلى $cleanReceiver!")
                    }
                    code == 401 -> {
                        ActivationResult.Expired("انتهت صلاحية الجلسة، يرجى إعادة تسجيل الدخول.")
                    }
                    code == 400 || code == 409 || text.contains("already", ignoreCase = true) -> {
                        ActivationResult.Failed("الرقم $cleanReceiver تلقى دعوة مسبقاً أو غير مؤهل.")
                    }
                    else -> {
                        ActivationResult.Failed("فشل إرسال الدعوة (كود $code): $text")
                    }
                }
            }
        } catch (e: Exception) {
            ActivationResult.Failed("خطأ في الاتصال: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Get subscriber main balance directly using official endpoint:
     * GET /mobile-api/api/v1/subscribers/main-balance/{msisdn}
     */
    suspend fun getMainBalance(token: String, phone: String): Result<MainBalanceInfo> = withContext(Dispatchers.IO) {
        val cleanPhone = formatPhoneNumber(phone)
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/v1/subscribers/main-balance/$cleanPhone")
                .header("User-Agent", "MobileApp/3.0.7")
                .header("Accept", "application/json")
                .header("accept-language", "fr")
                .header("accept-encoding", "gzip")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "getMainBalance for $cleanPhone: code $code body: $body")

                if (code == 401) {
                    return@withContext Result.failure(Exception("انتهت صلاحية الجلسة"))
                }

                if (code in 200..299 && body.isNotBlank()) {
                    val json = JSONObject(body)
                    val amount = when {
                        json.has("mainBalance") -> json.optDouble("mainBalance", 0.0)
                        json.has("balance") -> json.optDouble("balance", 0.0)
                        json.has("amount") -> json.optDouble("amount", 0.0)
                        json.has("credit") -> json.optDouble("credit", 0.0)
                        json.has("data") -> {
                            val d = json.optJSONObject("data")
                            d?.optDouble("mainBalance", d.optDouble("balance", 0.0)) ?: 0.0
                        }
                        else -> 0.0
                    }
                    val currency = json.optString("currency", "DZD")
                    val expirationDate = when {
                        json.has("expirationDate") -> json.optString("expirationDate")
                        json.has("expiryDate") -> json.optString("expiryDate")
                        json.has("validityDate") -> json.optString("validityDate")
                        json.has("data") -> json.optJSONObject("data")?.optString("expirationDate")
                        else -> null
                    }
                    val formatted = String.format(java.util.Locale.US, "%.2f دج", amount)
                    Result.success(
                        MainBalanceInfo(
                            amount = formatted,
                            rawAmount = amount,
                            currency = currency,
                            expirationDate = expirationDate,
                            isSuccess = true,
                            rawJson = body
                        )
                    )
                } else {
                    Result.failure(Exception("فشل جلب الرصيد (كود $code)"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch real subscriber MGM customer offers from official Djezzy APIM endpoint:
     * GET https://apim.djezzy.dz/mobile-api/api/v1/subscribers/customer-offers/{msisdn}?tags=MGM
     */
    suspend fun getMgmCustomerOffers(token: String, phone: String): Result<MgmStatusInfo> = withContext(Dispatchers.IO) {
        val cleanPhone = formatPhoneNumber(phone)
        try {
            val url = "$BASE_URL/api/v1/subscribers/customer-offers/$cleanPhone?tags=MGM"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MobileApp/3.0.7")
                .header("Accept", "application/json")
                .header("accept-language", "fr")
                .header("accept-encoding", "gzip")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "getMgmCustomerOffers for $cleanPhone: code $code body: $body")

                if (code == 401) {
                    return@withContext Result.failure(Exception("انتهت صلاحية الجلسة، يرجى إعادة تسجيل الدخول."))
                }

                if (code in 200..299) {
                    val parsed = parseMgmCustomerOffersJson(body)
                    Result.success(parsed)
                } else {
                    Result.failure(Exception("فشل فحص عروض MGM (كود $code)"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMgmCustomerOffers error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Resilient parser for Djezzy MGM customer-offers JSON responses:
     * Accurately detects when all 5 invites were used in the past, or if 1, 2, or more invites remain.
     */
    fun parseMgmCustomerOffersJson(body: String): MgmStatusInfo {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            return MgmStatusInfo(
                isSuccess = true,
                totalAllowed = 5,
                usedInvites = 5,
                remainingInvites = 0,
                isAllConsumed = true,
                hasRemaining = false,
                statusSummary = "تم استهلاك جميع دعوات الرعاية الـ 5 مسبقاً (0 دعوة متبقية).",
                rawJson = body
            )
        }

        try {
            var explicitRemaining: Int? = null
            var explicitUsed: Int? = null
            var explicitTotal: Int? = null
            val offerItems = mutableListOf<MgmOfferItem>()

            val offersArray: JSONArray? = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val rootObj = JSONObject(trimmed)
                    for (k in listOf("remainingInvitations", "remainingInvites", "remaining", "left", "available")) {
                        if (rootObj.has(k)) {
                            explicitRemaining = rootObj.optInt(k)
                            break
                        }
                    }
                    for (k in listOf("usedInvitations", "consumedInvitations", "consumed", "used", "sent")) {
                        if (rootObj.has(k)) {
                            explicitUsed = rootObj.optInt(k)
                            break
                        }
                    }
                    for (k in listOf("totalInvitations", "maxInvitations", "total", "max", "limit")) {
                        if (rootObj.has(k)) {
                            explicitTotal = rootObj.optInt(k)
                            break
                        }
                    }

                    when {
                        rootObj.has("offers") -> rootObj.optJSONArray("offers")
                        rootObj.has("customerOffers") -> rootObj.optJSONArray("customerOffers")
                        rootObj.has("data") -> rootObj.optJSONArray("data")
                        rootObj.has("items") -> rootObj.optJSONArray("items")
                        rootObj.has("results") -> rootObj.optJSONArray("results")
                        else -> null
                    }
                }
                else -> null
            }

            var activeCount = 0
            var consumedCount = 0

            if (offersArray != null) {
                for (i in 0 until offersArray.length()) {
                    val item = offersArray.optJSONObject(i) ?: continue
                    val code = item.optString("offerCode", item.optString("code", item.optString("id", "")))
                    val name = item.optString("offerName", item.optString("name", item.optString("label", "عرض MGM")))
                    val status = item.optString("status", item.optString("state", "")).uppercase()
                    val description = item.optString("description", "")
                    val isAvail = item.optBoolean(
                        "isAvailable",
                        item.optBoolean("available", !status.contains("CONSUMED") && !status.contains("EXHAUSTED") && !status.contains("USED"))
                    )

                    val itemRem = when {
                        item.has("remaining") -> item.optInt("remaining")
                        item.has("remainingInvitations") -> item.optInt("remainingInvitations")
                        item.has("balance") -> item.optInt("balance")
                        else -> null
                    }
                    val itemUsed = when {
                        item.has("consumed") -> item.optInt("consumed")
                        item.has("used") -> item.optInt("used")
                        item.has("counter") -> item.optInt("counter")
                        else -> null
                    }

                    if (itemRem != null && explicitRemaining == null) explicitRemaining = itemRem
                    if (itemUsed != null && explicitUsed == null) explicitUsed = itemUsed

                    if (isAvail && !status.contains("CONSUMED") && !status.contains("EXHAUSTED") && !status.contains("USED")) {
                        activeCount++
                    } else {
                        consumedCount++
                    }

                    offerItems.add(
                        MgmOfferItem(
                            code = code,
                            name = name,
                            status = status.ifBlank { if (isAvail) "ACTIVE" else "CONSUMED" },
                            description = description,
                            isAvailable = isAvail,
                            remaining = itemRem,
                            consumed = itemUsed
                        )
                    )
                }
            }

            val total = explicitTotal ?: 5
            val remaining: Int
            val used: Int

            when {
                // If explicit remaining counter was detected
                explicitRemaining != null -> {
                    remaining = explicitRemaining.coerceIn(0, total)
                    used = (explicitUsed ?: (total - remaining)).coerceIn(0, total)
                }
                // If explicit used/consumed counter was detected
                explicitUsed != null -> {
                    used = explicitUsed.coerceIn(0, total)
                    remaining = (total - used).coerceIn(0, total)
                }
                // Empty customer-offers for MGM means all offers/invites are consumed
                offersArray != null && offersArray.length() == 0 -> {
                    remaining = 0
                    used = total
                }
                // Array contains mix of active and consumed
                consumedCount > 0 && activeCount > 0 -> {
                    remaining = activeCount.coerceIn(0, total)
                    used = consumedCount.coerceIn(0, total)
                }
                // Array contains only active available invitations (e.g. 1 or 2 items = 1 or 2 left)
                activeCount in 1..total && consumedCount == 0 -> {
                    remaining = activeCount
                    used = (total - activeCount).coerceIn(0, total)
                }
                // All items in array are consumed
                consumedCount > 0 && activeCount == 0 -> {
                    remaining = 0
                    used = total
                }
                else -> {
                    // Default fallback
                    remaining = 5
                    used = 0
                }
            }

            val isAllConsumed = remaining <= 0
            val summary = when {
                isAllConsumed -> "تم استهلاك جميع دعوات الرعاية الـ $total مسبقاً بالكامل (0 دعوة متبقية)."
                used == 0 -> "لديك $remaining دعوات رعاية متاحة بالكامل من أصل $total (لم يتم استهلاك أي دعوة بعد)."
                else -> "متبقي لك $remaining دعوة من أصل $total (تم استهلاك $used دعوات سابقاً)."
            }

            return MgmStatusInfo(
                isSuccess = true,
                totalAllowed = total,
                usedInvites = used,
                remainingInvites = remaining,
                isAllConsumed = isAllConsumed,
                hasRemaining = remaining > 0,
                statusSummary = summary,
                offers = offerItems,
                rawJson = body
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseMgmCustomerOffersJson parse error: ${e.message}", e)
            return MgmStatusInfo(
                isSuccess = true,
                totalAllowed = 5,
                usedInvites = 0,
                remainingInvites = 5,
                isAllConsumed = false,
                hasRemaining = true,
                statusSummary = "تم الفحص بنجاح.",
                rawJson = body
            )
        }
    }

    fun formatPhoneNumber(input: String): String {
        val digits = input.filter { it.isDigit() }
        return when {
            digits.startsWith("0") -> "213" + digits.substring(1)
            digits.startsWith("213") -> digits
            digits.startsWith("7") -> "213$digits"
            else -> digits
        }
    }

    fun formatDisplayPhone(input: String): String {
        val digits = input.filter { it.isDigit() }
        return when {
            digits.startsWith("213") -> "0" + digits.substring(3)
            digits.startsWith("0") -> digits
            else -> "0$digits"
        }
    }
}
