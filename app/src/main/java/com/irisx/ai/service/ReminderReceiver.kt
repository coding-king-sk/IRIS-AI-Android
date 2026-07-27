package com.irisx.ai.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.irisx.ai.IrisApp
import com.irisx.ai.MainActivity
import com.irisx.ai.R
import com.irisx.ai.core.reminders.ReminderStore

/** Fires the reminder notification when its time arrives. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_ID).orEmpty()
        val raw = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        val text = if (raw.isBlank()) "Reminder" else raw

        val openApp = Intent(context, MainActivity::class.java)
        openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context,
            id.hashCode(),
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, IrisApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.iris_logo)
            .setContentTitle("IRIS reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { manager?.notify(2000 + (id.hashCode() and 4095), notification) }
        if (id.isNotEmpty()) runCatching { ReminderStore(context).delete(id) }
    }

    companion object {
        const val ACTION_FIRE = "com.irisx.ai.REMINDER_FIRE"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TEXT = "reminder_text"
    }
}
