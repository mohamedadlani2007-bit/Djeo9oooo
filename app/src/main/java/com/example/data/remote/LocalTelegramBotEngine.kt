package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.TelegramSessionStore
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Local Telegram Bot Engine running directly on the user's Android phone.
 * - Works directly over the local network without proxy.
 * - Persists sessions across app restarts using TelegramSessionStore.
 * - Supports ReplyKeyboards & InlineKeyboards with callback queries.
 * - Formatted with clear Algerian/Arabic responses and informative status cards.
 */
class LocalTelegramBotEngine private constructor(
    context: Context,
    private val apiClient: DjezzyApiClient
) {
    companion object {
        private const val TAG = "TelegramBotEngine"
        private const val TELEGRAM_API_BASE = "https://api.telegram.org"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var instance: LocalTelegramBotEngine? = null

        fun getInstance(context: Context, apiClient: DjezzyApiClient = DjezzyApiClient()): LocalTelegramBotEngine {
            return instance ?: synchronized(this) {
                instance ?: LocalTelegramBotEngine(
                    context = context.applicationContext,
                    apiClient = apiClient
                ).also { instance = it }
            }
        }
    }

    private val sessionStore = TelegramSessionStore(context)

    // Direct HTTP client for Telegram API
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

    // In-memory sessions synchronized with persistent disk storage
    private val sessions = ConcurrentHashMap<Long, TelegramUserSession>()

    init {
        // Load saved sessions from permanent disk storage
        try {
            val loaded = sessionStore.getAllSessions()
            sessions.putAll(loaded)
            if (loaded.isNotEmpty()) {
                addLog("جلسات", "تم استرجاع ${loaded.size} جلسة مستخدم محفوظة من الذاكرة المحلية.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading saved sessions: ${e.message}")
        }
    }

    private fun getFormattedTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun addLog(tag: String, message: String, isError: Boolean = false, isSuccess: Boolean = false) {
        val item = TelegramLogItem(
            tag = tag,
            message = message,
            isError = isError,
            isSuccess = isSuccess
        )
        _botLogs.update { current ->
            (listOf(item) + current).take(200)
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

    private fun saveOrUpdateSession(session: TelegramUserSession) {
        sessions[session.chatId] = session
        sessionStore.saveSession(session)
    }

    private fun deleteSession(chatId: Long) {
        sessions.remove(chatId)
        sessionStore.removeSession(chatId)
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
        addLog("تشغيل", "جاري التحقق من التوكن وتشغيل البوت في الخلفية...")

        botJob = scope.launch(Dispatchers.IO) {
            try {
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
                addLog("نجاح", "🟢 البوت متصل وشغال بنجاح في الخلفية: $info", isSuccess = true)
                addLog("معلومات", "يعمل محلياً بالكامل ويحفظ جلسات المستخدمين بدون انقطاع.")

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

                                        // 1. Check for standard message
                                        val messageObj = updateObj.optJSONObject("message")
                                        if (messageObj != null) {
                                            handleIncomingMessage(cleanToken, messageObj)
                                        }

                                        // 2. Check for interactive callback_query (inline button clicks)
                                        val callbackQueryObj = updateObj.optJSONObject("callback_query")
                                        if (callbackQueryObj != null) {
                                            handleCallbackQuery(cleanToken, callbackQueryObj)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Polling tick exception: ${e.message}")
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
     * Handle regular incoming Telegram messages.
     */
    private suspend fun handleIncomingMessage(token: String, messageObj: JSONObject) {
        val chatId = messageObj.optJSONObject("chat")?.optLong("id", 0L) ?: return
        val fromObj = messageObj.optJSONObject("from")
        val senderUsername = fromObj?.optString("username", "").orEmpty()
        val senderFirstName = fromObj?.optString("first_name", "مستخدم").orEmpty()
        val text = messageObj.optString("text", "").trim()

        if (text.isBlank()) return

        _messagesCount.update { it + 1 }
        addLog("رسالة", "👤 @$senderUsername ($senderFirstName): $text")

        // Retrieve or initialize session
        val session = sessions.getOrPut(chatId) {
            TelegramUserSession(
                chatId = chatId,
                username = senderUsername,
                firstName = senderFirstName
            )
        }
        session.lastActivity = System.currentTimeMillis()

        // Commands: /start, /help
        if (text == "/start" || text == "/help" || text.equals("start", ignoreCase = true)) {
            if (session.state == TelegramUserState.LOGGED_IN && session.activeToken.isNotBlank()) {
                val displayPhone = apiClient.formatDisplayPhone(session.activePhone)
                val welcomeBack = "👋 أهلاً بك مجدداً يا *$senderFirstName*!\n" +
                        "───────────────────\n" +
                        "📱 *حسابك النشط:* `$displayPhone`\n" +
                        "💾 *حالة الجلسة:* ✅ محفوظة ونشطة محلياً\n" +
                        "⚡ *الاتصال:* مباشر عبر شبكة الهاتف (بدون بروكسي)\n\n" +
                        "👇 اختر ما ترغب به من القائمة أدناه:"
                sendMessage(token, chatId, welcomeBack, getMainMenuMarkup())
                return
            } else {
                session.state = TelegramUserState.IDLE
                saveOrUpdateSession(session)
                sendWelcomeMessage(token, chatId, senderFirstName)
                return
            }
        }

        // Global Command: /cancel
        if (text == "/cancel" || text == "رجوع" || text == "إلغاء" || text.contains("رجوع للقائمة")) {
            if (session.state == TelegramUserState.WAITING_MGM_RECEIVER) {
                session.state = TelegramUserState.LOGGED_IN
                saveOrUpdateSession(session)
                sendMessage(token, chatId, "🔙 تم الرجوع إلى القائمة الرئيسية.", getMainMenuMarkup())
                return
            } else if (session.state == TelegramUserState.WAITING_OTP) {
                session.state = TelegramUserState.IDLE
                saveOrUpdateSession(session)
                sendMessage(token, chatId, "🔙 تم إلغاء عملية تسجيل الدخول. أرسل رقم هاتفك في أي وقت للبدء.", null)
                return
            }
        }

        when (session.state) {
            TelegramUserState.IDLE -> {
                // If user is already logged in but sends a text
                if (session.activeToken.isNotBlank()) {
                    session.state = TelegramUserState.LOGGED_IN
                    saveOrUpdateSession(session)
                    handleLoggedInAction(token, chatId, session, text)
                    return
                }

                // Check if user entered phone number
                val cleanDigits = text.filter { it.isDigit() }
                if (cleanDigits.length in 9..12 && (cleanDigits.startsWith("07") || cleanDigits.startsWith("7") || cleanDigits.startsWith("2137"))) {
                    processPhoneInput(token, chatId, session, cleanDigits)
                } else {
                    sendMessage(
                        token = token,
                        chatId = chatId,
                        text = "👋 مرحباً *$senderFirstName*!\n\n📱 يرجى إرسال رقم هاتف جيزي الخاص بك للبدء وتفعيل العروض:\n\nمثال: `0770123456` أو `0773527865`",
                        replyMarkup = null
                    )
                }
            }

            TelegramUserState.WAITING_OTP -> {
                val cleanOtp = text.filter { it.isDigit() }
                if (cleanOtp.length == 6) {
                    processOtpInput(token, chatId, session, cleanOtp)
                } else {
                    sendMessage(
                        token = token,
                        chatId = chatId,
                        text = "⚠️ *رمز التحقق غير صحيح.*\n\n🔢 رمز OTP يجب أن يتكون من 6 أرقام وصلتك في رسالة SMS.\nيرجى كتابته بدقة أو أرسل /cancel للرجوع."
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

    /**
     * Handle Interactive Inline Buttons (Callback Queries).
     */
    private suspend fun handleCallbackQuery(token: String, callbackQuery: JSONObject) {
        val queryId = callbackQuery.optString("id", "")
        val data = callbackQuery.optString("data", "")
        val fromObj = callbackQuery.optJSONObject("from")
        val chatId = callbackQuery.optJSONObject("message")?.optJSONObject("chat")?.optLong("id", 0L)
            ?: fromObj?.optLong("id", 0L)
            ?: return

        val session = sessions.getOrPut(chatId) {
            sessionStore.getSession(chatId) ?: TelegramUserSession(chatId = chatId)
        }

        // Acknowledge callback immediately to dismiss Telegram client spinner
        answerCallbackQuery(token, queryId, "جاري المعالجة...")

        when (data) {
            "act_1gb" -> handleLoggedInAction(token, chatId, session, "1 جيجا")
            "act_2gb" -> handleLoggedInAction(token, chatId, session, "2 جيجا")
            "act_3gb" -> handleLoggedInAction(token, chatId, session, "3 جيجا")
            "act_balance" -> handleLoggedInAction(token, chatId, session, "الرصيد")
            "act_mgm" -> handleLoggedInAction(token, chatId, session, "دعوة")
            "act_account" -> handleLoggedInAction(token, chatId, session, "حسابي")
            "act_logout" -> handleLoggedInAction(token, chatId, session, "خروج")
            "act_menu" -> {
                sendMessage(token, chatId, "📋 *القائمة الرئيسية للعروض والخدمات:*", getMainMenuMarkup())
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
        sendMessage(token, chatId, "⏳ *جاري إرسال رمز التحقق (OTP) إلى الرقم $displayPhone عبر شبكة جيزي مباشرة...*")

        val reqResult = apiClient.requestOtp(formatted)
        if (reqResult.isSuccess) {
            session.state = TelegramUserState.WAITING_OTP
            saveOrUpdateSession(session)
            addLog("نجاح", "✅ تم إرسال OTP للرقم $displayPhone بنجاح", isSuccess = true)

            val otpPrompt = "📩 *تم إرسال رمز التحقق (OTP)*\n" +
                    "───────────────────\n" +
                    "📱 *الرقم:* `$displayPhone`\n" +
                    "📬 تفقد رسائل SMS على هاتفك الآن.\n\n" +
                    "🔢 *اكتب رمز التحقق المكون من 6 أرقام هنا:*\n\n" +
                    "*(أو أرسل /cancel للإلغاء)*"

            sendMessage(token, chatId, otpPrompt, null)
        } else {
            val err = reqResult.exceptionOrNull()?.message ?: "خطأ في الاتصال بالشبكة"
            addLog("خطأ", "❌ فشل إرسال OTP للرقم $displayPhone: $err", isError = true)
            sendMessage(
                token = token,
                chatId = chatId,
                text = "❌ *تعذر إرسال رمز التحقق للرقم $displayPhone.*\n\nالسبب: $err\n\nتأكد من أن الرقم صحيح ويتبع لشبكة جيزي، ثم أرسل الرقم مجدداً."
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
        sendMessage(token, chatId, "⏳ *جاري التحقق من الرمز واستخراج مفتاح التفعيل...*")

        val verifyResult = apiClient.verifyOtp(phone, otp)
        if (verifyResult.isSuccess) {
            val djezzyToken = verifyResult.getOrThrow()
            session.activePhone = phone
            session.activeToken = djezzyToken
            session.state = TelegramUserState.LOGGED_IN
            saveOrUpdateSession(session)
            addLog("تسجيل", "🎉 تم تسجيل الدخول بنجاح وحفظ الجلسة للرقم $displayPhone", isSuccess = true)

            val successMsg = "🎉 *تم تسجيل الدخول بنجاح!* 🎉\n" +
                    "───────────────────\n" +
                    "📱 *الرقم النشط:* `$displayPhone`\n" +
                    "💾 *حفظ الجلسة:* ✅ تم حفظ جلستك (لن تحتاج لتسجيل الدخول مجدداً)\n" +
                    "⚡ *طريقة العمل:* محلي عبر هاتفك وبدون بروكسي\n\n" +
                    "👇 *اختر ما ترغب به من الأزرار التفاعلية بالأسفل:*"

            sendMessage(token, chatId, successMsg, getMainMenuMarkup())
        } else {
            val err = verifyResult.exceptionOrNull()?.message ?: "رمز غير صحيح أو منتهي الصلاحية"
            addLog("خطأ", "❌ فشل التحقق للرقم $displayPhone: $err", isError = true)
            sendMessage(
                token = token,
                chatId = chatId,
                text = "❌ *رمز التحقق غير صحيح أو انتهت صلاحيته!*\n\nيرجى إعادة كتابة الرمز الصحيح، أو أرسل /start للمحاولة برقم آخر."
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
        val timeNow = getFormattedTime()

        when {
            text.contains("1 جيجا") || text.contains("1GB") || text == "/1gb" -> {
                addLog("تفعيل", "🎁 طلب تفعيل 1GB مجاناً للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ *جاري تفعيل باقة 1 جيجا مجاناً (MGM) للرقم $displayPhone...*\nيرجى الانتظار ثوانٍ قليلة...")

                val result = apiClient.activate1Gb(djezzyToken, phone)
                when (result) {
                    is ActivationResult.Success -> {
                        addLog("نجاح", "✅ تم تفعيل 1GB للرقم $displayPhone", isSuccess = true)
                        val msg = "✨ *بصحتك! تم التفعيل بنجاح* ✨\n" +
                                "───────────────────\n" +
                                "📦 *الباقة:* 🎁 1 جيجا مجاناً (عرض MGM)\n" +
                                "📱 *الرقم المستفيد:* `$displayPhone`\n" +
                                "⏱️ *التوقيت:* $timeNow\n" +
                                "📶 *الحالة:* ✅ مفعلة في سيرفرات جيزي بنجاح\n\n" +
                                "💡 *ملاحظة:* إذا لم يظهر الرصيد فوراً، قم بتشغيل وضع الطيران (Mode Avion) ثم إيقافه لتحديث شبكة الهاتف ✈️"
                        sendMessage(token, chatId, msg, getMainMenuMarkup(), getOfferResultInlineKeyboard())
                    }
                    is ActivationResult.Limit -> {
                        addLog("تنبيه", "⚠️ حد التفعيل لـ 1GB: ${result.message}")
                        val msg = "⚠️ *تنبيه من جيزي*\n" +
                                "───────────────────\n" +
                                "📱 *الرقم:* `$displayPhone`\n" +
                                "📝 *الرسالة:* ${result.message}\n\n" +
                                "ℹ️ يمكنك تفعيل باقة أخرى متوفرة مثل *2 جيجا مشي* من القائمة بالأسفل 👇"
                        sendMessage(token, chatId, msg, getMainMenuMarkup(), getQuickAlternativesInlineKeyboard())
                    }
                    is ActivationResult.Expired -> {
                        session.state = TelegramUserState.IDLE
                        session.activeToken = ""
                        saveOrUpdateSession(session)
                        addLog("جلسة منتهية", "انتهت صلاحية توكن جيزي للرقم $displayPhone", isError = true)
                        sendMessage(token, chatId, "⌛ *انتهت صلاحية جلسة جيزي.*\n📱 يرجى إرسال رقم هاتفك لتسجيل الدخول برمز OTP جديد.", null)
                    }
                    is ActivationResult.Failed -> {
                        addLog("فشل", "❌ فشل تفعيل 1GB: ${result.message}", isError = true)
                        val msg = "❌ *فشل التفعيل*\n" +
                                "───────────────────\n" +
                                "📱 *الرقم:* `$displayPhone`\n" +
                                "📝 *السبب:* ${result.message}"
                        sendMessage(token, chatId, msg, getMainMenuMarkup())
                    }
                }
            }

            text.contains("2 جيجا") || text.contains("مشي") || text.contains("Walk") || text == "/2gb" -> {
                addLog("تفعيل", "🚶 طلب تفعيل 2GB مشي للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ *جاري تفعيل مكافأة المشي 2 جيجا (Walk & Win) للرقم $displayPhone...*")

                val result = apiClient.activate2Gb(djezzyToken, phone)
                when (result) {
                    is ActivationResult.Success -> {
                        addLog("نجاح", "✅ تم تفعيل 2GB مشي للرقم $displayPhone", isSuccess = true)
                        val msg = "✨ *بصحتك! تم تفعيل مكافأة المشي* ✨\n" +
                                "───────────────────\n" +
                                "📦 *الباقة:* 🚶 2 جيجا أسبوعياً (Walk & Win)\n" +
                                "📱 *الرقم المستفيد:* `$displayPhone`\n" +
                                "⏱️ *التوقيت:* $timeNow\n" +
                                "📶 *الحالة:* ✅ مفعلة في سيرفرات جيزي بنجاح\n\n" +
                                "💡 *ملاحظة:* صالحة لمدة 7 أيام وتتجدد أسبوعياً."
                        sendMessage(token, chatId, msg, getMainMenuMarkup(), getOfferResultInlineKeyboard())
                    }
                    is ActivationResult.Limit -> {
                        addLog("تنبيه", "⚠️ حد التفعيل لـ 2GB: ${result.message}")
                        val msg = "⚠️ *تنبيه من جيزي*\n" +
                                "───────────────────\n" +
                                "📱 *الرقم:* `$displayPhone`\n" +
                                "📝 *الرسالة:* ${result.message}\n\n" +
                                "ℹ️ هذه المكافأة متاحة مرة واحدة كل 7 أيام."
                        sendMessage(token, chatId, msg, getMainMenuMarkup(), getQuickAlternativesInlineKeyboard())
                    }
                    is ActivationResult.Expired -> {
                        session.state = TelegramUserState.IDLE
                        session.activeToken = ""
                        saveOrUpdateSession(session)
                        sendMessage(token, chatId, "⌛ *انتهت صلاحية جلسة جيزي.*\n📱 يرجى إعادة إرسال رقم الهاتف للتجديد.", null)
                    }
                    is ActivationResult.Failed -> {
                        addLog("فشل", "❌ فشل تفعيل 2GB: ${result.message}", isError = true)
                        sendMessage(token, chatId, "❌ *فشل التفعيل:*\n${result.message}", getMainMenuMarkup())
                    }
                }
            }

            text.contains("3 جيجا") || text.contains("3GB") || text == "/3gb" -> {
                addLog("تفعيل", "⚡ طلب تفعيل باقة 3GB للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ *جاري تفعيل باقة 3 جيجا المدمجة (1GB + 2GB) للرقم $displayPhone...*")

                val result = apiClient.activate3Gb(djezzyToken, phone)
                when (result) {
                    is ActivationResult.Success -> {
                        addLog("نجاح", "✅ تم تفعيل 3GB للرقم $displayPhone", isSuccess = true)
                        val msg = "⚡ *بصحتك! تم تفعيل باقة 3 جيجا بالكامل* ⚡\n" +
                                "───────────────────\n" +
                                "📦 *الباقة:* 🎁 1GB هدية + 🚶 2GB مشي\n" +
                                "📱 *الرقم المستفيد:* `$displayPhone`\n" +
                                "⏱️ *التوقيت:* $timeNow\n" +
                                "📶 *الحالة:* ✅ مفعلة بالكامل بنجاح"
                        sendMessage(token, chatId, msg, getMainMenuMarkup(), getOfferResultInlineKeyboard())
                    }
                    is ActivationResult.Limit -> {
                        addLog("تنبيه", "⚠️ حد تفعيل 3GB: ${result.message}")
                        val msg = "⚠️ *تنبيه من جيزي:*\n${result.message}"
                        sendMessage(token, chatId, msg, getMainMenuMarkup())
                    }
                    is ActivationResult.Expired -> {
                        session.state = TelegramUserState.IDLE
                        session.activeToken = ""
                        saveOrUpdateSession(session)
                        sendMessage(token, chatId, "⌛ *انتهت صلاحية جلسة جيزي.*\n📱 يرجى إعادة إرسال رقم الهاتف للتجديد.", null)
                    }
                    is ActivationResult.Failed -> {
                        addLog("فشل", "❌ فشل 3GB: ${result.message}", isError = true)
                        sendMessage(token, chatId, "❌ *فشل التفعيل:*\n${result.message}", getMainMenuMarkup())
                    }
                }
            }

            text.contains("دعوة") || text.contains("MGM") || text.contains("رعاية") || text == "/mgm" -> {
                session.state = TelegramUserState.WAITING_MGM_RECEIVER
                saveOrUpdateSession(session)
                val prompt = "💌 *إرسال دعوة رعاية (MGM Send Invitation)*\n" +
                        "───────────────────\n" +
                        "🎁 هذه الخدمة ترسل دعوة رسمية من رقمك إلى صديقك ليستفيد من 1GB إنترنت مجاناً!\n\n" +
                        "📱 *يرجى إرسال رقم هاتف جيزي للشخص المستلم:*\n" +
                        "مثال: `0773527865` أو `0770123456`\n\n" +
                        "*(أو أرسل كلمة 'رجوع' للعودة للقائمة)*"
                sendMessage(token, chatId, prompt, getCancelMarkup())
            }

            text.contains("الرصيد") || text.contains("رصيد") || text.contains("Solde") || text == "/balance" -> {
                addLog("رصيد", "💰 استعلام عن الرصيد للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ *جاري فحص الرصيد من سيرفر جيزي الرسمي...*")

                val balanceResult = apiClient.getMainBalance(djezzyToken, phone)
                if (balanceResult.isSuccess) {
                    val info = balanceResult.getOrThrow()
                    val validityStr = if (info.expirationDate != null) "\n📅 *تاريخ الصلاحية:* ${info.expirationDate}" else ""
                    val reply = "💰 *تفاصيل رصيد حسابك:*\n" +
                            "───────────────────\n" +
                            "📱 *الرقم:* `$displayPhone`\n" +
                            "💵 *الرصيد الأساسي:* *${info.amount}*$validityStr\n" +
                            "⏱️ *وقت الفحص:* $timeNow\n" +
                            "📶 *حالة الخط:* نشط وجاهز"
                    sendMessage(token, chatId, reply, getMainMenuMarkup(), getBalanceInlineKeyboard())
                } else {
                    val err = balanceResult.exceptionOrNull()?.message ?: "فشل فحص الرصيد"
                    sendMessage(token, chatId, "❌ *تعذر جلب الرصيد:*\n$err", getMainMenuMarkup())
                }
            }

            text.contains("حسابي") || text.contains("معلومات") || text == "/account" -> {
                val accountMsg = "ℹ️ *تفاصيل حسابك المحفوظ:*\n" +
                        "───────────────────\n" +
                        "📱 *الرقم:* `$displayPhone`\n" +
                        "👤 *المستخدم:* @${session.username.ifBlank { "غير محدد" }}\n" +
                        "💾 *حالة الجلسة:* ✅ مسجلة ومحفوظة محلياً في هاتفك\n" +
                        "⚡ *نوع البوت:* خادم محلي بدون وسيط\n\n" +
                        "لإلغاء هذا الحساب واستخدام رقم آخر، اضغط على زر *تسجيل خروج*."
                sendMessage(token, chatId, accountMsg, getMainMenuMarkup())
            }

            text.contains("خروج") || text.contains("تسجيل خروج") || text.contains("رقم جديد") || text == "/logout" -> {
                deleteSession(chatId)
                addLog("خروج", "🚪 تم تسجيل الخروج وحذف الجلسة للرقم $displayPhone")
                val logoutMsg = "🚪 *تم تسجيل الخروج بنجاح.*\n" +
                        "───────────────────\n" +
                        "تم مسح بيانات الجلسة السابقة للرقم `$displayPhone`.\n\n" +
                        "📱 *أرسل رقم هاتف جيزي جديد في أي وقت للبدء:*"
                sendMessage(token, chatId, logoutMsg, null)
            }

            else -> {
                val help = "❓ لم أفهم اختيارك. يرجى الضغط على أحد الأزرار التفاعلية بالأسفل 👇"
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
            saveOrUpdateSession(session)
            sendMessage(token, chatId, "🔙 تم الرجوع إلى القائمة الرئيسية.", getMainMenuMarkup())
            return
        }

        val cleanDigits = receiverText.filter { it.isDigit() }
        if (cleanDigits.length !in 9..12) {
            sendMessage(
                token = token,
                chatId = chatId,
                text = "⚠️ *رقم هاتف غير صالح.*\n\nيرجى كتابة رقم جيزي صالح (مثال: `0773527865`) أو أرسل 'رجوع':"
            )
            return
        }

        val senderPhone = session.activePhone
        val djezzyToken = session.activeToken
        val displayReceiver = apiClient.formatDisplayPhone(cleanDigits)
        val displaySender = apiClient.formatDisplayPhone(senderPhone)

        addLog("دعوة", "💌 إرسال دعوة MGM من $displaySender إلى $displayReceiver")
        sendMessage(token, chatId, "⏳ *جاري إرسال دعوة الرعاية (MGM) إلى الرقم $displayReceiver عبر سيرفر جيزي...*")

        val result = apiClient.sendMgmInvitation(
            token = djezzyToken,
            senderPhone = senderPhone,
            receiverPhone = cleanDigits
        )

        session.state = TelegramUserState.LOGGED_IN
        saveOrUpdateSession(session)

        when (result) {
            is ActivationResult.Success -> {
                addLog("نجاح", "✅ تم إرسال دعوة MGM إلى $displayReceiver بنجاح", isSuccess = true)
                val msg = "💌 *تم إرسال دعوة الرعاية بنجاح!* 💌\n" +
                        "───────────────────\n" +
                        "📤 *المرسل:* `$displaySender`\n" +
                        "📥 *المستلم:* `$displayReceiver`\n" +
                        "🎁 *الهدية:* سيتمكن المستلم من تفعيل 1GB إنترنت مجاناً!\n" +
                        "⏱️ *التوقيت:* ${getFormattedTime()}\n\n" +
                        "🎉 شكراً لاستخدامك البوت."
                sendMessage(token, chatId, msg, getMainMenuMarkup(), getOfferResultInlineKeyboard())
            }
            is ActivationResult.Limit -> {
                addLog("تنبيه", "⚠️ حد الدعوات: ${result.message}")
                val msg = "⚠️ *تنبيه من جيزي*\n" +
                        "───────────────────\n" +
                        "📝 ${result.message}\n" +
                        "ربما تجاوزت الحد الأقصى للدعوات المسموحة لهذا اليوم."
                sendMessage(token, chatId, msg, getMainMenuMarkup())
            }
            is ActivationResult.Expired -> {
                session.state = TelegramUserState.IDLE
                session.activeToken = ""
                saveOrUpdateSession(session)
                sendMessage(token, chatId, "⌛ *انتهت صلاحية جلسة جيزي.* يرجى تسجيل الدخول مجدداً برقم الهاتف.", null)
            }
            is ActivationResult.Failed -> {
                addLog("فشل", "❌ فشل إرسال الدعوة: ${result.message}", isError = true)
                sendMessage(token, chatId, "❌ *فشل إرسال الدعوة:*\n${result.message}", getMainMenuMarkup())
            }
        }
    }

    private suspend fun sendWelcomeMessage(token: String, chatId: Long, firstName: String) {
        val welcome = "╔══════════════════════╗\n" +
                "   🇩🇿  *بوت جيزي للمكافآت والعروض*  🇩🇿\n" +
                "   *Djezzy Local Rewards Bot*\n" +
                "╚══════════════════════╝\n\n" +
                "👋 مرحباً بك يا *$firstName* في بوت تفعيل خدمات ومكافآت جيزي مجاناً!\n\n" +
                "⚡ *المميزات والخدمات المتاحة:*\n" +
                "• 🎁 *1 جيجا مجاناً* (عرض MGM الشهري)\n" +
                "• 🚶 *2 جيجا مشي* (تحدي Walk & Win الأسبوعي)\n" +
                "• ⚡ *باقة 3 جيجا كاملة* (1GB + 2GB معاً)\n" +
                "• 💌 *إرسال دعوات رعاية (MGM)* لأي رقم جيزي\n" +
                "• 💰 *استعلام فوري عن الرصيد والصلاحية*\n\n" +
                "🔒 *ميزة العمل المحلي والحفظ الدائم:*\n" +
                "يعمل هذا البوت مباشرة من شبكة هاتفك ويحفظ جلستك تلقائياً دون الحاجة لإعادة الدخول كل مرة.\n\n" +
                "───────────────────\n" +
                "📱 *للبدء، يرجى إرسال رقم هاتف جيزي الخاص بك:*\n" +
                "مثال: `0770123456` أو `0773527865`"

        sendMessage(token, chatId, welcome, null)
    }

    /**
     * Send message to a Telegram chat with Markdown parse mode and optional markup.
     */
    private suspend fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        replyMarkup: JSONObject? = null,
        inlineMarkup: JSONObject? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val url = "$TELEGRAM_API_BASE/bot$token/sendMessage"
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", "Markdown")
                // If inline markup is provided, it takes precedence in the message bubble
                val markupToSend = inlineMarkup ?: replyMarkup
                if (markupToSend != null) {
                    put("reply_markup", markupToSend)
                }
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            telegramHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "sendMessage failed: ${response.code} - ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending telegram message: ${e.message}")
        }
    }

    private suspend fun answerCallbackQuery(
        token: String,
        callbackQueryId: String,
        text: String? = null,
        showAlert: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            val url = "$TELEGRAM_API_BASE/bot$token/answerCallbackQuery"
            val payload = JSONObject().apply {
                put("callback_query_id", callbackQueryId)
                if (text != null) {
                    put("text", text)
                    put("show_alert", showAlert)
                }
            }
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            telegramHttpClient.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    /**
     * Polished Main Reply Keyboard matching Algerian mobile app conventions.
     */
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
                put(JSONObject().apply { put("text", "ℹ️ تفاصيل حسابي") })
            })
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "🚪 تسجيل خروج / رقم جديد") })
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

    /**
     * Inline Keyboard attached after successful activation.
     */
    private fun getOfferResultInlineKeyboard(): JSONObject {
        val inlineKeyboard = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "💰 فحص الرصيد الآن")
                    put("callback_data", "act_balance")
                })
                put(JSONObject().apply {
                    put("text", "💌 إرسال دعوة لصديق")
                    put("callback_data", "act_mgm")
                })
            })
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "📋 القائمة الكاملة")
                    put("callback_data", "act_menu")
                })
            })
        }
        return JSONObject().apply {
            put("inline_keyboard", inlineKeyboard)
        }
    }

    /**
     * Inline Keyboard attached when an offer hit cooldown limit.
     */
    private fun getQuickAlternativesInlineKeyboard(): JSONObject {
        val inlineKeyboard = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "🚶 جرب 2 جيجا مشي")
                    put("callback_data", "act_2gb")
                })
                put(JSONObject().apply {
                    put("text", "💌 إرسال دعوة MGM")
                    put("callback_data", "act_mgm")
                })
            })
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "💰 فحص الرصيد")
                    put("callback_data", "act_balance")
                })
            })
        }
        return JSONObject().apply {
            put("inline_keyboard", inlineKeyboard)
        }
    }

    private fun getBalanceInlineKeyboard(): JSONObject {
        val inlineKeyboard = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "🔄 تحديث الرصيد")
                    put("callback_data", "act_balance")
                })
                put(JSONObject().apply {
                    put("text", "🎁 تفعيل 1 جيجا")
                    put("callback_data", "act_1gb")
                })
            })
        }
        return JSONObject().apply {
            put("inline_keyboard", inlineKeyboard)
        }
    }
}
