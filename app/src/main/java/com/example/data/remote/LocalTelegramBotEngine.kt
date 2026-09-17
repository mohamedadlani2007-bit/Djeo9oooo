package com.example.data.remote

import android.util.Log
import com.example.data.model.TelegramLogItem
import com.example.data.model.TelegramUserSession
import com.example.data.model.TelegramUserState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class LocalTelegramBotEngine(
    private val apiClient: DjezzyApiClient
) {
    companion object {
        private const val TAG = "TelegramBotEngine"
        private const val TELEGRAM_API_BASE = "https://api.telegram.org"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    // Direct HTTP client for Telegram API (never uses SOCKS/HTTP proxy intended for Djezzy)
    private val telegramHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var botJob: Job? = null
    private var currentToken: String = ""

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _botUsername = MutableStateFlow<String?>(null)
    val botUsername: StateFlow<String?> = _botUsername.asStateFlow()

    private val _botLogs = MutableStateFlow<List<TelegramLogItem>>(emptyList())
    val botLogs: StateFlow<List<TelegramLogItem>> = _botLogs.asStateFlow()

    private val _messagesCount = MutableStateFlow(0)
    val messagesCount: StateFlow<Int> = _messagesCount.asStateFlow()

    private val sessions = ConcurrentHashMap<Long, TelegramUserSession>()

    fun addLog(tag: String, message: String, isError: Boolean = false, isSuccess: Boolean = false) {
        val item = TelegramLogItem(
            tag = tag,
            message = message,
            isError = isError,
            isSuccess = isSuccess
        )
        _botLogs.update { current ->
            (listOf(item) + current).take(150)
        }
        if (isError) {
            Log.e(TAG, "[$tag] $message")
        } else {
            Log.d(TAG, "[$tag] $message")
        }
    }

    fun clearLogs() {
        _botLogs.value = emptyList()
    }

    /**
     * Test token validity and fetch Bot Info from Telegram.
     */
    suspend fun testToken(token: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(Exception("يرجى إدخال التوكن أولاً"))
        }

        try {
            val url = "$TELEGRAM_API_BASE/bot$cleanToken/getMe"
            val request = Request.Builder().url(url).get().build()
            telegramHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (json.optBoolean("ok", false)) {
                    val resultObj = json.getJSONObject("result")
                    val username = resultObj.optString("username", "")
                    val firstName = resultObj.optString("first_name", "Bot")
                    Result.success("@$username ($firstName)")
                } else {
                    val desc = json.optString("description", "توكن غير صالح")
                    Result.failure(Exception(desc))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Start the local Telegram Bot polling loop.
     */
    fun start(token: String, scope: CoroutineScope) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            addLog("خطأ", "التوكن فارغ، لا يمكن بدء البوت", isError = true)
            return
        }

        if (_isRunning.value) {
            stop()
        }

        currentToken = cleanToken
        _isRunning.value = true
        addLog("تشغيل", "جاري التحقق من التوكن وبدء الخدمة المحلية...")

        botJob = scope.launch(Dispatchers.IO) {
            try {
                // Verify Token First
                val testRes = testToken(cleanToken)
                if (testRes.isFailure) {
                    val err = testRes.exceptionOrNull()?.message ?: "خطأ غير معروف"
                    addLog("خطأ", "فشل التحقق من التوكن: $err", isError = true)
                    _isRunning.value = false
                    return@launch
                }

                val info = testRes.getOrNull().orEmpty()
                val uname = if (info.contains("@")) info.substringAfter("@").substringBefore(" ") else null
                _botUsername.value = uname
                addLog("نجاح", "🟢 البوت متصل بنجاح: $info", isSuccess = true)
                addLog("معلومات", "يعمل البوت محلياً على هذا الجهاز عبر شبكة جيزي مباشرة وبدون بروكسي.")

                // Polling Loop
                var lastUpdateId = 0L
                while (isActive && _isRunning.value) {
                    try {
                        val updatesUrl = "$TELEGRAM_API_BASE/bot$cleanToken/getUpdates?offset=$lastUpdateId&timeout=20"
                        val request = Request.Builder().url(updatesUrl).get().build()

                        val response = telegramHttpClient.newCall(request).execute()
                        val body = response.body?.string().orEmpty()
                        response.close()

                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            if (json.optBoolean("ok", false)) {
                                val resultArray = json.optJSONArray("result")
                                if (resultArray != null && resultArray.length() > 0) {
                                    for (i in 0 until resultArray.length()) {
                                        val updateObj = resultArray.getJSONObject(i)
                                        val updateId = updateObj.optLong("update_id", 0L)
                                        if (updateId >= lastUpdateId) {
                                            lastUpdateId = updateId + 1
                                        }

                                        val messageObj = updateObj.optJSONObject("message")
                                        if (messageObj != null) {
                                            handleIncomingMessage(cleanToken, messageObj)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Polling tick error: ${e.message}")
                        delay(2500)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    addLog("توقف", "توقف البوت: ${e.localizedMessage ?: e.message}", isError = true)
                }
            } finally {
                _isRunning.value = false
                _botUsername.value = null
                addLog("توقف", "تم إيقاف تشغيل البوت المحلي.")
            }
        }
    }

    /**
     * Stop the local Telegram Bot.
     */
    fun stop() {
        _isRunning.value = false
        botJob?.cancel()
        botJob = null
        _botUsername.value = null
    }

    /**
     * Handle incoming Telegram message.
     */
    private suspend fun handleIncomingMessage(token: String, messageObj: JSONObject) {
        val chatId = messageObj.optJSONObject("chat")?.optLong("id", 0L) ?: return
        val fromObj = messageObj.optJSONObject("from")
        val senderUsername = fromObj?.optString("username", "").orEmpty()
        val senderFirstName = fromObj?.optString("first_name", "مستخدم").orEmpty()
        val text = messageObj.optString("text", "").trim()

        if (text.isBlank()) return

        _messagesCount.update { it + 1 }
        addLog("رسالة", "👤 من @$senderUsername ($senderFirstName): $text")

        val session = sessions.getOrPut(chatId) {
            TelegramUserSession(chatId = chatId, username = senderUsername, firstName = senderFirstName)
        }
        session.lastActivity = System.currentTimeMillis()

        // Commands
        if (text == "/start" || text == "/help" || text.equals("start", ignoreCase = true)) {
            session.state = TelegramUserState.IDLE
            sendWelcomeMessage(token, chatId, senderFirstName)
            return
        }

        when (session.state) {
            TelegramUserState.IDLE -> {
                // Check if user sent a phone number
                val cleanDigits = text.filter { it.isDigit() }
                if (cleanDigits.length in 9..12 && (cleanDigits.startsWith("07") || cleanDigits.startsWith("7") || cleanDigits.startsWith("2137"))) {
                    processPhoneInput(token, chatId, session, cleanDigits)
                } else {
                    sendMessage(
                        token = token,
                        chatId = chatId,
                        text = "📱 مرحباً $senderFirstName! يرجى إرسال رقم هاتف جيزي الخاص بك للبدء:\n\nمثال: `0770123456` أو `213770123456`",
                        replyMarkup = null
                    )
                }
            }

            TelegramUserState.WAITING_OTP -> {
                val cleanOtp = text.filter { it.isDigit() }
                if (cleanOtp.length == 6) {
                    processOtpInput(token, chatId, session, cleanOtp)
                } else if (text.equals("إلغاء", ignoreCase = true) || text == "/cancel") {
                    session.state = TelegramUserState.IDLE
                    sendMessage(token, chatId, "تم إلغاء العملية. أرسل رقم هاتفك في أي وقت للبدء من جديد.")
                } else {
                    sendMessage(
                        token = token,
                        chatId = chatId,
                        text = "⚠️ رمز التحقق (OTP) يجب أن يتكون من 6 أرقام.\nيرجى كتابة الرمز الذي وصلك في رسالة SMS، أو أرسل /start للإلغاء."
                    )
                }
            }

            TelegramUserState.LOGGED_IN -> {
                handleLoggedInAction(token, chatId, session, text)
            }

            TelegramUserState.WAITING_MGM_RECEIVER -> {
                processMgmReceiverInput(token, chatId, session, text)
            }
        }
    }

    private suspend fun processPhoneInput(
        token: String,
        chatId: Long,
        session: TelegramUserSession,
        rawPhone: String
    ) {
        val formatted = apiClient.formatPhoneNumber(rawPhone)
        val displayPhone = apiClient.formatDisplayPhone(rawPhone)
        session.pendingPhone = formatted

        addLog("طلب رمز", "📩 إرسال كود OTP للرقم $displayPhone")
        sendMessage(token, chatId, "⏳ جاري إرسال رمز التحقق (OTP) إلى الرقم $displayPhone عبر شبكة جيزي مباشرة...")

        val reqResult = apiClient.requestOtp(formatted)
        if (reqResult.isSuccess) {
            session.state = TelegramUserState.WAITING_OTP
            addLog("نجاح", "✅ تم إرسال OTP للرقم $displayPhone بنجاح", isSuccess = true)
            sendMessage(
                token = token,
                chatId = chatId,
                text = "✅ تم إرسال رمز التحقق في رسالة SMS إلى هاتفك: *$displayPhone*\n\n🔢 يرجى إرسال رمز التحقق المكون من 6 أرقام الآن:",
                replyMarkup = null
            )
        } else {
            val err = reqResult.exceptionOrNull()?.message ?: "خطأ في الاتصال"
            addLog("خطأ", "❌ فشل إرسال OTP للرقم $displayPhone: $err", isError = true)
            sendMessage(
                token = token,
                chatId = chatId,
                text = "❌ تعذر إرسال رمز التحقق للرقم $displayPhone.\nالسبب: $err\n\nتأكد من أن الرقم صحيح ويتبع لشبكة جيزي، ثم حاول مجدداً."
            )
        }
    }

    private suspend fun processOtpInput(
        token: String,
        chatId: Long,
        session: TelegramUserSession,
        otp: String
    ) {
        val phone = session.pendingPhone
        val displayPhone = apiClient.formatDisplayPhone(phone)

        addLog("تحقق", "🔑 التحقق من رمز OTP للرقم $displayPhone")
        sendMessage(token, chatId, "⏳ جاري التحقق من الرمز واستخراج التوكن...")

        val verifyResult = apiClient.verifyOtp(phone, otp)
        if (verifyResult.isSuccess) {
            val djezzyToken = verifyResult.getOrThrow()
            session.activePhone = phone
            session.activeToken = djezzyToken
            session.state = TelegramUserState.LOGGED_IN
            addLog("تسجيل", "🎉 تم تسجيل الدخول بنجاح للرقم $displayPhone", isSuccess = true)

            val successMsg = "🎉 *تم تسجيل الدخول بنجاح!* 🎉\n" +
                    "📱 الرقم: `$displayPhone`\n" +
                    "⚡ الاتصال: محلي مباشر عبر جهازك\n\n" +
                    "👇 اختر ما ترغب به من القائمة بالأسفل:"

            sendMessage(token, chatId, successMsg, getMainMenuMarkup())
        } else {
            val err = verifyResult.exceptionOrNull()?.message ?: "رمز غير صحيح"
            addLog("خطأ", "❌ فشل التحقق للرقم $displayPhone: $err", isError = true)
            sendMessage(
                token = token,
                chatId = chatId,
                text = "❌ رمز التحقق غير صحيح أو انتهت صلاحيته!\nيرجى إعادة إدخال الرمز الصحيح، أو أرسل /start للبدء من جديد."
            )
        }
    }

    private suspend fun handleLoggedInAction(
        token: String,
        chatId: Long,
        session: TelegramUserSession,
        text: String
    ) {
        val phone = session.activePhone
        val djezzyToken = session.activeToken
        val displayPhone = apiClient.formatDisplayPhone(phone)

        when {
            text.contains("1 جيجا") || text.contains("1GB") -> {
                addLog("تفعيل", "🎁 طلب 1GB مجاناً للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ جاري تفعيل باقة 1 جيجا مجاناً (MGM) للرقم $displayPhone...\nقد تستغرق العملية بضع ثوانٍ...")

                val result = apiClient.activate1Gb(djezzyToken, phone)
                val reply = when (result) {
                    is ActivationResult.Success -> "✅ *${result.message}*"
                    is ActivationResult.Limit -> "⚠️ *${result.message}*"
                    is ActivationResult.Expired -> {
                        session.state = TelegramUserState.IDLE
                        "⌛ انتهت صلاحية الجلسة، يرجى إرسال رقم الهاتف لتسجيل الدخول مجدداً."
                    }
                    is ActivationResult.Failed -> "❌ *${result.message}*"
                }
                addLog("نتيجة", "1GB -> $reply", isSuccess = result is ActivationResult.Success, isError = result is ActivationResult.Failed)
                sendMessage(token, chatId, reply, getMainMenuMarkup())
            }

            text.contains("2 جيجا") || text.contains("مشي") || text.contains("Walk") -> {
                addLog("تفعيل", "🚶 طلب 2GB مشي للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ جاري تفعيل باقة 2 جيجا أسبوعياً (Walk & Win) للرقم $displayPhone...")

                val result = apiClient.activate2Gb(djezzyToken, phone)
                val reply = when (result) {
                    is ActivationResult.Success -> "✅ *${result.message}*"
                    is ActivationResult.Limit -> "⚠️ *${result.message}*"
                    is ActivationResult.Expired -> {
                        session.state = TelegramUserState.IDLE
                        "⌛ انتهت صلاحية الجلسة، يرجى إعادة تسجيل الدخول."
                    }
                    is ActivationResult.Failed -> "❌ *${result.message}*"
                }
                addLog("نتيجة", "2GB -> $reply", isSuccess = result is ActivationResult.Success, isError = result is ActivationResult.Failed)
                sendMessage(token, chatId, reply, getMainMenuMarkup())
            }

            text.contains("3 جيجا") || text.contains("3GB") -> {
                addLog("تفعيل", "⚡ طلب باقة 3 جيجا للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ جاري تفعيل باقة 3 جيجا (1GB + 2GB) للرقم $displayPhone...")

                val result = apiClient.activate3Gb(djezzyToken, phone)
                val reply = when (result) {
                    is ActivationResult.Success -> "✅ *${result.message}*"
                    is ActivationResult.Limit -> "⚠️ *${result.message}*"
                    is ActivationResult.Expired -> {
                        session.state = TelegramUserState.IDLE
                        "⌛ انتهت صلاحية الجلسة، يرجى إعادة تسجيل الدخول."
                    }
                    is ActivationResult.Failed -> "❌ *${result.message}*"
                }
                addLog("نتيجة", "3GB -> $reply", isSuccess = result is ActivationResult.Success, isError = result is ActivationResult.Failed)
                sendMessage(token, chatId, reply, getMainMenuMarkup())
            }

            text.contains("دعوة") || text.contains("MGM") || text.contains("رعاية") -> {
                session.state = TelegramUserState.WAITING_MGM_RECEIVER
                val prompt = "💌 *إرسال دعوة رعاية (MGM)*\n\n" +
                        "يرجى إرسال رقم هاتف جيزي للشخص الذي ترغب بإرسال الدعوة إليه:\n" +
                        "مثال: `0773527865` أو `213773527865`\n\n" +
                        "أو أرسل كلمة 'رجوع' للعودة للقائمة."
                sendMessage(token, chatId, prompt, getCancelMarkup())
            }

            text.contains("الرصيد") || text.contains("رصيد") || text.contains("Solde") -> {
                addLog("رصيد", "💰 استعلام عن الرصيد للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ جاري فحص الرصيد من سيرفر جيزي...")

                val balanceResult = apiClient.getMainBalance(djezzyToken, phone)
                if (balanceResult.isSuccess) {
                    val info = balanceResult.getOrThrow()
                    val validityStr = if (info.expirationDate != null) "\n📅 *تاريخ الصلاحية:* ${info.expirationDate}" else ""
                    val reply = "💰 *تفاصيل الرصيد الأساسي:*\n" +
                            "📱 الرقم: `$displayPhone`\n" +
                            "💵 الرصيد: *${info.amount}*$validityStr"
                    sendMessage(token, chatId, reply, getMainMenuMarkup())
                } else {
                    val err = balanceResult.exceptionOrNull()?.message ?: "فشل فحص الرصيد"
                    sendMessage(token, chatId, "❌ $err", getMainMenuMarkup())
                }
            }

            text.contains("خروج") || text.contains("تسجيل خروج") || text.contains("رقم جديد") -> {
                session.state = TelegramUserState.IDLE
                session.activePhone = ""
                session.activeToken = ""
                addLog("خروج", "🚪 تسجيل خروج للرقم $displayPhone")
                sendMessage(
                    token = token,
                    chatId = chatId,
                    text = "🚪 تم تسجيل الخروج بنجاح.\n\nأرسل رقم هاتف جيزي جديد للبدء من جديد:",
                    replyMarkup = null
                )
            }

            else -> {
                val help = "❓ لم أفهم اختيارك. يرجى الضغط على أحد الأزرار في القائمة بالأسفل:"
                sendMessage(token, chatId, help, getMainMenuMarkup())
            }
        }
    }

    private suspend fun processMgmReceiverInput(
        token: String,
        chatId: Long,
        session: TelegramUserSession,
        receiverText: String
    ) {
        if (receiverText.contains("رجوع") || receiverText.contains("إلغاء") || receiverText == "/cancel") {
            session.state = TelegramUserState.LOGGED_IN
            sendMessage(token, chatId, "تم الرجوع إلى القائمة الرئيسية.", getMainMenuMarkup())
            return
        }

        val cleanDigits = receiverText.filter { it.isDigit() }
        if (cleanDigits.length !in 9..12) {
            sendMessage(
                token = token,
                chatId = chatId,
                text = "⚠️ رقم هاتف غير صالح. يرجى كتابة رقم جيزي صالح (مثال: `0773527865`) أو أرسل 'رجوع':"
            )
            return
        }

        val senderPhone = session.activePhone
        val djezzyToken = session.activeToken
        val displayReceiver = apiClient.formatDisplayPhone(cleanDigits)

        addLog("دعوة", "💌 إرسال دعوة MGM من ${apiClient.formatDisplayPhone(senderPhone)} إلى $displayReceiver")
        sendMessage(token, chatId, "⏳ جاري إرسال دعوة الرعاية (MGM) إلى $displayReceiver...")

        val result = apiClient.sendMgmInvitation(
            token = djezzyToken,
            senderPhone = senderPhone,
            receiverPhone = cleanDigits
        )

        session.state = TelegramUserState.LOGGED_IN

        val reply = when (result) {
            is ActivationResult.Success -> "🎉 *${result.message}*\nتم تسجيل الدعوة بنجاح في نظام جيزي!"
            is ActivationResult.Limit -> "⚠️ *${result.message}*"
            is ActivationResult.Expired -> {
                session.state = TelegramUserState.IDLE
                "⌛ انتهت صلاحية الجلسة، يرجى تسجيل الدخول مجدداً."
            }
            is ActivationResult.Failed -> "❌ *${result.message}*"
        }

        addLog("دعوة", "نتيجة الدعوة: $reply", isSuccess = result is ActivationResult.Success, isError = result is ActivationResult.Failed)
        sendMessage(token, chatId, reply, getMainMenuMarkup())
    }

    private suspend fun sendWelcomeMessage(token: String, chatId: Long, firstName: String) {
        val welcome = "👋 مرحباً بك يا *$firstName* في بوت تفعيل خدمات جيزي!\n\n" +
                "⚡ *المميزات:*\n" +
                "• 🎁 تفعيل 1 جيجا إنترنت مجاناً (MGM)\n" +
                "• 🚶 تفعيل 2 جيجا مكافأة المشي (Walk & Win)\n" +
                "• ⚡ تفعيل باقة 3 جيجا (2GB + 1GB)\n" +
                "• 💌 إرسال دعوة رعاية (MGM Send Invitation)\n" +
                "• 💰 الاستعلام عن الرصيد والصلاحية فورياً\n\n" +
                "🔒 يعمل هذا البوت محلياً ومباشرة عبر جهازك وبدون أي وسيط أو بروكسي.\n\n" +
                "📱 *للبدء، يرجى إرسال رقم هاتف جيزي الخاص بك:*\n" +
                "مثال: `0770123456` أو `213770123456`"

        sendMessage(token, chatId, welcome, null)
    }

    /**
     * Send message to a Telegram chat with markdown formatting and optional ReplyKeyboardMarkup.
     */
    private suspend fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        replyMarkup: JSONObject? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val url = "$TELEGRAM_API_BASE/bot$token/sendMessage"
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", "Markdown")
                if (replyMarkup != null) {
                    put("reply_markup", replyMarkup)
                }
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            telegramHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "sendMessage failed with code ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending telegram message: ${e.message}")
        }
    }

    private fun getMainMenuMarkup(): JSONObject {
        val keyboard = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "🎁 تفعيل 1 جيجا") })
                put(JSONObject().apply { put("text", "🚶 تفعيل 2 جيجا مشي") })
            })
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "⚡ تفعيل باقة 3 جيجا") })
                put(JSONObject().apply { put("text", "💌 إرسال دعوة MGM") })
            })
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "💰 استعلام عن الرصيد") })
                put(JSONObject().apply { put("text", "🚪 خروج / رقم جديد") })
            })
        }

        return JSONObject().apply {
            put("keyboard", keyboard)
            put("resize_keyboard", true)
            put("one_time_keyboard", false)
        }
    }

    private fun getCancelMarkup(): JSONObject {
        val keyboard = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "🔙 رجوع للقائمة") })
            })
        }
        return JSONObject().apply {
            put("keyboard", keyboard)
            put("resize_keyboard", true)
            put("one_time_keyboard", true)
        }
    }
}
