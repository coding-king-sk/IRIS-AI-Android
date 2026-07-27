package com.irisx.ai.core.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Offline index of launchable apps, shared by the Device screen and voice tools. */
object AppLauncher {

    data class AppItem(val label: String, val packageName: String)

    fun installedApps(context: Context): List<AppItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0).mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                AppItem(info.loadLabel(pm).toString(), pkg)
            }.distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun find(context: Context, query: String): AppItem? {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return null
        val apps = installedApps(context)
        return apps.firstOrNull { it.label.lowercase() == q }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(q) }
            ?: apps.firstOrNull { it.label.lowercase().contains(q) }
            ?: apps.firstOrNull { it.packageName.lowercase().contains(q) }
    }

    fun launch(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackageCompat(packageName)
            ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launch); true }.getOrDefault(false)
    }

    private fun PackageManager.getLaunchIntentForPackageCompat(pkg: String): Intent? =
        runCatching { getLaunchIntentForPackage(pkg) }.getOrNull()
}

internal fun Context.startExternal(intent: Intent): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { startActivity(intent); true }.getOrDefault(false)
}
