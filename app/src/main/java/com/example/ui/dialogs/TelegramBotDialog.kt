package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.TelegramLogItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val TelegramBlue = Color(0xFF229ED9)
val TelegramBlueDark = Color(0xFF0088CC)
val TerminalBg = Color(0xFF1E1E24)

@Composable
fun TelegramBotDialog(
    initialToken: String,
    isRunning: Boolean,
    isWaitingForNetwork: Boolean = false,
    botUsername: String?,
    messagesCount: Int,
    logs: List<TelegramLogItem>,
    onDismiss: () -> Unit,
    onStartBot: (String) -> Unit,
    onStopBot: () -> Unit,
    onClearLogs: () -> Unit,
    onTestToken: (String, (Boolean, String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var tokenInput by remember { mutableStateOf(initialToken) }
    var isTestingToken by remember { mutableStateOf(false) }
    var testFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp)
                .padding(vertical = 16.dp)
                .testTag("telegram_bot_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(TelegramBlue, TelegramBlueDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "بوت تيليجرام المحلي",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val statusText = if (isRunning) {
                                if (isWaitingForNetwork) "🟢 شغال (0 نت 📡 في وضع الاستعداد)"
                                else "🟢 شغال في الخلفية باستمرار (بدون توقف)"
                            } else {
                                "⚪ متوقف عن العمل"
                            }
                            val statusColor = if (isRunning) {
                                if (isWaitingForNetwork) Color(0xFFE65100) else Color(0xFF2E7D32)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_telegram_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Info Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = TelegramBlue.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TelegramBlue.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = TelegramBlueDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "يعمل البوت كخدمة خلفية مستمرة للأبد حتى توقفه بنفسك! يبقى شغالاً حتى بـ 0 نت وبدون توقف، ويحفظ جميع الجلسات تلقائياً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Token Field
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it
                        testFeedback = null
                    },
                    label = { Text("توكن البوت (Bot API Token)") },
                    placeholder = { Text("1234567890:AAHxxxx...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("telegram_token_field"),
                    singleLine = true,
                    enabled = !isRunning,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tokenInput.isNotBlank() && !isRunning) {
                                IconButton(onClick = { tokenInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        tokenInput = clip.getItemAt(0).text.toString().trim()
                                    }
                                },
                                enabled = !isRunning
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "لصق")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    shape = RoundedCornerShape(12.dp)
                )

                // Feedback from test
                if (testFeedback != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = (if (testFeedback!!.first) "✅ " else "❌ ") + testFeedback!!.second,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testFeedback!!.first) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons: Test / Start / Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isRunning) {
                        OutlinedButton(
                            onClick = {
                                if (tokenInput.isBlank()) {
                                    Toast.makeText(context, "أدخل التوكن أولاً", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                isTestingToken = true
                                onTestToken(tokenInput) { ok, msg ->
                                    isTestingToken = false
                                    testFeedback = Pair(ok, msg)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isTestingToken && tokenInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isTestingToken) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("فحص التوكن")
                            }
                        }

                        Button(
                            onClick = { onStartBot(tokenInput) },
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("start_telegram_bot_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlueDark),
                            enabled = tokenInput.isNotBlank()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تشغيل البوت")
                        }
                    } else {
                        // Bot is running: show link and Stop button
                        if (botUsername != null) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$botUsername"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "تعذر فتح تيليجرام", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("@$botUsername", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Button(
                            onClick = onStopBot,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stop_telegram_bot_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إيقاف البوت")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats and Log Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "سجل النشاط المباشر (${logs.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (messagesCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "$messagesCount رسالة",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    if (logs.isNotEmpty()) {
                        TextButton(
                            onClick = onClearLogs,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مسح", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Terminal Log Console
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    color = TerminalBg
                ) {
                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier.padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isRunning) "البوت قيد الاستماع... في انتظار رسائل المستخدمين عبر تيليجرام 📡"
                                else "اضغط على 'تشغيل البوت' للبدء في معالجة طلبات المستخدمين محلياً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            items(logs, key = { it.id }) { log ->
                                val time = timeFormat.format(Date(log.timestamp))
                                val tagColor = when {
                                    log.isError -> Color(0xFFFF5252)
                                    log.isSuccess -> Color(0xFF69F0AE)
                                    log.tag == "رسالة" -> TelegramBlue
                                    else -> Color(0xFFFFD740)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        "[$time]",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "[${log.tag}]",
                                        color = tagColor,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        log.message,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Hint
                Text(
                    "💡 كيف تحصل على التوكن؟ افتح @BotFather في تيليجرام، أرسل /newbot واختر اسماً ومعرفاً للبوت، ثم انسخ الـ API Token والصقه هنا.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
