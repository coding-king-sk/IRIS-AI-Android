package com.irisx.ai.core.tools

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.service.IrisNotificationListener

/**
 * Real transport control for Spotify / YouTube Music / any player.
 *
 * Media keys alone are unreliable (they go wherever Android feels like), so we
 * talk to the actual MediaSession of the player. That needs notification-listener
 * access, which IRIS already asks for; without it we fall back to media keys.
 */
class MusicControlTool : IrisTool {
    override val name = "music_control"
    override val description =
        "Play, pause, skip or go back in Spotify, YouTube Music or whichever player is running."
    override val params = mapOf(
        "action" to "play, pause, next, previous or stop",
        "app" to "spotify, youtube music (optional)"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val action = args["action"].orEmpty().lowercase().ifBlank { "play" }
        val app = args["app"].orEmpty().lowercase()
        val controller = MediaSessions.controller(context, app)

        if (controller != null) {
            val controls = controller.transportControls
            val ok = runCatching {
                when (action) {
                    "pause" -> controls.pause()
                    "next", "skip" -> controls.skipToNext()
                    "previous", "prev", "back" -> controls.skipToPrevious()
                    "stop" -> controls.stop()
                    else -> controls.play()
                }
            }.isSuccess
            if (ok) {
                val where = MediaSessions.label(controller.packageName)
                val message = when (action) {
                    "pause" -> "Rok diya (" + where + ")."
                    "next", "skip" -> "Agla gana laga diya (" + where + ")."
                    "previous", "prev", "back" -> "Pichla gana wapas laga diya (" + where + ")."
                    "stop" -> "Band kar diya (" + where + ")."
                    else -> "Chalu kar diya (" + where + ")."
                }
                return ToolResult(true, message)
            }
        }

        // Fallback: system media key.
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult(false, "Koi player chal nahi raha.")
        val keyCode = when (action) {
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY
        }
        return runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            ToolResult(true, "Media " + action + " bhej diya.")
        }.getOrElse { ToolResult(false, "Player tak command nahi pahunchi.") }
    }
}

/** "Kya baj raha hai?" */
class NowPlayingTool : IrisTool {
    override val name = "now_playing"
    override val description = "Say which song is currently playing."

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val controller = MediaSessions.controller(context, "")
            ?: return ToolResult(true, "Abhi kuch baj nahi raha.")
        val metadata = runCatching { controller.metadata }.getOrNull()
            ?: return ToolResult(true, "Player chal raha hai par gaane ka naam nahi mil raha.")
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val where = MediaSessions.label(controller.packageName)
        if (title.isBlank()) return ToolResult(true, where + " pe kuch chal raha hai.")
        val who = if (artist.isBlank()) "" else " \u2014 " + artist
        return ToolResult(true, where + " pe chal raha hai: " + title + who)
    }
}

internal object MediaSessions {

    fun controller(context: Context, app: String): MediaController? {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return null
        val component = ComponentName(context, IrisNotificationListener::class.java)
        val sessions = runCatching { manager.getActiveSessions(component) }
            .getOrNull()
            .orEmpty()
        if (sessions.isEmpty()) return null

        val wanted = when {
            app.contains("spotify") -> "com.spotify.music"
            app.contains("yt music") || app.contains("youtube music") ->
                "com.google.android.apps.youtube.music"
            app.contains("youtube") -> "com.google.android.youtube"
            else -> ""
        }
        if (wanted.isNotBlank()) {
            sessions.firstOrNull { it.packageName == wanted }?.let { return it }
        }
        sessions.firstOrNull { session ->
            runCatching { session.playbackState?.state == PlaybackState.STATE_PLAYING }
                .getOrDefault(false)
        }?.let { return it }
        return sessions.firstOrNull()
    }

    fun label(packageName: String?): String = when {
        packageName == null -> "player"
        packageName.contains("spotify") -> "Spotify"
        packageName.contains("youtube.music") -> "YouTube Music"
        packageName.contains("youtube") -> "YouTube"
        packageName.contains("gaana") -> "Gaana"
        packageName.contains("wynk") -> "Wynk"
        packageName.contains("jiosaavn") || packageName.contains("saavn") -> "JioSaavn"
        else -> "player"
    }
}
