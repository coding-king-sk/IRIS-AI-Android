package com.irisx.ai.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Keeps a small in-memory ring of recent notifications for voice read-out. */
class IrisNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val app = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

        synchronized(buffer) {
            buffer.add(app + ": " + listOf(title, text).filter { it.isNotBlank() }.joinToString(" — "))
            while (buffer.size > 20) buffer.removeAt(0)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    companion object {
        private val buffer = mutableListOf<String>()

        fun recent(): List<String> = synchronized(buffer) { buffer.reversed().toList() }
    }
}
