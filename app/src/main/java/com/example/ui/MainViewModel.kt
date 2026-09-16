package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.UserAccountEntity
import com.example.data.model.AvailableOffers
import com.example.data.model.Offer
import com.example.data.model.ProxyConfig
import com.example.data.remote.ActivationResult
import com.example.data.repository.DjezzyRepository
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
    val isTestingConnection: Boolean = false,
    val connectionTestStatus: String? = null,
    val selectedCategory: String = "الكل",
    val searchQuery: String = "",
    val snackbarMessage: String? = null,
    val proxyConfig: ProxyConfig = ProxyConfig()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = DjezzyRepository(
        userAccountDao = database.userAccountDao(),
        activationHistoryDao = database.activationHistoryDao()
    )

    val activeAccount: StateFlow<UserAccountEntity?> = repository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAccounts: StateFlow<List<UserAccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyList = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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
