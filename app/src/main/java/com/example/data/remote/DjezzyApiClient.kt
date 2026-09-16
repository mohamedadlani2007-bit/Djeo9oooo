package com.example.data.remote

import android.util.Log
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

        // Official Djezzy App User-Agent recognized by Djezzy gateway for free zero-rated access
        const val DJEZZY_USER_AGENT = "DjezzyApp/3.2.0 (Android; Mobile; ZeroRating)"
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
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyConfig.host, proxyConfig.port))
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

        // Ensure all requests carry official Djezzy App headers for zero-rating free network access
        builder.addInterceptor { chain ->
            val original = chain.request()
            val requestWithHeaders = original.newBuilder()
                .header("User-Agent", DJEZZY_USER_AGENT)
                .header("X-App-Version", "3.2.0")
                .header("X-Client-Platform", "Android")
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
