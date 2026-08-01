package com.irisx.ai.service

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.irisx.ai.MainActivity

/**
 * Quick Settings tile: pull the shade down, tap once, start talking.
 * The app never opens — the mic is served by the background service.
 */
class IrisTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = Tile.STATE_INACTIVE
            tile.label = "IRIS AI"
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        if (micGranted) {
            IrisForegroundService.listenNow(applicationContext)
            qsTile?.let { tile ->
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()
            }
            return
        }

        // No mic permission yet — the app has to ask for it.
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
