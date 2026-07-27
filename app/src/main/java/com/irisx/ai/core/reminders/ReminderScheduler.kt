package com.irisx.ai.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.irisx.ai.service.ReminderReceiver

/** Schedules reminder notifications through AlarmManager, fully offline. */
object ReminderScheduler {

    fun schedule(context: Context, reminder: Reminder): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        return runCatching {
            val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.canScheduleExactAlarms()
            } else {
                true
            }
            if (exact) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.at,
                    pendingIntent(context, reminder)
                )
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, reminder.at, pendingIntent(context, reminder))
            }
            true
        }.getOrDefault(false)
    }

    fun cancel(context: Context, reminder: Reminder) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(pendingIntent(context, reminder)) }
    }

    /** Called after boot so reminders survive a restart. */
    fun rescheduleAll(context: Context) {
        val store = ReminderStore(context)
        runCatching { store.prune() }
        store.upcoming().forEach { schedule(context, it) }
    }

    private fun pendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        intent.action = ReminderReceiver.ACTION_FIRE
        intent.putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
        intent.putExtra(ReminderReceiver.EXTRA_TEXT, reminder.text)
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
