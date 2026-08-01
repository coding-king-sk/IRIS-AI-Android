package com.irisx.ai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.irisx.ai.MainActivity
import com.irisx.ai.R
import com.irisx.ai.service.IrisForegroundService

/**
 * Home screen widget.
 *  - Tap the card  -> open the IRIS command deck.
 *  - Tap the mic   -> talk straight away, without opening the app.
 */
class IrisWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_iris)

            val openIntent = Intent(context, MainActivity::class.java)
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val open = PendingIntent.getActivity(
                context,
                id,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val listenIntent = Intent(context, IrisForegroundService::class.java)
                .setAction(IrisForegroundService.ACTION_LISTEN)
            val listen = PendingIntent.getForegroundService(
                context,
                1000 + id,
                listenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_action, listen)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
