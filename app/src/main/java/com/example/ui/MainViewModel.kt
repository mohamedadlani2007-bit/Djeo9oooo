package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.TelegramBotPreferences
import com.example.data.local.UserAccountEntity
import com.example.data.model.AvailableOffers
import com.example.data.model.MainBalanceInfo
import com.example.data.model.MgmStatusInfo
import com.example.data.model.Offer
import com.example.data.model.ProxyConfig
import com.example.data.model.TelegramLogItem
import com.example.data.remote.ActivationResult
import com.example.data.repository.DjezzyRepository
import com.example.service.TelegramBotService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val isRequestingOtp: Boolean = false,
    val isVerifyingOtp: Boolean = false,
    val otpSentForPhone: String? = null,
    val isActivating: Boolean = false,
    val activatingOfferName: String? = null,
    val activationProgressStep: String = "",
    val activationResult: ActivationResult? = null,
    val selectedOfferForDetails: Offer? = null,
    val showAddAccountSheet: Boolean = false,
    val showProxyDialog: Boolean = false,
    val showHistoryDialog: Boolean = false,
    val showTelegramBotDialog: Boolean = false,
    val showMgmInviteDialog: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionTestStatus: String? = null,
    val selectedCategory: String = "الكل",
    val searchQuery: String = "",
    val snackbarMessage: String? = null,
    val proxyConfig: ProxyConfig = ProxyConfig(),
    val mainBalance: MainBalanceInfo? = null,
    val isFetchingBalance: Boolean = false,
    // Telegram Bot state
    val telegramBotToken: String = "",
    val isTelegramBotRunning: Boolean = false,
    val isTelegramBotWaitingForNetwork: Boolean = false,
    val telegramBotUsername: String? = null,
    val telegramMessagesCount: Int = 0,
    val telegramLogs: List<TelegramLogItem> = emptyList(),
    // MGM Invite state
    val isSendingMgmInvite: Boolean = false,
    val mgmStatus: MgmStatusInfo? = null,
    val isFetchingMgmStatus: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val botPrefs = TelegramBotPreferences(application)
    private val repository = DjezzyRepository(
        userAccountDao = database.userAccountDao(),
        activationHistoryDao = database.activationHistoryDao(),
        context = application
    )

    val activeAccount: StateFlow<UserAccountEntity?> = repository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAccounts: StateFlow<List<UserAccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyList = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(UiState(telegramBotToken = botPrefs.getBotToken()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            activeAccount.collect { account ->
                if (account != null && account.token.isNotBlank() && account.token != "EXPIRED") {
                    refreshMainBalance()
                } else {
                    _uiState.update { it.copy(mainBalance = null) }
                }
            }
        }

        // Collect Bot state
        viewModelScope.launch {
            repository.botEngine.isRunning.collect { running ->
                _uiState.update { it.copy(isTelegramBotRunning = running) }
            }
        }
        viewModelScope.launch {
            repository.botEngine.isWaitingForNetwork.collect { isWaiting ->
                _uiState.update { it.copy(isTelegramBotWaitingForNetwork = isWaiting) }
            }
        }
        viewModelScope.launch {
            repository.botEngine.botUsername.collect { username ->
                _uiState.update { it.copy(telegramBotUsername = username) }
            }
        }
        viewModelScope.launch {
            repository.botEngine.botLogs.collect { logs ->
                _uiState.update { it.copy(telegramLogs = logs) }
            }
        }
        viewModelScope.launch {
            repository.botEngine.messagesCount.collect { count ->
                _uiState.update { it.copy(telegramMessagesCount = count) }
            }
        }

        // Auto-start bot service if previously enabled by user
        val savedToken = botPrefs.getBotToken()
        if (botPrefs.isAutoStart() && savedToken.isNotBlank()) {
            TelegramBotService.start(getApplication(), savedToken)
        }
    }

    fun refreshMainBalance() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingBalance = true) }
            val result = repository.fetchMainBalance()
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isFetchingBalance = false,
                        mainBalance = result.getOrNull()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isFetchingBalance = false)
                }
            }
        }
    }

    fun setCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openOfferDetails(offer: Offer) {
        _uiState.update { it.copy(selectedOfferForDetails = offer) }
    }

    fun closeOfferDetails() {
        _uiState.update { it.copy(selectedOfferForDetails = null) }
    }

    fun showAddAccount(show: Boolean) {
        _uiState.update { it.copy(showAddAccountSheet = show, otpSentForPhone = null) }
    }

    fun showProxyDialog(show: Boolean) {
        _uiState.update { it.copy(showProxyDialog = show) }
    }

    fun showHistoryDialog(show: Boolean) {
        _uiState.update { it.copy(showHistoryDialog = show) }
    }

    fun dismissActivationResult() {
        _uiState.update { it.copy(activationResult = null, isActivating = false) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun requestOtp(phone: String) {
        if (phone.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "يرجى كتابة رقم الهاتف أولاً") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRequestingOtp = true) }
            val result = repository.requestOtp(phone)
            _uiState.update {
                it.copy(
                    isRequestingOtp = false,
                    otpSentForPhone = if (result.isSuccess) phone else null,
                    snackbarMessage = if (result.isSuccess) {
                        "تم إرسال رمز التحقق عبر SMS إلى هاتفك بنجاح"
                    } else {
                        "فشل إرسال الرمز: ${result.exceptionOrNull()?.localizedMessage ?: "تحقق من الرقم والشبكة"}"
                    }
                )
            }
        }
    }

    fun verifyOtp(phone: String, otp: String) {
        if (otp.length != 6) {
            _uiState.update { it.copy(snackbarMessage = "الرمز يجب أن يتكون من 6 أرقام") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingOtp = true) }
            val result = repository.verifyOtpAndSaveAccount(phone, otp)
            _uiState.update {
                it.copy(
                    isVerifyingOtp = false,
                    otpSentForPhone = if (result.isSuccess) null else it.otpSentForPhone,
                    showAddAccountSheet = if (result.isSuccess) false else it.showAddAccountSheet,
                    snackbarMessage = if (result.isSuccess) {
                        "تم تسجيل الدخول وحفظ الرقم بنجاح!"
                    } else {
                        "فشل التحقق: ${result.exceptionOrNull()?.localizedMessage ?: "تأكد من صحة الرمز"}"
                    }
                )
            }
        }
    }

    fun switchAccount(phone: String) {
        viewModelScope.launch {
            repository.switchAccount(phone)
            _uiState.update { it.copy(snackbarMessage = "تم التبديل للرقم ${repository.formatDisplayPhone(phone)}") }
        }
    }

    fun logoutAccount(phone: String) {
        viewModelScope.launch {
            repository.logoutAccount(phone)
            _uiState.update { it.copy(snackbarMessage = "تم تسجيل الخروج بنجاح") }
        }
    }

    fun activateOffer(offer: Offer) {
        closeOfferDetails()
        if (offer.code == "MGM_INVITE") {
            showMgmInviteDialog(true)
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isActivating = true,
                    activatingOfferName = offer.name,
                    activationProgressStep = "جاري بدء الاتصال بالخادم...",
                    activationResult = null
                )
            }

            val result = repository.activateOffer(offer) { step ->
                _uiState.update { it.copy(activationProgressStep = step) }
            }

            _uiState.update {
                it.copy(
                    isActivating = false,
                    activationResult = result
                )
            }
        }
    }

    fun showTelegramBotDialog(show: Boolean) {
        _uiState.update { it.copy(showTelegramBotDialog = show) }
    }

    fun showMgmInviteDialog(show: Boolean) {
        _uiState.update { it.copy(showMgmInviteDialog = show) }
        if (show) {
            refreshMgmStatus()
        }
    }

    fun refreshMgmStatus() {
        val current = activeAccount.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingMgmStatus = true) }
            val result = repository.getMgmStatus()
            _uiState.update {
                it.copy(
                    isFetchingMgmStatus = false,
                    mgmStatus = result.getOrNull()
                )
            }
        }
    }

    fun startTelegramBot(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "يرجى كتابة توكن البوت أولاً") }
            return
        }
        botPrefs.saveBotToken(cleanToken)
        botPrefs.setAutoStart(true)
        _uiState.update { it.copy(telegramBotToken = cleanToken) }
        TelegramBotService.start(getApplication(), cleanToken)
        _uiState.update { it.copy(snackbarMessage = "تم تشغيل البوت! سيبقى شغالاً باستمرار حتى بـ 0 نت.") }
    }

    fun stopTelegramBot() {
        botPrefs.setAutoStart(false)
        TelegramBotService.stop(getApplication())
        _uiState.update { it.copy(snackbarMessage = "تم إيقاف تشغيل البوت يدوياً") }
    }

    fun clearTelegramLogs() {
        repository.botEngine.clearLogs()
    }

    fun testTelegramToken(token: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.botEngine.testToken(token)
            if (res.isSuccess) {
                onResult(true, res.getOrThrow())
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "توكن غير صالح")
            }
        }
    }

    fun sendMgmInvitation(receiverPhone: String) {
        val current = activeAccount.value
        if (current == null) {
            _uiState.update { it.copy(snackbarMessage = "يرجى تسجيل الدخول أولاً") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isActivating = true,
                    activatingOfferName = "إرسال دعوة رعاية (MGM)",
                    activationProgressStep = "جاري إرسال الدعوة إلى $receiverPhone عبر سيرفر جيزي...",
                    activationResult = null,
                    showMgmInviteDialog = false
                )
            }

            val result = repository.sendMgmInvitation(receiverPhone)
            _uiState.update {
                it.copy(
                    isActivating = false,
                    activationResult = result
                )
            }
            refreshMgmStatus()
        }
    }

    fun saveProxyConfig(config: ProxyConfig) {
        _uiState.update { it.copy(proxyConfig = config) }
        repository.updateProxyConfig(config)
        _uiState.update { it.copy(snackbarMessage = if (config.isEnabled) "تم تفعيل إعدادات البروكسي" else "تم تعطيل البروكسي (اتصال محلي مباشر)") }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, connectionTestStatus = "جاري فحص الاتصال...") }
            val (ok, message) = repository.testConnection()
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    connectionTestStatus = message
                )
            }
        }
    }
}
