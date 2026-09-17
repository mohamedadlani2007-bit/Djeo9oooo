package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.UserAccountEntity
import com.example.data.model.AvailableOffers
import com.example.data.model.MainBalanceInfo
import com.example.data.model.Offer
import com.example.data.model.ProxyConfig
import com.example.ui.components.CooldownHelper
import com.example.ui.theme.DjezzyAmber
import com.example.ui.theme.DjezzyCyan
import com.example.ui.theme.DjezzyGreen
import com.example.ui.theme.DjezzyRed
import com.example.ui.theme.DjezzyRedDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    activeAccount: UserAccountEntity,
    allAccounts: List<UserAccountEntity>,
    selectedCategory: String,
    searchQuery: String,
    proxyConfig: ProxyConfig,
    mainBalance: MainBalanceInfo? = null,
    isFetchingBalance: Boolean = false,
    onRefreshBalance: () -> Unit = {},
    onCategorySelected: (String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSelectOffer: (Offer) -> Unit,
    onQuickActivateFreeOffer: (Offer) -> Unit,
    onSwitchAccount: (String) -> Unit,
    onAddNewAccount: () -> Unit,
    onLogoutAccount: (String) -> Unit,
    onOpenProxySettings: () -> Unit,
    onOpenHistory: () -> Unit,
    isTelegramBotRunning: Boolean = false,
    telegramBotUsername: String? = null,
    onOpenTelegramBot: () -> Unit = {},
    onOpenMgmInvite: () -> Unit = {}
) {
    var showAccountSwitcherSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val categories = listOf(
        "الكل",
        "المكافآت المجانية",
        "عروض BTL",
        "عروض SPEED",
        "عروض MIXTE",
        "عروض FAMILY",
        "عروض IZZY"
    )

    // Filter offers
    val filteredOffers = AvailableOffers.allOffers.filter { offer ->
        val matchesCategory = when (selectedCategory) {
            "الكل" -> true
            "المكافآت المجانية" -> offer.isFree
            else -> offer.category == selectedCategory
        }
        val matchesSearch = if (searchQuery.isBlank()) true else {
            offer.name.contains(searchQuery, ignoreCase = true) ||
                    offer.price.contains(searchQuery, ignoreCase = true) ||
                    offer.dataVolume.contains(searchQuery, ignoreCase = true) ||
                    offer.category.contains(searchQuery, ignoreCase = true)
        }
        matchesCategory && matchesSearch
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(DjezzyRed, DjezzyRedDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "عروض ومكافآت جيزي",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "تفعيل فوري ومباشر",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenTelegramBot,
                        modifier = Modifier.testTag("open_telegram_button")
                    ) {
                        Box {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = "بوت تيليجرام المحلي",
                                tint = if (isTelegramBotRunning) Color(0xFF229ED9) else MaterialTheme.colorScheme.onSurface
                            )
                            if (isTelegramBotRunning) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2E7D32))
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.testTag("open_history_button")
                    ) {
                        Icon(Icons.Default.History, contentDescription = "سجل التفعيلات")
                    }
                    IconButton(
                        onClick = onOpenProxySettings,
                        modifier = Modifier.testTag("open_proxy_button")
                    ) {
                        Icon(Icons.Default.Lan, contentDescription = "إعدادات الاتصال والبروكسي")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Active Account Banner Card
            item {
                ActiveAccountCard(
                    account = activeAccount,
                    accountCount = allAccounts.size,
                    mainBalance = mainBalance,
                    isFetchingBalance = isFetchingBalance,
                    onRefreshBalance = onRefreshBalance,
                    onOpenSwitcher = { showAccountSwitcherSheet = true },
                    onLogout = { onLogoutAccount(activeAccount.phone) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 2. Local Telegram Bot Status Card
            item {
                TelegramBotQuickCard(
                    isRunning = isTelegramBotRunning,
                    botUsername = telegramBotUsername,
                    onClick = onOpenTelegramBot,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Proxy Active Indicator Banner
            if (proxyConfig.isEnabled) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DjezzyCyan.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DjezzyCyan.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onOpenProxySettings() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "تعديل",
                                style = MaterialTheme.typography.labelSmall,
                                color = DjezzyCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "بروكسي جزائري نشط مؤقتاً 🇩🇿",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = DjezzyCyan
                                    )
                                    Text(
                                        text = "${proxyConfig.host}:${proxyConfig.port} (الجزائر - الجزائر العاصمة)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Lan,
                                    contentDescription = null,
                                    tint = DjezzyCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Free Mobile Data Status & Guidance Banner
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DjezzyGreen.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DjezzyGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "مجاني 100%",
                                color = DjezzyGreen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "الولوج المجاني بدون رصيد",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "فقط شغّل بيانات الهاتف (4G) لشريحة جيزي حتى لو كان رصيدك 0 دج و 0 ميغا",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Right
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DjezzyGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SignalCellularAlt,
                                    contentDescription = null,
                                    tint = DjezzyGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Free Rewards Section Header
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المكافآت المجانية اليومية والأسبوعية",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DjezzyGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "بدون رصيد",
                            color = DjezzyGreen,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 3. Free Rewards Cards
            item {
                FreeRewardsRow(
                    account = activeAccount,
                    onQuickActivate = onQuickActivateFreeOffer,
                    onSelectOffer = onSelectOffer
                )
            }

            // 4. Offers Header & Category Tabs
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "جميع باقات وعروض جيزي",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("offers_search_input"),
                        placeholder = { Text("بحث عن عرض (الاسم، السعر، الحجم...)") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Filter Chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = cat == selectedCategory
                            FilterChip(
                                selected = isSelected,
                                onClick = { onCategorySelected(cat) },
                                label = { Text(cat, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DjezzyRed,
                                    selectedLabelColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 5. Offers List
            if (filteredOffers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد عروض تطابق بحثك",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredOffers, key = { it.code }) { offer ->
                    OfferItemCard(
                        offer = offer,
                        onClick = { onSelectOffer(offer) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // Account Switcher Bottom Sheet
    if (showAccountSwitcherSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSwitcherSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "إدارة أرقام وحسابات جيزي",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                allAccounts.forEach { acc ->
                    val isCurrent = acc.phone == activeAccount.phone
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) DjezzyRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, DjezzyRed) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            if (!isCurrent) {
                                onSwitchAccount(acc.phone)
                                showAccountSwitcherSheet = false
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCurrent) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DjezzyRed
                                ) {
                                    Text(
                                        "الحالي",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        onSwitchAccount(acc.phone)
                                        showAccountSwitcherSheet = false
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("تفعيل")
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = acc.displayPhone,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (acc.token == "EXPIRED") "انتهت الجلسة" else "نشط",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (acc.token == "EXPIRED") DjezzyRed else DjezzyGreen
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = DjezzyRed)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        showAccountSwitcherSheet = false
                        onAddNewAccount()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DjezzyRed)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة رقم هاتف جديد")
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ActiveAccountCard(
    account: UserAccountEntity,
    accountCount: Int,
    mainBalance: MainBalanceInfo? = null,
    isFetchingBalance: Boolean = false,
    onRefreshBalance: () -> Unit = {},
    onOpenSwitcher: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("active_account_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logout button
                IconButton(onClick = onLogout, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = "تسجيل خروج",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }

                // Account info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = account.displayPhone,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (account.token == "EXPIRED") DjezzyRed else DjezzyGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (account.token == "EXPIRED") "بحاجة لتجديد الرمز" else "حساب جيزي نشط",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (account.token == "EXPIRED") DjezzyRed else DjezzyGreen
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DjezzyRed.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = DjezzyRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Balance Section (Fetched from official GET /mobile-api/api/v1/subscribers/main-balance/{msisdn})
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Refresh balance button
                    IconButton(
                        onClick = onRefreshBalance,
                        enabled = !isFetchingBalance,
                        modifier = Modifier.size(34.dp).testTag("refresh_balance_button")
                    ) {
                        if (isFetchingBalance) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = DjezzyRed
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "تحديث الرصيد",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "الرصيد الرئيسي",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = mainBalance?.amount ?: "— دج",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (mainBalance != null) DjezzyGreen else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!mainBalance?.expirationDate.isNullOrBlank()) {
                            Text(
                                text = "صالح إلى: ${mainBalance!!.expirationDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Switch / Add Account Button
            OutlinedButton(
                onClick = onOpenSwitcher,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "$accountCount أرقام",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("إدارة وتبديل الأرقام")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeRewardsRow(
    account: UserAccountEntity,
    onQuickActivate: (Offer) -> Unit,
    onSelectOffer: (Offer) -> Unit
) {
    val (ready1Gb, text1Gb) = CooldownHelper.getRemaining1Gb(account.lastActivation1Gb)
    val (ready2Gb, text2Gb) = CooldownHelper.getRemaining2Gb(account.lastActivation2Gb)
    val (ready3Gb, text3Gb) = CooldownHelper.getRemaining3Gb(account.lastActivation3Gb)

    val freeOffers = AvailableOffers.freeOffers

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1GB Card
        freeOffers.getOrNull(0)?.let { offer ->
            FreeGiftCard(
                offer = offer,
                isReady = ready1Gb,
                statusText = text1Gb,
                badgeColor = if (ready1Gb) DjezzyGreen else DjezzyAmber,
                icon = Icons.Default.Bolt,
                onActivate = { onQuickActivate(offer) },
                onDetails = { onSelectOffer(offer) }
            )
        }

        // 2GB Card
        freeOffers.getOrNull(1)?.let { offer ->
            FreeGiftCard(
                offer = offer,
                isReady = ready2Gb,
                statusText = text2Gb,
                badgeColor = if (ready2Gb) DjezzyGreen else DjezzyAmber,
                icon = Icons.Default.DirectionsWalk,
                onActivate = { onQuickActivate(offer) },
                onDetails = { onSelectOffer(offer) }
            )
        }

        // 3GB Card
        freeOffers.getOrNull(2)?.let { offer ->
            FreeGiftCard(
                offer = offer,
                isReady = ready3Gb,
                statusText = text3Gb,
                badgeColor = if (ready3Gb) DjezzyGreen else DjezzyAmber,
                icon = Icons.Default.Speed,
                onActivate = { onQuickActivate(offer) },
                onDetails = { onSelectOffer(offer) }
            )
        }
    }
}

@Composable
private fun FreeGiftCard(
    offer: Offer,
    isReady: Boolean,
    statusText: String,
    badgeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onActivate: () -> Unit,
    onDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDetails() }
            .testTag("free_gift_card_${offer.code}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Activate button
            Button(
                onClick = onActivate,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReady) DjezzyGreen else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isReady) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                enabled = isReady
            ) {
                Text(if (isReady) "تفعيل" else "انتظر", fontWeight = FontWeight.Bold)
            }

            // Info & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = statusText,
                                color = badgeColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = offer.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${offer.validity} • ${offer.bonus}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isReady) DjezzyGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isReady) DjezzyGreen else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferItemCard(
    offer: Offer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("offer_card_${offer.code}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Price badge & click
            Column(horizontalAlignment = Alignment.Start) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (offer.isFree) DjezzyGreen.copy(alpha = 0.15f) else DjezzyRed.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = offer.price,
                        color = if (offer.isFree) DjezzyGreen else DjezzyRed,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "عرض التفاصيل",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Info
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = offer.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = offer.validity,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = offer.dataVolume,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun TelegramBotQuickCard(
    isRunning: Boolean,
    botUsername: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isRunning) Color(0xFF229ED9).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isRunning) Color(0xFF229ED9).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("telegram_quick_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isRunning) Color(0xFF0088CC) else MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = if (isRunning) "إدارة البوت" else "تشغيل البوت",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E7D32))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isRunning) "بوت تيليجرام شغال (@${botUsername ?: "DjezzyBot"})" else "بوت تيليجرام المحلي",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) Color(0xFF0088CC) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (isRunning) "يستقبل طلبات التفعيل ويرسل OTP محلياً بدون بروكسي" else "شغل بوت محلي يستقبل الأكواد ويرسل دعوات MGM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF229ED9).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color(0xFF0088CC),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
