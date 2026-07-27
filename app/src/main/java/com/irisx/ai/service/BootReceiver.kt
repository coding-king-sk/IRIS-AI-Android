package com.irisx.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the wake-word layer back after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching { IrisForegroundService.start(context) }
        }
    }
}
