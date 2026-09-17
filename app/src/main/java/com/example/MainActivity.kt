package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.dialogs.ActivationProgressDialog
import com.example.ui.dialogs.HistoryDialog
import com.example.ui.dialogs.OfferDetailsDialog
import com.example.ui.dialogs.ProxySettingsDialog
import com.example.ui.dialogs.SendMgmDialog
import com.example.ui.dialogs.TelegramBotDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DjezzyApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun DjezzyApp(viewModel: MainViewModel) {
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Display snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (activeAccount == null || uiState.showAddAccountSheet) {
                LoginScreen(
                    isRequestingOtp = uiState.isRequestingOtp,
                    isVerifyingOtp = uiState.isVerifyingOtp,
                    otpSentForPhone = uiState.otpSentForPhone,
                    savedAccounts = allAccounts,
                    onSendOtp = { phone -> viewModel.requestOtp(phone) },
                    onVerifyOtp = { phone, otp -> viewModel.verifyOtp(phone, otp) },
                    onSelectSavedAccount = { phone ->
                        viewModel.switchAccount(phone)
                        viewModel.showAddAccount(false)
                    },
                    onOpenProxySettings = { viewModel.showProxyDialog(true) },
                    onOpenTelegramBot = { viewModel.showTelegramBotDialog(true) },
                    onCancelOtp = { viewModel.showAddAccount(false) }
                )
            } else {
                HomeScreen(
                    activeAccount = activeAccount!!,
                    allAccounts = allAccounts,
                    selectedCategory = uiState.selectedCategory,
                    searchQuery = uiState.searchQuery,
                    proxyConfig = uiState.proxyConfig,
                    mainBalance = uiState.mainBalance,
                    isFetchingBalance = uiState.isFetchingBalance,
                    onRefreshBalance = { viewModel.refreshMainBalance() },
                    onCategorySelected = { cat -> viewModel.setCategory(cat) },
                    onSearchChanged = { q -> viewModel.setSearchQuery(q) },
                    onSelectOffer = { offer -> viewModel.openOfferDetails(offer) },
                    onQuickActivateFreeOffer = { offer -> viewModel.activateOffer(offer) },
                    onSwitchAccount = { phone -> viewModel.switchAccount(phone) },
                    onAddNewAccount = { viewModel.showAddAccount(true) },
                    onLogoutAccount = { phone -> viewModel.logoutAccount(phone) },
                    onOpenProxySettings = { viewModel.showProxyDialog(true) },
                    onOpenHistory = { viewModel.showHistoryDialog(true) },
                    isTelegramBotRunning = uiState.isTelegramBotRunning,
                    isTelegramBotWaitingForNetwork = uiState.isTelegramBotWaitingForNetwork,
                    telegramBotUsername = uiState.telegramBotUsername,
                    onOpenTelegramBot = { viewModel.showTelegramBotDialog(true) },
                    onOpenMgmInvite = { viewModel.showMgmInviteDialog(true) }
                )
            }

            // Dialogs
            if (uiState.selectedOfferForDetails != null && activeAccount != null) {
                OfferDetailsDialog(
                    offer = uiState.selectedOfferForDetails!!,
                    currentPhone = activeAccount!!.displayPhone,
                    onDismiss = { viewModel.closeOfferDetails() },
                    onConfirmActivate = { offer -> viewModel.activateOffer(offer) }
                )
            }

            if (uiState.isActivating || uiState.activationResult != null) {
                ActivationProgressDialog(
                    isActivating = uiState.isActivating,
                    offerName = uiState.activatingOfferName,
                    progressStep = uiState.activationProgressStep,
                    result = uiState.activationResult,
                    onDismiss = { viewModel.dismissActivationResult() }
                )
            }

            if (uiState.showProxyDialog) {
                ProxySettingsDialog(
                    currentConfig = uiState.proxyConfig,
                    isTesting = uiState.isTestingConnection,
                    testResult = uiState.connectionTestStatus,
                    onDismiss = { viewModel.showProxyDialog(false) },
                    onSaveConfig = { config -> viewModel.saveProxyConfig(config) },
                    onTestConnection = { viewModel.testConnection() }
                )
            }

            if (uiState.showHistoryDialog) {
                HistoryDialog(
                    historyList = historyList,
                    onDismiss = { viewModel.showHistoryDialog(false) }
                )
            }

            if (uiState.showTelegramBotDialog) {
                TelegramBotDialog(
                    initialToken = uiState.telegramBotToken,
                    isRunning = uiState.isTelegramBotRunning,
                    isWaitingForNetwork = uiState.isTelegramBotWaitingForNetwork,
                    botUsername = uiState.telegramBotUsername,
                    messagesCount = uiState.telegramMessagesCount,
                    logs = uiState.telegramLogs,
                    onDismiss = { viewModel.showTelegramBotDialog(false) },
                    onStartBot = { token -> viewModel.startTelegramBot(token) },
                    onStopBot = { viewModel.stopTelegramBot() },
                    onClearLogs = { viewModel.clearTelegramLogs() },
                    onTestToken = { token, cb -> viewModel.testTelegramToken(token, cb) }
                )
            }

            if (uiState.showMgmInviteDialog && activeAccount != null) {
                SendMgmDialog(
                    senderPhone = activeAccount!!.displayPhone,
                    mgmStatus = uiState.mgmStatus,
                    isFetchingStatus = uiState.isFetchingMgmStatus,
                    onRefreshStatus = { viewModel.refreshMgmStatus() },
                    onDismiss = { viewModel.showMgmInviteDialog(false) },
                    onSendInvite = { receiver -> viewModel.sendMgmInvitation(receiver) }
                )
            }
        }
    }
}

/**
 * Kept for GreetingScreenshotTest and preview compatibility.
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Djezzy") }
}
