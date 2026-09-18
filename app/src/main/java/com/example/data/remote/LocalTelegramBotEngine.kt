package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.TelegramSessionStore
import com.example.data.model.AvailableOffers
import com.example.data.model.MgmStatusInfo
import com.example.data.model.SavedTelegramPhoneAccount
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
 * - Multi-account support: easily save, manage, and switch between 3, 5, or unlimited phone numbers.
 * - Full Djezzy offers directory (Speed, BTL, Mixte, Family) + instant 1-click free rewards.
 * - MGM referral tracker: checks sent invites, remaining invites out of 5, and earned gigabytes.
 * - Clean Algerian interface: stripped of technical clutter, with friendly error masking.
 */
class LocalTelegramBotEngine private constructor(
    context: Context,
    private var apiClient: DjezzyApiClient
) {
    companion object {
        private const val TAG = "TelegramBotEngine"
        private const val TELEGRAM_API_BASE = "https://api.telegram.org"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_MGM_INVITES = 5
        const val SESSION_DURATION_MS = 4 * 60 * 60 * 1000L // 4 hours session limit

        @Volatile
        private var instance: LocalTelegramBotEngine? = null

        fun getInstance(context: Context, apiClient: DjezzyApiClient = DjezzyApiClient()): LocalTelegramBotEngine {
            val existing = instance
            if (existing != null) {
                existing.apiClient = apiClient
                return existing
            }
            return synchronized(this) {
                val synced = instance
                if (synced != null) {
                    synced.apiClient = apiClient
                    synced
                } else {
                    LocalTelegramBotEngine(
                        context = context.applicationContext,
                        apiClient = apiClient
                    ).also { instance = it }
                }
            }
        }
    }

    private val sessionStore = TelegramSessionStore(context)

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

    private val _isWaitingForNetwork = MutableStateFlow(false)
    val isWaitingForNetwork: StateFlow<Boolean> = _isWaitingForNetwork.asStateFlow()

    private val _botUsername = MutableStateFlow<String?>(null)
    val botUsername: StateFlow<String?> = _botUsername.asStateFlow()

    private val _botLogs = MutableStateFlow<List<TelegramLogItem>>(emptyList())
    val botLogs: StateFlow<List<TelegramLogItem>> = _botLogs.asStateFlow()

    private val _messagesCount = MutableStateFlow(0)
    val messagesCount: StateFlow<Int> = _messagesCount.asStateFlow()

    // In-memory sessions synchronized with permanent storage
    private val sessions = ConcurrentHashMap<Long, TelegramUserSession>()

    init {
        try {
            val loaded = sessionStore.getAllSessions()
            sessions.putAll(loaded)
            if (loaded.isNotEmpty()) {
                addLog("جلسات", "تم استرجاع ${loaded.size} جلسة محفوظة في الذاكرة الدائمة.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading saved sessions: ${e.message}")
        }
    }

    private fun getFormattedTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
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

    private fun getActiveAccount(session: TelegramUserSession): SavedTelegramPhoneAccount? {
        return session.savedAccounts.find { it.phone == session.activePhone }
            ?: session.savedAccounts.firstOrNull()
    }

    /**
     * Checks if the 4-hour session limit has expired for this Telegram session.
     */
    private fun isSessionExpired(session: TelegramUserSession): Boolean {
        if (session.state != TelegramUserState.LOGGED_IN || session.activeToken.isBlank()) return true
        val activeAcc = getActiveAccount(session) ?: return true
        if (activeAcc.token.isBlank()) return true
        val elapsed = System.currentTimeMillis() - activeAcc.addedAt
        return elapsed >= SESSION_DURATION_MS
    }

    /**
     * Formats remaining time before the 4-hour session expires.
     */
    private fun getSessionRemainingFormatted(session: TelegramUserSession): String {
        val activeAcc = getActiveAccount(session) ?: return "منتهية"
        val elapsed = System.currentTimeMillis() - activeAcc.addedAt
        val remaining = SESSION_DURATION_MS - elapsed
        if (remaining <= 0) return "منتهية"
        val hours = remaining / (1000 * 60 * 60)
        val minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60)
        return if (hours > 0) "$hours ساعة و $minutes دقيقة" else "$minutes دقيقة"
    }

    /**
     * Masks all technical and raw error messages into pleasant user-facing Arabic.
     */
    private fun sanitizeErrorMessage(rawError: String?): String {
        val err = rawError.orEmpty().lowercase()
        return when {
            err.contains("401") || err.contains("unauthorized") || err.contains("expire") ->
                "انتهت صلاحية جلسة الحساب، يرجى إعادة تسجيل الدخول برقم الهاتف."
            err.contains("limit") || err.contains("cooldown") || err.contains("déjà") || err.contains("already") || err.contains("atteint") ->
                "لقد استفدت من هذا العرض مسبقاً، يمكنك تجربة عرض آخر متوفر بالقائمة."
            err.contains("eligible") || err.contains("eligib") || err.contains("not allow") ->
                "هذا الرقم غير مؤهل لهذا العرض حالياً وفقاً لنظام جيزي."
            err.contains("timeout") || err.contains("connect") || err.contains("resolve") || err.contains("failed to connect") ->
                "تعذر الاتصال بسيرفر جيزي مؤقتاً، يرجى المحاولة بعد قليل."
            err.contains("otp") || err.contains("code") ->
                "رمز التحقق غير صحيح، يرجى التأكد من كتابة الأرقام الستة بدقة."
            err.contains("phone") || err.contains("numero") || err.contains("invalid") ->
                "رقم الهاتف غير صالح، تأكد من كتابة رقم جيزي صحيح."
            else ->
                "تعذر إتمام العملية حالياً، يرجى إعادة المحاولة لاحقاً."
        }
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
        _isWaitingForNetwork.value = false
        addLog("تشغيل", "🟢 تم تشغيل البوت! سيبقى شغالاً في الخلفية دائماً ولن ينطفئ حتى بـ 0 نت.")

        botJob = scope.launch(Dispatchers.IO) {
            var lastUpdateId = 0L

            while (isActive && _isRunning.value) {
                try {
                    // Try to fetch bot username if not yet resolved
                    if (_botUsername.value == null) {
                        try {
                            val testRes = testToken(cleanToken)
                            if (testRes.isSuccess) {
                                val info = testRes.getOrNull().orEmpty()
                                val uname = if (info.contains("@")) info.substringAfter("@").substringBefore(" ") else null
                                _botUsername.value = uname
                                addLog("نجاح", "🟢 البوت متصل وشغال: $info", isSuccess = true)
                            }
                        } catch (_: Exception) {}
                    }

                    val updatesUrl = "$TELEGRAM_API_BASE/bot$cleanToken/getUpdates?offset=$lastUpdateId&timeout=20"
                    val request = Request.Builder().url(updatesUrl).get().build()

                    val response = telegramHttpClient.newCall(request).execute()
                    val body = response.body?.string().orEmpty()
                    response.close()

                    if (_isWaitingForNetwork.value) {
                        _isWaitingForNetwork.value = false
                        addLog("اتصال", "🟢 توفرت شبكة الإنترنت: استئناف استقبال وتفعيل الرسائل فوراً.", isSuccess = true)
                    }

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

                                    // 1. Regular text messages
                                    val messageObj = updateObj.optJSONObject("message")
                                    if (messageObj != null) {
                                        handleIncomingMessage(cleanToken, messageObj)
                                    }

                                    // 2. Interactive callback queries (Inline Buttons)
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
                    // Caught network failure (0 net / offline / disconnected)
                    if (!_isWaitingForNetwork.value) {
                        _isWaitingForNetwork.value = true
                        addLog("استعداد", "📡 البوت شغال ومستمر (0 نت): في وضع الاستعداد الدائم ولن ينطفئ، بانتظار شبكة التيليجرام.")
                    }
                    delay(3500)
                }
            }

            if (!_isRunning.value) {
                addLog("توقف", "تم إيقاف تشغيل البوت يدوياً.")
            }
        }
    }

    /**
     * Stop the local Telegram Bot.
     */
    fun stop() {
        _isRunning.value = false
        _isWaitingForNetwork.value = false
        botJob?.cancel()
        botJob = null
        _botUsername.value = null
    }

    /**
     * Handle incoming Telegram messages.
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

        val session = sessions.getOrPut(chatId) {
            sessionStore.getSession(chatId) ?: TelegramUserSession(
                chatId = chatId,
                username = senderUsername,
                firstName = senderFirstName
            )
        }
        session.lastActivity = System.currentTimeMillis()

        // Global Command: /start, /help
        if (text == "/start" || text == "/help" || text.equals("start", ignoreCase = true)) {
            if (session.state == TelegramUserState.LOGGED_IN && session.activeToken.isNotBlank() && !isSessionExpired(session)) {
                val displayPhone = apiClient.formatDisplayPhone(session.activePhone)
                val remaining = getSessionRemainingFormatted(session)
                val welcomeBack = "👋 أهلاً بك مجدداً يا *$senderFirstName*!\n" +
                        "───────────────────\n" +
                        "📱 *الرقم النشط:* `$displayPhone`\n" +
                        "⏳ *صلاحية الجلسة:* باقي $remaining (تتجدد كل 4 ساعات)\n" +
                        "💾 *أرقامك المسجلة:* ${session.savedAccounts.size} أرقام\n\n" +
                        "👇 اختر ما ترغب به من القائمة أدناه:"
                sendMessage(token, chatId, welcomeBack, getMainMenuMarkup())
                return
            } else {
                if (session.activeToken.isNotBlank() && isSessionExpired(session)) {
                    promptReLogin(token, chatId, session)
                    return
                }
                session.state = TelegramUserState.IDLE
                saveOrUpdateSession(session)
                sendWelcomeMessage(token, chatId, senderFirstName)
                return
            }
        }

        // Global Command: /cancel, رجوع, إلغاء
        if (text == "/cancel" || text == "رجوع" || text == "إلغاء" || text.contains("رجوع للقائمة")) {
            session.state = if (session.activeToken.isNotBlank() && !isSessionExpired(session)) TelegramUserState.LOGGED_IN else TelegramUserState.IDLE
            saveOrUpdateSession(session)
            sendMessage(token, chatId, "🔙 تم الرجوع إلى القائمة الرئيسية.", if (session.state == TelegramUserState.LOGGED_IN) getMainMenuMarkup() else null)
            return
        }

        when (session.state) {
            TelegramUserState.IDLE -> {
                if (session.activeToken.isNotBlank()) {
                    if (isSessionExpired(session)) {
                        promptReLogin(token, chatId, session)
                        return
                    }
                    session.state = TelegramUserState.LOGGED_IN
                    saveOrUpdateSession(session)
                    handleLoggedInAction(token, chatId, session, text)
                    return
                }

                val cleanDigits = text.filter { it.isDigit() }
                if (cleanDigits.length in 9..12 && (cleanDigits.startsWith("07") || cleanDigits.startsWith("7") || cleanDigits.startsWith("2137"))) {
                    processPhoneInput(token, chatId, session, cleanDigits, isAddingNewAccount = false)
                } else {
                    sendMessage(
                        token = token,
                        chatId = chatId,
                        text = "👋 مرحباً *$senderFirstName*!\n\n📱 يرجى إرسال رقم هاتف جيزي الخاص بك للبدء وتفعيل العروض:\nمثال: `0770123456` أو `0773527865`",
                        replyMarkup = null
                    )
                }
            }

            TelegramUserState.WAITING_OTP -> {
                val cleanOtp = text.filter { it.isDigit() }
                if (cleanOtp.length == 6) {
                    processOtpInput(token, chatId, session, cleanOtp, isAddingNewAccount = false)
                } else {
                    sendMessage(
                        token = token,
                        chatId = chatId,
                        text = "⚠️ *رمز التحقق غير صحيح.*\n\n🔢 يرجى كتابة رمز OTP المكون من 6 أرقام وصلتك في SMS.\nأو أرسل /cancel للرجوع."
                    )
                }
            }

            TelegramUserState.WAITING_NEW_PHONE -> {
                val cleanDigits = text.filter { it.isDigit() }
                if (cleanDigits.length in 9..12 && (cleanDigits.startsWith("07") || cleanDigits.startsWith("7") || cleanDigits.startsWith("2137"))) {
                    processPhoneInput(token, chatId, session, cleanDigits, isAddingNewAccount = true)
                } else {
                    sendMessage(
                        token = token,
                        chatId = chatId,
                        text = "⚠️ *رقم هاتف غير صالح.*\n\nيرجى كتابة رقم جيزي صالح لإضافته (مثال: `0770123456`) أو أرسل 'رجوع':"
                    )
                }
            }

            TelegramUserState.WAITING_NEW_PHONE_OTP -> {
                val cleanOtp = text.filter { it.isDigit() }
                if (cleanOtp.length == 6) {
                    processOtpInput(token, chatId, session, cleanOtp, isAddingNewAccount = true)
                } else {
                    sendMessage(
                        token = token,
                        chatId = chatId,
                        text = "⚠️ *رمز التحقق غير صحيح.*\n\nيرجى إدخال رمز التحقق المكون من 6 أرقام للرقم الجديد أو أرسل 'رجوع':"
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

        answerCallbackQuery(token, queryId, "جاري المعالجة...")

        when {
            data == "act_1gb" -> handleLoggedInAction(token, chatId, session, "1 جيجا")
            data == "act_2gb" -> handleLoggedInAction(token, chatId, session, "2 جيجا")
            data == "act_3gb" -> handleLoggedInAction(token, chatId, session, "3 جيجا")
            data == "act_balance" -> handleLoggedInAction(token, chatId, session, "الرصيد")
            data == "act_mgm" -> handleLoggedInAction(token, chatId, session, "دعوة")
            data == "act_mgm_stats" -> handleLoggedInAction(token, chatId, session, "فحص دعوات")
            data == "act_offers" -> showOffersCatalog(token, chatId)
            data == "act_offers_speed" -> showCategoryOffers(token, chatId, "عروض SPEED")
            data == "act_offers_btl" -> showCategoryOffers(token, chatId, "عروض BTL")
            data == "act_offers_mixte" -> showCategoryOffers(token, chatId, "عروض MIXTE")
            data == "act_offers_family" -> showCategoryOffers(token, chatId, "عروض FAMILY")
            data == "act_my_numbers" -> showNumbersManagement(token, chatId, session)
            data == "act_add_number" -> startAddNewNumberFlow(token, chatId, session)
            data.startsWith("switch_phone_") -> {
                val targetPhone = data.removePrefix("switch_phone_")
                switchActivePhone(token, chatId, session, targetPhone)
            }
            data.startsWith("del_phone_") -> {
                val targetPhone = data.removePrefix("del_phone_")
                deletePhoneAccount(token, chatId, session, targetPhone)
            }
            data == "act_menu" -> {
                sendMessage(token, chatId, "📋 *القائمة الرئيسية للعروض والخدمات:*", getMainMenuMarkup())
            }
        }
    }

    private suspend fun processPhoneInput(
        token: String,
        chatId: Long,
        session: TelegramUserSession,
        rawPhone: String,
        isAddingNewAccount: Boolean
    ) {
        val formatted = apiClient.formatPhoneNumber(rawPhone)
        val displayPhone = apiClient.formatDisplayPhone(rawPhone)
        session.pendingPhone = formatted

        addLog("طلب رمز", "📩 إرسال كود OTP للرقم $displayPhone عبر شبكة الهاتف المتصلة")
        sendMessage(token, chatId, "⏳ *جاري إرسال رمز التحقق (OTP) إلى الرقم $displayPhone عبر شبكة الهاتف مباشرة...*")

        val reqResult = apiClient.requestOtp(formatted)
        if (reqResult.isSuccess) {
            session.state = if (isAddingNewAccount) TelegramUserState.WAITING_NEW_PHONE_OTP else TelegramUserState.WAITING_OTP
            saveOrUpdateSession(session)
            addLog("نجاح", "✅ تم إرسال OTP للرقم $displayPhone بنجاح عبر شبكة الهاتف المحلية", isSuccess = true)

            val otpPrompt = "📩 *تم إرسال رمز التحقق (OTP) عبر شبكة جيزي*\n" +
                    "───────────────────\n" +
                    "📱 *الرقم:* `$displayPhone`\n" +
                    "📬 تفقد رسائل SMS على هاتفك الآن.\n\n" +
                    "🔢 *اكتب رمز التحقق المكون من 6 أرقام هنا:*\n\n" +
                    "*(أو أرسل 'رجوع' للإلغاء)*"

            sendMessage(token, chatId, otpPrompt, null)
        } else {
            val userFriendlyError = sanitizeErrorMessage(reqResult.exceptionOrNull()?.message)
            addLog("خطأ", "❌ فشل إرسال OTP للرقم $displayPhone عبر الشبكة", isError = true)
            sendMessage(
                token = token,
                chatId = chatId,
                text = "❌ *تعذر إرسال رمز التحقق عبر الشبكة.*\n\n$userFriendlyError\n\nتأكد من صحة رقم جيزي وتوفر اتصال بالشبكة وأعد المحاولة."
            )
        }
    }

    private suspend fun processOtpInput(
        token: String,
        chatId: Long,
        session: TelegramUserSession,
        otp: String,
        isAddingNewAccount: Boolean
    ) {
        val phone = session.pendingPhone
        val displayPhone = apiClient.formatDisplayPhone(phone)

        addLog("تحقق", "🔑 التحقق من رمز OTP للرقم $displayPhone")
        sendMessage(token, chatId, "⏳ *جاري التحقق من الرمز...*")

        val verifyResult = apiClient.verifyOtp(phone, otp)
        if (verifyResult.isSuccess) {
            val djezzyToken = verifyResult.getOrThrow()

            // Update or add to savedAccounts
            val now = System.currentTimeMillis()
            val existing = session.savedAccounts.find { it.phone == phone }
            if (existing != null) {
                existing.token = djezzyToken
                existing.addedAt = now
            } else {
                session.savedAccounts.add(
                    SavedTelegramPhoneAccount(
                        phone = phone,
                        token = djezzyToken,
                        addedAt = now
                    )
                )
            }

            session.activePhone = phone
            session.activeToken = djezzyToken
            session.state = TelegramUserState.LOGGED_IN
            saveOrUpdateSession(session)

            addLog("تسجيل", "🎉 تم حفظ الرقم $displayPhone بنجاح", isSuccess = true)

            val successMsg = if (isAddingNewAccount) {
                "🎉 *تمت إضافة الرقم بنجاح!* 🎉\n" +
                        "───────────────────\n" +
                        "📱 *الرقم النشط حالياً:* `$displayPhone`\n" +
                        "⏳ *صلاحية الجلسة:* صالحة لـ 4 ساعات\n" +
                        "💾 *إجمالي أرقامك المحفوظة:* ${session.savedAccounts.size} أرقام\n\n" +
                        "يمكنك التبديل بين أرقامك في أي وقت من زر *[📱 إدارة أرقامي]*."
            } else {
                "🎉 *تم تسجيل الدخول بنجاح!* 🎉\n" +
                        "───────────────────\n" +
                        "📱 *الرقم النشط:* `$displayPhone`\n" +
                        "⏳ *صلاحية الجلسة:* صالحة لـ 4 ساعات (وفق نظام جيزي لحماية أمان خطك)\n" +
                        "📱 *ميزة الأرقام المتعددة:* يمكنك إضافة 3، 5، أو أي عدد من الأرقام والتبديل بينها فوراً!"
            }

            sendMessage(token, chatId, successMsg, getMainMenuMarkup())
        } else {
            val userFriendlyError = sanitizeErrorMessage(verifyResult.exceptionOrNull()?.message)
            addLog("خطأ", "❌ فشل التحقق للرقم $displayPhone", isError = true)
            sendMessage(
                token = token,
                chatId = chatId,
                text = "❌ *فشل التحقق!*\n\n$userFriendlyError\nيرجى كتابة الرمز الصحيح أو أرسل 'رجوع'."
            )
        }
    }

    private suspend fun handleLoggedInAction(
        token: String,
        chatId: Long,
        session: TelegramUserSession,
        text: String
    ) {
        if (isSessionExpired(session)) {
            promptReLogin(token, chatId, session)
            return
        }
        val phone = session.activePhone
        val djezzyToken = session.activeToken
        val displayPhone = apiClient.formatDisplayPhone(phone)
        val timeNow = getFormattedTime()

        when {
            // 1GB Activation
            text.contains("1 جيجا") || text.contains("1GB") || text == "/1gb" -> {
                addLog("تفعيل", "🎁 طلب تفعيل 1GB مجاناً للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ *جاري تفعيل باقة 1 جيجا مجاناً للرقم $displayPhone...*")

                val result = apiClient.activate1Gb(djezzyToken, phone)
                when (result) {
                    is ActivationResult.Success -> {
                        getActiveAccount(session)?.last1GbActivatedAt = System.currentTimeMillis()
                        saveOrUpdateSession(session)
                        addLog("نجاح", "✅ تم تفعيل 1GB للرقم $displayPhone", isSuccess = true)

                        val msg = "✨ *بصحتك! تم التفعيل بنجاح* ✨\n" +
                                "───────────────────\n" +
                                "📦 *الباقة:* 🎁 1 جيجا مجاناً (عرض MGM)\n" +
                                "📱 *الرقم:* `$displayPhone`\n" +
                                "⏱️ *التوقيت:* $timeNow\n\n" +
                                "💡 *نصيحة:* إذا لم يظهر الرصيد فوراً، قم بتشغيل وضع الطيران (Mode Avion) ثم إيقافه لتحديث الشبكة ✈️"
                        sendMessage(token, chatId, msg, getMainMenuMarkup(), getOfferResultInlineKeyboard())
                    }
                    is ActivationResult.Limit -> {
                        val friendlyMsg = sanitizeErrorMessage(result.message)
                        val msg = "⚠️ *تنبيه من جيزي*\n" +
                                "───────────────────\n" +
                                "📱 *الرقم:* `$displayPhone`\n" +
                                "📝 $friendlyMsg\n\n" +
                                "ℹ️ يمكنك تجربة باقة أخرى متوفرة مثل *2 جيجا مشي* 👇"
                        sendMessage(token, chatId, msg, getMainMenuMarkup(), getQuickAlternativesInlineKeyboard())
                    }
                    is ActivationResult.Expired -> {
                        promptReLogin(token, chatId, session)
                    }
                    is ActivationResult.Failed -> {
                        val friendlyMsg = sanitizeErrorMessage(result.message)
                        sendMessage(token, chatId, "⚠️ *تعذر التفعيل:*\n$friendlyMsg", getMainMenuMarkup())
                    }
                }
            }

            // 2GB Walk & Win Activation
            text.contains("2 جيجا") || text.contains("مشي") || text.contains("Walk") || text == "/2gb" -> {
                addLog("تفعيل", "🚶 طلب تفعيل 2GB مشي للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ *جاري تفعيل مكافأة المشي 2 جيجا للرقم $displayPhone...*")

                val result = apiClient.activate2Gb(djezzyToken, phone)
                when (result) {
                    is ActivationResult.Success -> {
                        getActiveAccount(session)?.last2GbActivatedAt = System.currentTimeMillis()
                        saveOrUpdateSession(session)
                        addLog("نجاح", "✅ تم تفعيل 2GB مشي للرقم $displayPhone", isSuccess = true)

                        val msg = "✨ *بصحتك! تم تفعيل مكافأة المشي* ✨\n" +
                                "───────────────────\n" +
                                "📦 *الباقة:* 🚶 2 جيجا أسبوعياً (Walk & Win)\n" +
                                "📱 *الرقم:* `$displayPhone`\n" +
                                "⏱️ *التوقيت:* $timeNow\n" +
                                "📅 *الصلاحية:* 7 أيام وتتجدد أسبوعياً."
                        sendMessage(token, chatId, msg, getMainMenuMarkup(), getOfferResultInlineKeyboard())
                    }
                    is ActivationResult.Limit -> {
                        val friendlyMsg = sanitizeErrorMessage(result.message)
                        val msg = "⚠️ *تنبيه من جيزي*\n" +
                                "───────────────────\n" +
                                "📱 *الرقم:* `$displayPhone`\n" +
                                "📝 $friendlyMsg\n\n" +
                                "ℹ️ مكافأة المشي متاحة مرة واحدة كل 7 أيام."
                        sendMessage(token, chatId, msg, getMainMenuMarkup(), getQuickAlternativesInlineKeyboard())
                    }
                    is ActivationResult.Expired -> {
                        promptReLogin(token, chatId, session)
                    }
                    is ActivationResult.Failed -> {
                        val friendlyMsg = sanitizeErrorMessage(result.message)
                        sendMessage(token, chatId, "⚠️ *تعذر التفعيل:*\n$friendlyMsg", getMainMenuMarkup())
                    }
                }
            }

            // 3GB Combo Activation
            text.contains("3 جيجا") || text.contains("3GB") || text == "/3gb" -> {
                addLog("تفعيل", "⚡ طلب تفعيل باقة 3GB للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ *جاري تفعيل باقة 3 جيجا (1GB + 2GB) للرقم $displayPhone...*")

                val result = apiClient.activate3Gb(djezzyToken, phone)
                when (result) {
                    is ActivationResult.Success -> {
                        getActiveAccount(session)?.last3GbActivatedAt = System.currentTimeMillis()
                        saveOrUpdateSession(session)
                        addLog("نجاح", "✅ تم تفعيل 3GB للرقم $displayPhone", isSuccess = true)

                        val msg = "⚡ *بصحتك! تم تفعيل باقة 3 جيجا بالكامل* ⚡\n" +
                                "───────────────────\n" +
                                "📦 *الباقة:* 🎁 1GB هدية + 🚶 2GB مشي\n" +
                                "📱 *الرقم:* `$displayPhone`\n" +
                                "⏱️ *التوقيت:* $timeNow"
                        sendMessage(token, chatId, msg, getMainMenuMarkup(), getOfferResultInlineKeyboard())
                    }
                    is ActivationResult.Limit -> {
                        val friendlyMsg = sanitizeErrorMessage(result.message)
                        sendMessage(token, chatId, "⚠️ *تنبيه من جيزي:*\n$friendlyMsg", getMainMenuMarkup())
                    }
                    is ActivationResult.Expired -> {
                        promptReLogin(token, chatId, session)
                    }
                    is ActivationResult.Failed -> {
                        val friendlyMsg = sanitizeErrorMessage(result.message)
                        sendMessage(token, chatId, "⚠️ *تعذر التفعيل:*\n$friendlyMsg", getMainMenuMarkup())
                    }
                }
            }

            // MGM Send Invitation
            text.contains("إرسال دعوة") || text == "💌 إرسال دعوة MGM" || text == "/mgm" -> {
                val acc = getActiveAccount(session)
                if (acc == null) {
                    sendMessage(token, chatId, "⚠️ يرجى تسجيل الدخول أولاً.", getMainMenuMarkup())
                    return
                }

                sendMessage(token, chatId, "⏳ *جاري التحقق من أهلية رقمك للدعوات عبر سيرفر جيزي...*", getMainMenuMarkup())
                val mgmResult = apiClient.getMgmCustomerOffers(acc.token, phone)
                val info = mgmResult.getOrNull()
                if (info != null) {
                    acc.mgmInvitesSent = info.usedInvites
                    saveOrUpdateSession(session)
                }

                if (info != null && info.isAllConsumed) {
                    val textExhausted = "⚠️ *عذراً، لا يمكنك إرسال دعوات!* ⚠️\n" +
                            "───────────────────\n" +
                            "📱 *الرقم:* `$displayPhone`\n" +
                            "❌ لقد قمت باستهلاك وتفعيل جميع دعوات الرعاية الـ ${info.totalAllowed} بالكامل من قبل!\n" +
                            "🎁 *الدعوات المتبقية:* 0 دعوات متاحة.\n\n" +
                            "💡 *ملاحظة:* إذا كان لديك رقم جيزي آخر لم يستنفذ دعواته، يمكنك التبديل إليه من قائمة '📱 إدارة أرقامي' وإرسال الدعوات منه."

                    val inlineKb = JSONArray().apply {
                        put(JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "📱 إدارة أرقامي")
                                put("callback_data", "act_numbers")
                            })
                            put(JSONObject().apply {
                                put("text", "🎁 تفعيل 1 جيجا")
                                put("callback_data", "act_1gb")
                            })
                        })
                    }
                    val markup = JSONObject().apply { put("inline_keyboard", inlineKb) }
                    sendMessage(token, chatId, textExhausted, getMainMenuMarkup(), markup)
                    return
                }

                val total = info?.totalAllowed ?: MAX_MGM_INVITES
                val remaining = info?.remainingInvites ?: maxOf(0, total - (acc.mgmInvitesSent))
                val sent = info?.usedInvites ?: acc.mgmInvitesSent

                val prompt = "💌 *إرسال دعوة رعاية (MGM)*\n" +
                        "───────────────────\n" +
                        "🎁 أرسل دعوة رسمية من رقمك إلى صديقك ليستفيد من 1GB إنترنت مجاناً، وتكسب أنت أيضاً 1GB!\n\n" +
                        "📊 *حالة رصيد دعواتك الحقيقي:* متبقي *$remaining* من *$total* دعوات ($sent مستهلكة)\n\n" +
                        "📱 *اكتب رقم هاتف جيزي الخاص بالشخص المستلم:*\n" +
                        "مثال: `0773527865` أو `0770123456`\n\n" +
                        "*(أو أرسل 'رجوع' للعودة)*"

                session.state = TelegramUserState.WAITING_MGM_RECEIVER
                saveOrUpdateSession(session)
                sendMessage(token, chatId, prompt, getCancelMarkup())
            }

            // MGM Tracker & Checker
            text.contains("فحص دعوات") || text.contains("إحصائيات دعوات") || text == "📊 فحص دعوات MGM" || text == "/mgm_status" -> {
                showMgmStats(token, chatId, session)
            }

            // Djezzy Offers Directory
            text.contains("باقات وعروض") || text.contains("العروض") || text == "📦 باقات وعروض جيزي" || text == "/offers" -> {
                showOffersCatalog(token, chatId)
            }

            // Multi-Number Management
            text.contains("أرقامي") || text.contains("إدارة أرقامي") || text == "📱 إدارة أرقامي" || text == "/numbers" -> {
                showNumbersManagement(token, chatId, session)
            }

            // Check Balance
            text.contains("الرصيد") || text.contains("رصيد") || text.contains("Solde") || text == "/balance" -> {
                addLog("رصيد", "💰 استعلام عن الرصيد للرقم $displayPhone")
                sendMessage(token, chatId, "⏳ *جاري فحص الرصيد...*")

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
                    val friendlyMsg = sanitizeErrorMessage(balanceResult.exceptionOrNull()?.message)
                    sendMessage(token, chatId, "⚠️ *تعذر جلب الرصيد:*\n$friendlyMsg", getMainMenuMarkup())
                }
            }

            // Logout
            text.contains("خروج") || text.contains("تسجيل خروج") || text == "/logout" -> {
                deleteSession(chatId)
                addLog("خروج", "🚪 تم تسجيل الخروج ومسح الجلسة للرقم $displayPhone")
                val logoutMsg = "🚪 *تم تسجيل الخروج بنجاح.*\n" +
                        "تم مسح بيانات الجلسة للرقم `$displayPhone`.\n\n" +
                        "📱 أرسل رقم هاتف جيزي في أي وقت للبدء مجدداً."
                sendMessage(token, chatId, logoutMsg, null)
            }

            else -> {
                sendMessage(
                    token = token,
                    chatId = chatId,
                    text = "👋 مرحباً! اختر من الأزرار التفاعلية بالأسفل 👇",
                    replyMarkup = getMainMenuMarkup()
                )
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
                text = "⚠️ *رقم هاتف غير صالح.*\nيرجى كتابة رقم جيزي صالح (مثال: `0773527865`) أو أرسل 'رجوع':"
            )
            return
        }

        val senderPhone = session.activePhone
        val djezzyToken = session.activeToken
        val displayReceiver = apiClient.formatDisplayPhone(cleanDigits)
        val displaySender = apiClient.formatDisplayPhone(senderPhone)

        addLog("دعوة", "💌 إرسال دعوة MGM من $displaySender إلى $displayReceiver")
        sendMessage(token, chatId, "⏳ *جاري إرسال دعوة الرعاية إلى $displayReceiver...*")

        val result = apiClient.sendMgmInvitation(
            token = djezzyToken,
            senderPhone = senderPhone,
            receiverPhone = cleanDigits
        )

        session.state = TelegramUserState.LOGGED_IN

        when (result) {
            is ActivationResult.Success -> {
                val acc = getActiveAccount(session)
                if (acc != null) {
                    acc.mgmInvitesSent++
                }
                saveOrUpdateSession(session)

                val sent = acc?.mgmInvitesSent ?: 1
                val remaining = maxOf(0, MAX_MGM_INVITES - sent)

                addLog("نجاح", "✅ تم إرسال دعوة MGM إلى $displayReceiver", isSuccess = true)

                val msg = "💌 *تم إرسال دعوة الرعاية بنجاح!* 💌\n" +
                        "───────────────────\n" +
                        "📤 *المرسل:* `$displaySender`\n" +
                        "📥 *المستلم:* `$displayReceiver`\n" +
                        "🎁 *الهدية:* سيستفيد المستلم من 1GB مجاناً وتكسب أنت 1GB!\n" +
                        "📊 *الدعوات المستهلكة:* $sent من $MAX_MGM_INVITES\n" +
                        "🎁 *المتبقي لك:* $remaining دعوات متاحة"

                sendMessage(token, chatId, msg, getMainMenuMarkup(), getOfferResultInlineKeyboard())
            }
            is ActivationResult.Limit -> {
                val friendlyMsg = sanitizeErrorMessage(result.message)
                sendMessage(token, chatId, "⚠️ *تنبيه من جيزي:*\n$friendlyMsg\nلقد بلغت الحد الأقصى للدعوات المسموحة لهذا الرقم.", getMainMenuMarkup())
            }
            is ActivationResult.Expired -> {
                promptReLogin(token, chatId, session)
            }
            is ActivationResult.Failed -> {
                val friendlyMsg = sanitizeErrorMessage(result.message)
                sendMessage(token, chatId, "⚠️ *تعذر إرسال الدعوة:*\n$friendlyMsg", getMainMenuMarkup())
            }
        }
    }

    /**
     * Show MGM Referral Tracker Card with live data from official Djezzy APIM endpoint.
     */
    private suspend fun showMgmStats(token: String, chatId: Long, session: TelegramUserSession) {
        val phone = session.activePhone
        val displayPhone = apiClient.formatDisplayPhone(phone)
        val acc = getActiveAccount(session)
        if (acc == null) {
            sendMessage(token, chatId, "⚠️ يرجى تسجيل الدخول أولاً.", getMainMenuMarkup())
            return
        }

        sendMessage(token, chatId, "⏳ *جاري فحص رصيد دعوات MGM عبر سيرفر جيزي الرسمي...*", getMainMenuMarkup())

        val mgmResult = apiClient.getMgmCustomerOffers(acc.token, phone)
        if (mgmResult.isSuccess) {
            val info = mgmResult.getOrThrow()
            acc.mgmInvitesSent = info.usedInvites
            saveOrUpdateSession(session)

            val statusTitle = if (info.isAllConsumed) {
                "⚠️ *حالة الحساب:* تم تفعيل واستهلاك جميع الدعوات الـ ${info.totalAllowed} بالكامل من قبل! (0 دعوة متبقية)"
            } else if (info.usedInvites == 0) {
                "✅ *حالة الحساب:* مؤهل بالكامل، لم تستهلك أي دعوة بعد!"
            } else {
                "✅ *حالة الحساب:* مؤهل، متبقي لك *${info.remainingInvites}* دعوة من أصل *${info.totalAllowed}*"
            }

            val text = "📊 *فحص دعوات الرعاية (MGM) الرسمي*\n" +
                    "───────────────────\n" +
                    "📱 *الرقم الحالي:* `$displayPhone`\n" +
                    "$statusTitle\n\n" +
                    "🎁 *الدعوات المتبقية:* *${info.remainingInvites}* من *${info.totalAllowed}* دعوات متاحة\n" +
                    "💌 *الدعوات المستهلكة:* *${info.usedInvites}* دعوات سابقة\n" +
                    "⚡ *إجمالي الرصيد المكتسب:* *${info.usedInvites} جيجا* مجاناً\n\n" +
                    "📝 *تقرير السيرفر الرسمي:*\n${info.statusSummary}\n\n" +
                    "💡 *كيف يعمل العرض؟*\n" +
                    "في كل مرة يفتح صديقك الدعوة ويسجل دخوله، يربح هو 1 جيجا وتربح أنت 1 جيجا إضافية!"

            val inlineKeyboard = JSONArray().apply {
                if (info.remainingInvites > 0) {
                    put(JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "💌 إرسال دعوة جديدة الآن")
                            put("callback_data", "act_mgm")
                        })
                    })
                }
                put(JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "🎁 تفعيل 1 جيجا")
                        put("callback_data", "act_1gb")
                    })
                    put(JSONObject().apply {
                        put("text", "💰 فحص الرصيد")
                        put("callback_data", "act_balance")
                    })
                })
                if (session.savedAccounts.size > 1) {
                    put(JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "📱 إدارة / تبديل الأرقام")
                            put("callback_data", "act_numbers")
                        })
                    })
                }
            }

            val markup = JSONObject().apply { put("inline_keyboard", inlineKeyboard) }
            sendMessage(token, chatId, text, getMainMenuMarkup(), markup)
        } else {
            val error = mgmResult.exceptionOrNull()?.message.orEmpty()
            if (error.contains("انتهت صلاحية الجلسة")) {
                promptReLogin(token, chatId, session)
            } else {
                val friendlyMsg = sanitizeErrorMessage(error)
                sendMessage(token, chatId, "⚠️ *تعذر فحص دعوات MGM:*\n$friendlyMsg", getMainMenuMarkup())
            }
        }
    }

    /**
     * Show Multi-Number Management Interface.
     */
    private suspend fun showNumbersManagement(token: String, chatId: Long, session: TelegramUserSession) {
        val activePhone = session.activePhone
        val count = session.savedAccounts.size

        val sb = StringBuilder()
        sb.append("📱 *إدارة الأرقام المسجلة*\n")
        sb.append("───────────────────\n")
        sb.append("لديك حالياً *$count* أرقام محفوظة.\n")
        sb.append("*(يمكنك إضافة أي عدد من الأرقام والتبديل بينها بضغطة زر)*\n\n")

        session.savedAccounts.forEachIndexed { index, acc ->
            val display = apiClient.formatDisplayPhone(acc.phone)
            val isActive = acc.phone == activePhone
            val elapsed = System.currentTimeMillis() - acc.addedAt
            val isExpired = acc.token.isBlank() || elapsed >= SESSION_DURATION_MS
            val statusTag = when {
                isActive && isExpired -> " 🔴 (النشط - منتهية الجلسة)"
                isActive -> " 🟢 (النشط حالياً)"
                isExpired -> " ⚪ (منتهية)"
                else -> ""
            }
            sb.append("${index + 1}. `$display`$statusTag\n")
        }

        val inlineKeyboard = JSONArray()

        // Switch buttons for non-active numbers
        session.savedAccounts.forEach { acc ->
            if (acc.phone != activePhone) {
                val display = apiClient.formatDisplayPhone(acc.phone)
                inlineKeyboard.put(JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "🔄 تفعيل $display")
                        put("callback_data", "switch_phone_${acc.phone}")
                    })
                    put(JSONObject().apply {
                        put("text", "🗑️ حذف")
                        put("callback_data", "del_phone_${acc.phone}")
                    })
                })
            }
        }

        // Add number button
        inlineKeyboard.put(JSONArray().apply {
            put(JSONObject().apply {
                put("text", "➕ إضافة رقم جيزي جديد")
                put("callback_data", "act_add_number")
            })
        })

        val markup = JSONObject().apply { put("inline_keyboard", inlineKeyboard) }
        sendMessage(token, chatId, sb.toString(), getMainMenuMarkup(), markup)
    }

    private suspend fun startAddNewNumberFlow(token: String, chatId: Long, session: TelegramUserSession) {
        session.state = TelegramUserState.WAITING_NEW_PHONE
        saveOrUpdateSession(session)

        val prompt = "➕ *إضافة رقم جيزي جديد*\n" +
                "───────────────────\n" +
                "📱 *يرجى كتابة رقم جيزي الجديد الذي تريد إضافته وحفظه:*\n" +
                "مثال: `0770123456`\n\n" +
                "*(أو أرسل 'رجوع' للإلغاء)*"

        sendMessage(token, chatId, prompt, getCancelMarkup())
    }

    private suspend fun switchActivePhone(token: String, chatId: Long, session: TelegramUserSession, targetPhone: String) {
        val acc = session.savedAccounts.find { it.phone == targetPhone }
        if (acc != null) {
            session.activePhone = acc.phone
            session.activeToken = acc.token
            val display = apiClient.formatDisplayPhone(acc.phone)

            if (isSessionExpired(session)) {
                promptReLogin(token, chatId, session)
                return
            }

            session.state = TelegramUserState.LOGGED_IN
            saveOrUpdateSession(session)

            val remaining = getSessionRemainingFormatted(session)
            val msg = "✅ *تم التبديل بنجاح!*\n" +
                    "───────────────────\n" +
                    "📱 *الرقم النشط الآن:* `$display`\n" +
                    "⏳ *صلاحية الجلسة:* باقي $remaining (تتجدد كل 4 ساعات)\n\n" +
                    "أي تفعيل أو استعلام سيتم تطبيقه على هذا الرقم."
            sendMessage(token, chatId, msg, getMainMenuMarkup())
        }
    }

    private suspend fun deletePhoneAccount(token: String, chatId: Long, session: TelegramUserSession, targetPhone: String) {
        session.savedAccounts.removeAll { it.phone == targetPhone }
        if (session.activePhone == targetPhone) {
            val next = session.savedAccounts.firstOrNull()
            if (next != null) {
                session.activePhone = next.phone
                session.activeToken = next.token
            } else {
                session.activePhone = ""
                session.activeToken = ""
                session.state = TelegramUserState.IDLE
            }
        }
        saveOrUpdateSession(session)

        val display = apiClient.formatDisplayPhone(targetPhone)
        sendMessage(token, chatId, "🗑️ تم حذف الرقم `$display` من قائمتك.", getMainMenuMarkup())
        showNumbersManagement(token, chatId, session)
    }

    /**
     * Show Catalog of all Djezzy Offers.
     */
    private suspend fun showOffersCatalog(token: String, chatId: Long) {
        val text = "📦 *دليل باقات وعروض جيزي الرسمية*\n" +
                "───────────────────\n" +
                "اختر نوع العرض للاطلاع على الباقات والأسعار وأكواد التفعيل المباشرة:"

        val inlineKeyboard = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "🚀 باقات SPEED (الإنترنت السريع)")
                    put("callback_data", "act_offers_speed")
                })
            })
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "📱 عروض BTL (إنترنت مكثف)")
                    put("callback_data", "act_offers_btl")
                })
            })
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "🔄 عروض MIXTE (مكالمات + نت)")
                    put("callback_data", "act_offers_mixte")
                })
            })
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "👨‍👩‍👧‍👦 عروض FAMILY & IZZY")
                    put("callback_data", "act_offers_family")
                })
            })
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "🎁 المكافآت المجانية (1GB و 2GB)")
                    put("callback_data", "act_1gb")
                })
            })
        }

        val markup = JSONObject().apply { put("inline_keyboard", inlineKeyboard) }
        sendMessage(token, chatId, text, getMainMenuMarkup(), markup)
    }

    private suspend fun showCategoryOffers(token: String, chatId: Long, categoryName: String) {
        val offers = AvailableOffers.paidOffers.filter { it.category == categoryName }
        val sb = StringBuilder()
        sb.append("📋 *باقات $categoryName:*\n")
        sb.append("───────────────────\n\n")

        offers.forEach { offer ->
            sb.append("🔹 *${offer.name}*\n")
            sb.append("💰 *السعر:* ${offer.price} | ⏳ *المدة:* ${offer.validity}\n")
            sb.append("📦 *الحجم:* ${offer.dataVolume}\n")
            sb.append("ℹ️ للتفعيل عبر الشريحة: استخدم كود `*720#` أو تطبيق جيزي.\n\n")
        }

        val inlineKeyboard = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "🔙 رجوع لدليل العروض")
                    put("callback_data", "act_offers")
                })
                put(JSONObject().apply {
                    put("text", "🎁 تفعيل مجاني (1GB)")
                    put("callback_data", "act_1gb")
                })
            })
        }

        val markup = JSONObject().apply { put("inline_keyboard", inlineKeyboard) }
        sendMessage(token, chatId, sb.toString(), getMainMenuMarkup(), markup)
    }

    private suspend fun promptReLogin(token: String, chatId: Long, session: TelegramUserSession) {
        val activeAcc = getActiveAccount(session)
        val displayPhone = if (activeAcc != null) apiClient.formatDisplayPhone(activeAcc.phone) else ""
        session.state = TelegramUserState.IDLE
        session.activeToken = ""
        if (activeAcc != null) {
            activeAcc.token = ""
        }
        saveOrUpdateSession(session)

        val msg = "⌛ *انتهت صلاحية جلسة جيزي (بعد مرور 4 ساعات)*\n" +
                (if (displayPhone.isNotBlank()) "📱 *الرقم:* `$displayPhone`\n\n" else "\n") +
                "🔒 تنتهي الجلسة تلقائياً كل 4 ساعات وفق نظام جيزي لحماية أمان خطك.\n" +
                "📱 يرجى كتابة رقم هاتفك لتسجيل الدخول مجدداً واستلام رمز OTP جديد."
        sendMessage(token, chatId, msg, null)
    }

    private suspend fun sendWelcomeMessage(token: String, chatId: Long, firstName: String) {
        val welcome = "╔══════════════════════╗\n" +
                "   🇩🇿  *بوت جيزي للمكافآت والعروض*  🇩🇿\n" +
                "╚══════════════════════╝\n\n" +
                "👋 مرحباً بك يا *$firstName* في بوت تفعيل خدمات ومكافآت جيزي!\n\n" +
                "⚡ *أبرز المميزات:*\n" +
                "• 🎁 *1 جيجا مجاناً* (عرض MGM)\n" +
                "• 🚶 *2 جيجا مشي* (تحدي Walk & Win)\n" +
                "• ⚡ *باقة 3 جيجا كاملة* (1GB + 2GB)\n" +
                "• 💌 *إرسال دعوات رعاية (MGM)* وفحص رصيد الدعوات\n" +
                "• 📱 *إدارة أرقام متعددة:* احفظ 3، 5، أو أي عدد من الأرقام وبدل بينها فوراً\n" +
                "• 📦 *دليل باقات جيزي الشامل* (Speed, BTL, Mixte, Family)\n\n" +
                "───────────────────\n" +
                "📱 *للبدء، اكتب رقم هاتف جيزي الخاص بك:*\n" +
                "مثال: `0770123456` أو `0773527865`"

        sendMessage(token, chatId, welcome, null)
    }

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
     * Clean, polished Main Reply Keyboard for Algerian users.
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
                put(JSONObject().apply { put("text", "📊 فحص دعوات MGM") })
                put(JSONObject().apply { put("text", "💰 استعلام عن الرصيد") })
            })
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "📦 باقات وعروض جيزي") })
                put(JSONObject().apply { put("text", "📱 إدارة أرقامي") })
            })
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "🚪 تسجيل خروج") })
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

    private fun getOfferResultInlineKeyboard(): JSONObject {
        val inlineKeyboard = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "💰 فحص الرصيد")
                    put("callback_data", "act_balance")
                })
                put(JSONObject().apply {
                    put("text", "📊 فحص دعواتي")
                    put("callback_data", "act_mgm_stats")
                })
            })
            put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "📦 باقات وعروض جيزي")
                    put("callback_data", "act_offers")
                })
            })
        }
        return JSONObject().apply {
            put("inline_keyboard", inlineKeyboard)
        }
    }

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
                    put("text", "📦 باقي العروض")
                    put("callback_data", "act_offers")
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
