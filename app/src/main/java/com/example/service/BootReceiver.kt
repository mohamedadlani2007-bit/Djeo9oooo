package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.TelegramBotPreferences

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val prefs = TelegramBotPreferences(context)
            if (prefs.isAutoStart()) {
                val token = prefs.getBotToken()
                if (token.isNotBlank()) {
                    Log.d(TAG, "Device rebooted / app updated: automatically restoring Telegram Bot Service.")
                    TelegramBotService.start(context, token)
                }
            }
        }
    }
}
