package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.TelegramBotPreferences
import com.example.data.remote.LocalTelegramBotEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Foreground Service to keep the Local Telegram Bot active indefinitely in the background,
 * even when the user closes or backgrounds the app, until the user explicitly stops it.
 */
class TelegramBotService : Service() {

    companion object {
        private const val TAG = "TelegramBotService"
        const val CHANNEL_ID = "djezzy_telegram_bot_fg_channel"
        const val NOTIFICATION_ID = 90210
        const val ACTION_START = "com.example.service.ACTION_START_BOT"
        const val ACTION_STOP = "com.example.service.ACTION_STOP_BOT"
        const val EXTRA_TOKEN = "extra_bot_token"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun start(context: Context, token: String) {
            val intent = Intent(context, TelegramBotService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TOKEN, token)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TelegramBotService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            Log.d(TAG, "Stopping Foreground Bot Service requested by user.")
            stopBotAndSelf()
            return START_NOT_STICKY
        }

        val token = intent?.getStringExtra(EXTRA_TOKEN)
            ?.takeIf { it.isNotBlank() }
            ?: TelegramBotPreferences(this).getBotToken()

        if (token.isBlank()) {
            Log.e(TAG, "No Telegram Bot token provided. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        isServiceRunning = true
        val notification = buildForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val engine = LocalTelegramBotEngine.getInstance(applicationContext)
        engine.start(token, serviceScope)

        serviceScope.launch {
            engine.isWaitingForNetwork.collect { isWaiting ->
                if (isServiceRunning) {
                    val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    notifManager?.notify(NOTIFICATION_ID, buildForegroundNotification(isWaiting))
                }
            }
        }

        // START_STICKY ensures Android restarts the service if it's killed under extreme memory pressure
        return START_STICKY
    }

    private fun buildForegroundNotification(isWaitingForNetwork: Boolean = false): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, TelegramBotService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (isWaitingForNetwork) {
            "🤖 بوت جيزي شغال بالخلفية (بـ 0 نت 📡)"
        } else {
            "🤖 بوت جيزي شغال بالخلفية 🟢"
        }

        val content = if (isWaitingForNetwork) {
            "البوت شغال دائماً في وضع الاستعداد بـ 0 نت ولن ينطفئ، بانتظار اتصال التيليجرام."
        } else {
            "البوت متصل ويعمل باستمرار في الخلفية بدون انقطاع، يستقبل وينفذ التفعيلات فوراً."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(content)
            )
            .setOngoing(true)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🛑 إيقاف البوت", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة بوت جيزي في الخلفية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار تشغيل بوت تيليجرام المحلي بشكل مستمر في الخلفية"
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DjezzyRewards:TelegramBotWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }
    }

    private fun stopBotAndSelf() {
        isServiceRunning = false
        try {
            LocalTelegramBotEngine.getInstance(applicationContext).stop()
        } catch (_: Exception) {}

        releaseWakeLock()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBotAndSelf()
        serviceScope.cancel()
    }
}
