package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.UserAccountEntity
import com.example.ui.theme.DjezzyCyan
import com.example.ui.theme.DjezzyGreen
import com.example.ui.theme.DjezzyRed
import com.example.ui.theme.DjezzyRedDark
import com.example.ui.theme.DjezzyRedLight
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    isRequestingOtp: Boolean,
    isVerifyingOtp: Boolean,
    otpSentForPhone: String?,
    savedAccounts: List<UserAccountEntity>,
    onSendOtp: (String) -> Unit,
    onVerifyOtp: (phone: String, otp: String) -> Unit,
    onSelectSavedAccount: (String) -> Unit,
    onOpenProxySettings: () -> Unit = {},
    onCancelOtp: () -> Unit = {}
) {
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var resendCooldown by remember { mutableIntStateOf(60) }
    val focusManager = LocalFocusManager.current
    val otpFocusRequester = remember { FocusRequester() }

    // 60-second precise countdown timer when OTP is sent
    LaunchedEffect(otpSentForPhone) {
        if (otpSentForPhone != null) {
            resendCooldown = 60
            otpCode = ""
            try {
                otpFocusRequester.requestFocus()
            } catch (_: Exception) {}
            while (resendCooldown > 0) {
                delay(1000)
                resendCooldown--
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9FB))
    ) {
        // High quality Djezzy brand background artwork matching user design
        Image(
            painter = painterResource(id = R.drawable.bg_djezzy_login_1789569688427),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Soft gradient overlay to ensure perfect contrast and readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.88f),
                            Color.White.copy(alpha = 0.94f),
                            Color.White.copy(alpha = 0.98f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Top Status Bar (Language & Slogan matching Djezzy official design)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Slogan "دايما أقرب إليك"
                Text(
                    text = "دايما أقرب إليك",
                    fontWeight = FontWeight.ExtraBold,
                    color = DjezzyRed,
                    fontSize = 15.sp
                )

                // Language Selector Badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "العربية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF222222)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🌐", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Djezzy Play Button Logo with shadow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .shadow(elevation = 14.dp, shape = RoundedCornerShape(26.dp), spotColor = DjezzyRed)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(DjezzyRed, DjezzyRedDark)
                        )
                    )
                    .size(width = 118.dp, height = 88.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "DJEZZY",
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "جيزي",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Welcoming headline
            Text(
                text = if (otpSentForPhone == null) "مرحباً بك في جيزي" else "تأكيد رقم الهاتف",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1E1E1E),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (otpSentForPhone == null)
                    "تواصل . استمتع . إكتشف المزيد"
                else
                    "أدخل الرمز المكون من 6 أرقام المرسل عبر SMS إلى $otpSentForPhone",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Main Phone Input & OTP Verification Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_input_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (otpSentForPhone == null) {
                        // Phone input container styled exactly like the screenshot
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFF7F7F9),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFE4E4EC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // +213 Prefix
                                Text(
                                    text = "+213",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color(0xFF1F1F1F)
                                )

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(Color(0xFFD6D6DF))
                                )

                                // Actual phone number text field
                                BasicTextField(
                                    value = phoneNumber,
                                    onValueChange = { input ->
                                        if (input.length <= 10) {
                                            phoneNumber = input.filter { it.isDigit() }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("phone_number_input"),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF222222),
                                        textAlign = TextAlign.Right
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            focusManager.clearFocus()
                                            if (phoneNumber.length >= 9 && !isRequestingOtp) {
                                                onSendOtp(phoneNumber)
                                            }
                                        }
                                    ),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            if (phoneNumber.isEmpty()) {
                                                Text(
                                                    "أدخل رقم هاتفك (07X...)",
                                                    fontSize = 15.sp,
                                                    color = Color(0xFF9E9EAA),
                                                    textAlign = TextAlign.Right
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Icon(
                                    Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Color(0xFF757580),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Large Red Action Button "متابعة"
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSendOtp(phoneNumber)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(28.dp), spotColor = DjezzyRed)
                                .testTag("send_otp_button"),
                            shape = RoundedCornerShape(28.dp),
                            enabled = phoneNumber.length >= 9 && !isRequestingOtp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DjezzyRed,
                                disabledContainerColor = Color(0xFFE5A8A8)
                            )
                        ) {
                            if (isRequestingOtp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "جاري إرسال الرمز...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "متابعة",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        // OTP Stage with 6-digit PIN view
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onCancelOtp) {
                                Text("تغيير الرقم", fontSize = 13.sp, color = DjezzyRed, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "رمز التحقق (6 أرقام)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF222222)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        ModernOtpCodeBoxes(
                            code = otpCode,
                            length = 6,
                            modifier = Modifier.fillMaxWidth().testTag("otp_boxes_view")
                        )

                        // Real input field
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { input ->
                                if (input.length <= 6) {
                                    otpCode = input.filter { it.isDigit() }
                                    if (otpCode.length == 6 && !isVerifyingOtp) {
                                        onVerifyOtp(otpSentForPhone, otpCode)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .focusRequester(otpFocusRequester)
                                .testTag("otp_code_input"),
                            shape = RoundedCornerShape(16.dp),
                            placeholder = {
                                Text(
                                    "اكتب الرمز هنا",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (otpCode.length == 6 && !isVerifyingOtp) {
                                        onVerifyOtp(otpSentForPhone, otpCode)
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DjezzyRed,
                                unfocusedBorderColor = Color(0xFFD6D6DF),
                                focusedContainerColor = Color(0xFFFAFAFC),
                                unfocusedContainerColor = Color(0xFFFAFAFC)
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Verify Button
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onVerifyOtp(otpSentForPhone, otpCode)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(28.dp), spotColor = DjezzyRed)
                                .testTag("verify_otp_button"),
                            shape = RoundedCornerShape(28.dp),
                            enabled = otpCode.length == 6 && !isVerifyingOtp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DjezzyRed,
                                disabledContainerColor = Color(0xFFE5A8A8)
                            )
                        ) {
                            if (isVerifyingOtp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "جاري التحقق والدخول...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "تأكيد وتسجيل الدخول",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Resend Countdown Timer (1 minute = 60 seconds)
                        if (resendCooldown > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F1F5),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "إعادة إرسال رمز جديد بعد: ",
                                        fontSize = 13.sp,
                                        color = Color(0xFF666677)
                                    )
                                    Text(
                                        text = "00:${resendCooldown.toString().padStart(2, '0')}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = DjezzyRed
                                    )
                                }
                            }
                        } else {
                            TextButton(
                                onClick = { onSendOtp(otpSentForPhone) },
                                enabled = !isRequestingOtp,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = DjezzyRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "إرسال رمز جديد الآن",
                                        color = DjezzyRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick services icons matching the bottom of the design screenshot
            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFDDDDDF)))
                Text(
                    text = "  أو  ",
                    fontSize = 13.sp,
                    color = Color(0xFF888899),
                    fontWeight = FontWeight.Medium
                )
                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFDDDDDF)))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // مسح QR
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF333333))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("مسح QR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                }

                // تفعيل الشريحة
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SimCard, contentDescription = null, tint = Color(0xFF333333))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("تفعيل الشريحة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                }

                // حسابي
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF333333))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("حسابي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                }
            }

            // Saved Accounts quick access (if any exist)
            if (savedAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "الحسابات المحفوظة مسبقاً (دخول مباشر):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF555566)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        savedAccounts.forEach { acc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSavedAccount(acc.phone) }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "دخول",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DjezzyRed
                                )
                                Text(
                                    acc.displayPhone,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF222222)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer Slogan "جيزي ... معاك في كل لحظة"
            Text(
                text = "جيزي ... معاك في كل لحظة",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF444444)
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/**
 * Modern 6-digit PIN boxes layout with responsive highlighted active box
 */
@Composable
private fun ModernOtpCodeBoxes(
    code: String,
    length: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until length) {
            val char = code.getOrNull(i)?.toString() ?: ""
            val isFocused = code.length == i

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isFocused) DjezzyRed.copy(alpha = 0.08f)
                        else Color(0xFFF2F2F6)
                    )
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) DjezzyRed else Color(0xFFD6D6DF),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = char,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isFocused) DjezzyRed else Color(0xFF1E1E1E)
                )
            }
        }
    }
}

