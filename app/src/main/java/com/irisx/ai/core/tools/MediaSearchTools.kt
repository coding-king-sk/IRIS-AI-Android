package com.irisx.ai.core.tools

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.core.automation.Automator

/**
 * "Koi bhi gana chalao", "Arijit ka song lagao", "YouTube pe Kesariya play karo".
 *
 * Order of attack:
 *  1. Android's own "play from search" intent — YouTube Music / Spotify / any
 *     player answers this and starts playback by itself.
 *  2. In-app YouTube search, then tap the first matching result through the
 *     accessibility service.
 *  3. Plain YouTube results page in the browser.
 */
class PlayMusicTool : IrisTool {
    override val name = "play_music"
    override val description =
        "Play a song, artist or playlist. Works with YouTube, YouTube Music or Spotify."
    override val params = mapOf(
        "query" to "Song, artist or playlist name (empty = something popular)",
        "app" to "Optional: youtube, youtube music or spotify"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val asked = args["query"].orEmpty().trim()
        val vague = asked.isBlank() ||
            Regex("^(koi bhi|kuch bhi|koi|random|anything|kuch)$").matches(asked)
        val query = if (vague) "trending hindi songs" else asked

        val hint = args["app"].orEmpty().lowercase()
        val preferred = when {
            hint.contains("spotify") -> "com.spotify.music"
            hint.contains("music") -> "com.google.android.apps.youtube.music"
            hint.contains("youtube") || hint.contains("yt") -> "com.google.android.youtube"
            else -> null
        }

        // 1. Universal play-from-search.
        if (preferred == null || preferred != "com.google.android.youtube") {
            val play = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .putExtra(SearchManager.QUERY, query)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            if (preferred != null && AppLauncher.installed(context, preferred)) {
                play.setPackage(preferred)
            }
            if (context.startExternal(play)) {
                nudgePlay()
                return ToolResult(true, "\"" + query + "\" chala raha hoon.")
            }
        }

        // 2. YouTube in-app search + tap first result.
        if (AppLauncher.installed(context, "com.google.android.youtube")) {
            val search = Intent(Intent.ACTION_SEARCH)
                .setPackage("com.google.android.youtube")
                .putExtra("query", query)
            if (context.startExternal(search)) {
                if (!Automator.available()) {
                    return ToolResult(
                        true,
                        "YouTube pe \"" + query + "\" search kar diya. Auto-play ke liye Accessibility on karo."
                    )
                }
                Automator.waitForApp("com.google.android.youtube", 9000)
                Automator.sleep(2600)
                val firstWord = query.split(" ").firstOrNull().orEmpty()
                val tapped = Automator.waitAndClick(query) ||
                    (firstWord.length >= 3 && Automator.waitAndClick(firstWord))
                return if (tapped) {
                    ToolResult(true, "\"" + query + "\" YouTube pe chala diya.")
                } else {
                    ToolResult(true, "YouTube pe result khul gaye — pehla video tap kar do.")
                }
            }
        }

        // 3. Browser fallback.
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        )
        return if (context.startExternal(web)) {
            ToolResult(true, "YouTube pe \"" + query + "\" khol diya.")
        } else {
            ToolResult(false, "Koi music app ya browser nahi mila.")
        }
    }

    /** Some players open paused; a play tap helps when accessibility is on. */
    private fun nudgePlay() {
        if (!Automator.available()) return
        Automator.sleep(2200)
        Automator.click("play", "play button", "chalao")
    }
}

/** "YouTube pe cricket highlights dikhao" — search inside YouTube. */
class YoutubeSearchTool : IrisTool {
    override val name = "youtube_search"
    override val description = "Search YouTube for a video and open the results."
    override val params = mapOf("query" to "What to search on YouTube")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val query = args["query"].orEmpty().trim()
        if (query.isBlank()) return ToolResult(false, "YouTube pe kya dhundna hai?")

        val inApp = Intent(Intent.ACTION_SEARCH)
            .setPackage("com.google.android.youtube")
            .putExtra("query", query)
        if (AppLauncher.installed(context, "com.google.android.youtube") &&
            context.startExternal(inApp)
        ) {
            return ToolResult(true, "YouTube pe \"" + query + "\" search kar diya.")
        }
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        )
        return if (context.startExternal(web)) {
            ToolResult(true, "YouTube results khol diye.")
        } else {
            ToolResult(false, "YouTube khul nahi paya.")
        }
    }
}
