package com.irisx.ai.core.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Offline index of launchable apps, shared by the Device screen and voice tools.
 *
 * Label matching alone was not enough: people say "insta", "wp", "yt", and some
 * OEM launchers label apps in Hindi. So we first try a hand-written alias map of
 * package names, then fall back to fuzzy label matching.
 */
object AppLauncher {

    data class AppItem(val label: String, val packageName: String)

    /** Spoken name -> candidate package names, best first. */
    private val ALIASES: Map<String, List<String>> = mapOf(
        "youtube" to listOf("com.google.android.youtube", "app.revanced.android.youtube"),
        "yt" to listOf("com.google.android.youtube"),
        "youtube music" to listOf("com.google.android.apps.youtube.music"),
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "wp" to listOf("com.whatsapp"),
        "instagram" to listOf("com.instagram.android"),
        "insta" to listOf("com.instagram.android"),
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
        "fb" to listOf("com.facebook.katana"),
        "telegram" to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
        "snapchat" to listOf("com.snapchat.android"),
        "spotify" to listOf("com.spotify.music"),
        "chrome" to listOf("com.android.chrome"),
        "browser" to listOf("com.android.chrome", "org.mozilla.firefox"),
        "gmail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "google maps" to listOf("com.google.android.apps.maps"),
        "play store" to listOf("com.android.vending"),
        "playstore" to listOf("com.android.vending"),
        "gpay" to listOf("com.google.android.apps.nbu.paisa.user"),
        "google pay" to listOf("com.google.android.apps.nbu.paisa.user"),
        "phonepe" to listOf("com.phonepe.app"),
        "paytm" to listOf("net.one97.paytm"),
        "netflix" to listOf("com.netflix.mediaclient"),
        "hotstar" to listOf("in.startv.hotstar", "in.startv.hotstar.dplus"),
        "amazon" to listOf("in.amazon.mShop.android.shopping"),
        "flipkart" to listOf("com.flipkart.android"),
        "camera" to listOf(
            "com.android.camera", "com.google.android.GoogleCamera",
            "com.oplus.camera", "com.samsung.android.camera", "com.miui.camera"
        ),
        "gallery" to listOf(
            "com.google.android.apps.photos", "com.miui.gallery",
            "com.samsung.android.gallery3d", "com.coloros.gallery3d"
        ),
        "photos" to listOf("com.google.android.apps.photos"),
        "settings" to listOf("com.android.settings"),
        "calculator" to listOf(
            "com.google.android.calculator", "com.android.calculator2", "com.miui.calculator"
        ),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
        "contacts" to listOf("com.google.android.contacts", "com.android.contacts"),
        "messages" to listOf("com.google.android.apps.messaging", "com.android.mms"),
        "zomato" to listOf("com.application.zomato"),
        "swiggy" to listOf("in.swiggy.android"),
        "uber" to listOf("com.ubercab"),
        "ola" to listOf("com.olacabs.customer"),
        "truecaller" to listOf("com.truecaller"),
        "jio" to listOf("com.jio.myjio"),
        "hotspot" to listOf("com.android.settings")
    )

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
            .removeSuffix(" app")
            .removeSuffix(" ko")
            .trim()
        if (q.isEmpty()) return null

        // 1. Alias table — works even when the launcher label is localised.
        aliasPackage(context, q)?.let { pkg ->
            return AppItem(labelOf(context, pkg) ?: q, pkg)
        }

        // 2. Label matching over installed launcher apps.
        val apps = installedApps(context)
        val direct = apps.firstOrNull { it.label.lowercase() == q }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(q) }
            ?: apps.firstOrNull { it.label.lowercase().contains(q) }
            ?: apps.firstOrNull { it.packageName.lowercase().contains(q) }
        if (direct != null) return direct

        // 3. Word-by-word match, so "insta reels kholo" still finds Instagram.
        val words = q.split(" ").filter { it.length >= 3 }
        words.forEach { word ->
            aliasPackage(context, word)?.let { pkg ->
                return AppItem(labelOf(context, pkg) ?: word, pkg)
            }
            apps.firstOrNull { it.label.lowercase().contains(word) }?.let { return it }
        }
        return null
    }

    fun launch(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackageCompat(packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(launch); true }.getOrDefault(false)) return true
        }
        // Not installed / no launcher activity: offer it in the Play Store.
        val store = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(store); true }.getOrDefault(false)
    }

    /** True when the package is actually installed and launchable. */
    fun installed(context: Context, packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackageCompat(packageName) != null

    private fun aliasPackage(context: Context, spoken: String): String? {
        val candidates = ALIASES[spoken] ?: ALIASES.entries
            .firstOrNull { spoken.contains(it.key) }
            ?.value
            ?: return null
        return candidates.firstOrNull { installed(context, it) }
    }

    private fun labelOf(context: Context, pkg: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    private fun PackageManager.getLaunchIntentForPackageCompat(pkg: String): Intent? =
        runCatching { getLaunchIntentForPackage(pkg) }.getOrNull()
}

internal fun Context.startExternal(intent: Intent): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { startActivity(intent); true }.getOrDefault(false)
}
