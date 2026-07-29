package com.irisx.ai.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.irisx.ai.MainActivity
import com.irisx.ai.R

/** Pin a saved macro to the launcher so one tap runs the whole sequence. */
object MacroShortcuts {

    const val EXTRA_MACRO = "iris_macro"

    fun pin(context: Context, macroName: String): Boolean {
        if (macroName.isBlank()) return false
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_MACRO, macroName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val id = "macro_" + macroName.lowercase().replace(" ", "_")
        val info = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(macroName.take(12))
            .setLongLabel(macroName)
            .setIcon(IconCompat.createWithResource(context, R.drawable.iris_logo))
            .setIntent(intent)
            .build()

        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, info, null)
        }.getOrDefault(false)
    }
}
