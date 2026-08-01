package com.irisx.ai.core.agent

/**
 * Fast lane for the commands people actually use. Runs BEFORE LocalIntentParser.
 *
 * Design rule after real-world testing: people do NOT speak clean commands.
 * They say "youtube kholo or search ker song" or "open whatsapp kais ko bolo
 * ki me aa raha hu". So the app-specific routes below are intentionally
 * forgiving: we detect the APP first, strip the filler words, and then look at
 * what is left over to decide the action.
 */
object ExtraIntentParser {

    fun parse(raw: String): ToolCall? {
        val t = raw.lowercase()
            .trim()
            .replace(Regex("""\s+"""), " ")
            .removeSuffix(".")
            .removeSuffix("?")
            .trim()
        if (t.isBlank()) return null

        // ---- Voice / wake word engine --------------------------------------
        if (Regex("""wake\s*word\s*(?:ka\s*)?(?:status|haal|kaisa)""").containsMatchIn(t)) {
            return ToolCall("neural_wake_status", emptyMap())
        }
        if (Regex("""(?:neural|openwakeword|open wake word|smart|new)\s*wake\s*word""").containsMatchIn(t) ||
            Regex("""wake\s*word\s*(?:model\s*)?(?:setup|install|download|chalu|on|enable|theek)""").containsMatchIn(t)
        ) {
            val model = OpenWakeWordPhrases.firstOrNull { t.contains(it) }.orEmpty()
            return ToolCall("neural_wake_setup", mapOf("model" to model))
        }
        if (Regex("""(?:neural|ai|nayi|apni|iris)\s*(?:ki\s*)?voice\s*(?:ka\s*)?(?:status|haal)""").containsMatchIn(t)) {
            return ToolCall("neural_voice_status", emptyMap())
        }
        if (Regex("""(?:neural|ai|nayi|apni|dost jaisi|achhi)\s*(?:ki\s*)?voice""").containsMatchIn(t) ||
            Regex("""voice\s*(?:model\s*)?(?:setup|install|download|badlo|badal do|change karo)""").containsMatchIn(t)
        ) {
            val off = Regex("""\b(?:off|band|bandh|hatao|delete)\b""").containsMatchIn(t)
            return ToolCall("neural_voice_setup", mapOf("action" to if (off) "off" else "on"))
        }

        // ---- Everyday toggles ----------------------------------------------
        if (Regex("""(?:do not disturb|dnd|disturb)""").containsMatchIn(t)) {
            val off = Regex("""\b(?:off|band|bandh|hata|hatao|nikal)""").containsMatchIn(t)
            return ToolCall("dnd", mapOf("state" to if (off) "off" else "on"))
        }
        if (Regex("""^(?:phone\s*)?silent(?:\s*mode)?(?:\s*(?:on|karo|kar do|chalu|lagao|kardo))?$""").containsMatchIn(t)) {
            return ToolCall("ringer", mapOf("mode" to "silent"))
        }
        if (Regex("""^(?:phone\s*)?vibrat\w*(?:\s*(?:pe|par|mode))?(?:\s*(?:on|karo|kar do|lagao|daal do))?$""").containsMatchIn(t)) {
            return ToolCall("ringer", mapOf("mode" to "vibrate"))
        }
        if (Regex("""^(?:normal mode|general mode|silent (?:off|hatao|band karo)|awaaz (?:on|wapas)(?: karo)?|sound (?:on|wapas)(?: karo)?|ringtone on)$""").containsMatchIn(t)) {
            return ToolCall("ringer", mapOf("mode" to "normal"))
        }
        if (Regex("""bluetooth""").containsMatchIn(t) &&
            Regex("""\b(?:on|off|chalu|band|bandh|start|kar do|karo|lagao|hatao)\b""").containsMatchIn(t)
        ) {
            val off = Regex("""\b(?:off|band|bandh|hata|hatao)""").containsMatchIn(t)
            return ToolCall("bluetooth", mapOf("state" to if (off) "off" else "on"))
        }
        if (Regex("""hotspot|tethering""").containsMatchIn(t)) {
            val off = Regex("""\b(?:off|band|bandh|hata|hatao)""").containsMatchIn(t)
            return ToolCall("hotspot", mapOf("state" to if (off) "off" else "on"))
        }

        // ---- Location sharing (before generic "X ko ... bhejo") ------------
        Regex("""^(?:meri\s*)?(?:live\s*)?location\s*(?:bhejo|bhej do|share karo|send karo|do)\s+(.+?)(?:\s+ko)?$""")
            .find(t)?.let {
                return ToolCall("location_share", mapOf("contact" to it.groupValues[1].trim()))
            }
        Regex("""^(.+?)\s+ko\s+(?:meri\s*)?(?:live\s*)?location\s*(?:bhejo|bhej do|share karo|send karo|do)$""")
            .find(t)?.let {
                return ToolCall("location_share", mapOf("contact" to it.groupValues[1].trim()))
            }

        // ---- WhatsApp (app detected first, then the leftover) --------------
        if (Regex("""\b(?:whatsapp|whats app|watsapp|wattsapp|wp)\b""").containsMatchIn(t)) {
            val body = strip(t, Regex("""\b(?:whatsapp|whats app|watsapp|wattsapp|wp)\b"""))
            messageParts(body)?.let { return whatsapp(it.first, it.second) }
            if (body.isBlank() || Regex("""^(?:kholo|open|khol do|chalu|jao)?$""").matches(body)) {
                return ToolCall("open_app", mapOf("query" to "whatsapp"))
            }
        }

        // ---- Instagram ------------------------------------------------------
        if (Regex("""\b(?:instagram|insta|ig)\b""").containsMatchIn(t)) {
            if (Regex("""reel|reels|scroll|swipe|feed""").containsMatchIn(t)) {
                return ToolCall("reels", mapOf("app" to "instagram"))
            }
            val body = strip(t, Regex("""\b(?:instagram|insta|ig)\b"""))
            messageParts(body)?.let {
                return ToolCall(
                    "instagram_send",
                    mapOf("user" to it.first, "message" to it.second)
                )
            }
            if (body.isBlank()) return ToolCall("open_app", mapOf("query" to "instagram"))
        }

        // ---- Telegram -------------------------------------------------------
        if (Regex("""\b(?:telegram|tg)\b""").containsMatchIn(t)) {
            val body = strip(t, Regex("""\b(?:telegram|tg)\b"""))
            messageParts(body)?.let {
                return ToolCall(
                    "telegram_send",
                    mapOf("user" to it.first, "message" to it.second)
                )
            }
            if (body.isBlank()) return ToolCall("open_app", mapOf("query" to "telegram"))
        }

        // ---- Reels / shorts scrolling ---------------------------------------
        if (Regex("""^(?:agli|agla|next|aage wali|aage)\s*(?:reel|reels|video|short|shorts)$""").containsMatchIn(t) ||
            Regex("""^(?:reel|reels)\s*(?:scroll|chalao|dikhao|badlo|next)""").containsMatchIn(t)
        ) {
            return ToolCall("reels", mapOf("action" to "next"))
        }

        // ---- Music transport (pause / next / previous / resume) ------------
        if (Regex("""^(?:gana|gaana|song|music|track|isko|ise|iska)?\s*(?:ko\s*)?(?:rok do|roko|ruk jao|pause karo|pause kar do|pause|band karo|band kar do|bandh karo)$""").containsMatchIn(t)) {
            return ToolCall("music_control", mapOf("action" to "pause", "app" to musicApp(t)))
        }
        if (Regex("""^(?:agla|next|aage wala|dusra)\s*(?:gana|gaana|song|track)?$|^(?:gana|gaana|song)\s*(?:badlo|badal do|change karo|skip karo)$|^skip$""").containsMatchIn(t)) {
            return ToolCall("music_control", mapOf("action" to "next", "app" to musicApp(t)))
        }
        if (Regex("""^(?:pichla|previous|pehle wala|last)\s*(?:gana|gaana|song|track)?$|^(?:wapas|peeche) chalao$""").containsMatchIn(t)) {
            return ToolCall("music_control", mapOf("action" to "previous", "app" to musicApp(t)))
        }
        if (Regex("""^(?:resume|resume karo|wapas chalu karo|chalu karo|play karo|chalao|continue karo)$""").containsMatchIn(t)) {
            return ToolCall("music_control", mapOf("action" to "play", "app" to musicApp(t)))
        }
        if (Regex("""kya\s*(?:gana|gaana|song)?\s*(?:baj|chal)\s*rah""").containsMatchIn(t) ||
            t == "now playing" || t == "ye kaunsa gana hai"
        ) {
            return ToolCall("now_playing", emptyMap())
        }

        // ---- YouTube / songs (app detected first, then the leftover) -------
        val ytApp = Regex("""\b(?:youtube|you tube|yt)\b""").containsMatchIn(t)
        val spotify = t.contains("spotify")
        val playVerb = Regex(
            """\b(?:chalao|chala do|chala|lagao|laga do|laga|legana|lagana|play|bajao|baja do|sunao|suna do|sunna hai)\b"""
        ).containsMatchIn(t)
        val searchVerb = Regex("""\b(?:search|dhundo|dhund|khojo|find|dikhao)\b""").containsMatchIn(t)
        if (ytApp || spotify) {
            val q = songQuery(t)
            return when {
                q.isNotBlank() && searchVerb && !playVerb ->
                    ToolCall("youtube_search", mapOf("query" to q))
                q.isNotBlank() ->
                    ToolCall(
                        "play_music",
                        mapOf("query" to q, "app" to if (spotify) "spotify" else "youtube")
                    )
                searchVerb ->
                    ToolCall("youtube_search", mapOf("query" to ""))
                playVerb ->
                    ToolCall(
                        "play_music",
                        mapOf("query" to "", "app" to if (spotify) "spotify" else "youtube")
                    )
                else ->
                    ToolCall(
                        "open_app",
                        mapOf("query" to if (spotify) "spotify" else "youtube")
                    )
            }
        }

        // ---- Songs without an app name --------------------------------------
        if (Regex("""^(?:koi bhi|kuch bhi|koi|random|any)?\s*(?:song|gana|gaana|gane|music|track)\s*(?:chalao|chala do|lagao|laga do|play karo|play kar do|play|bajao|baja do|sunao|suna do)$""")
                .containsMatchIn(t)
        ) {
            return ToolCall("play_music", mapOf("query" to ""))
        }
        if (playVerb && Regex("""\b(?:song|gana|gaana|track|music)\b""").containsMatchIn(t)) {
            val q = songQuery(t)
            return ToolCall("play_music", mapOf("query" to q))
        }
        Regex("""^(?:play|chalao|chala do|lagao|laga do|bajao|baja do|sunao)\s+(.+)$""")
            .find(t)?.let {
                val q = it.groupValues[1].trim()
                val blocked = Regex("""alarm|timer|reminder|torch|flashlight|video call|macro|shortcut""")
                if (q.isNotBlank() && !blocked.containsMatchIn(q)) {
                    return ToolCall("play_music", mapOf("query" to q))
                }
            }

        return null
    }

    /** Wake phrases the pretrained openWakeWord models understand. */
    private val OpenWakeWordPhrases = listOf(
        "hey jarvis", "jarvis", "alexa", "hey mycroft", "mycroft", "hey rhasspy", "rhasspy"
    )

    private val LEAD = Regex(
        """\b(?:iris|hey iris|please|zara|jara|open|launch|start|kholo|khol do|khol|chalu karo|chalu|jao|chalo|karo|kar do|kero|ker do|ker|kr do|do|de do|abhi|phir|aur|or)\b"""
    )

    private val GLUE = Regex("""\b(?:pe|par|per|pr|me|mein|se|ka|ki|ke|wala|wali)\b""")

    /** Removes the app name plus the usual filler so only the payload is left. */
    private fun strip(text: String, app: Regex): String = text
        .replace(app, " ")
        .replace(LEAD, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /**
     * Pulls "<contact> ko ... ki <message>" out of whatever is left after the
     * app name is removed. Handles "kais ko bolo ki me aa raha hu" and
     * "riya ko message bhejo good morning".
     */
    private fun messageParts(body: String): Pair<String, String>? {
        if (body.isBlank()) return null
        val m = Regex(
            """^(.+?)\s+ko\s*(?:ye|yeh|ek)?\s*(?:message|msg|dm|text)?\s*(?:bhejo|bhej do|bhej|send karo|send|likho|likh do|bolo|kaho|bol do)?\s*(?:ki|that|-|:)?\s*(.*)$"""
        ).find(body.trim()) ?: return null
        val contact = m.groupValues[1].replace(GLUE, " ").replace(Regex("""\s+"""), " ").trim()
        val message = m.groupValues[2].trim().removeSuffix(".")
        if (contact.isBlank() || contact.length > 40) return null
        return contact to message
    }

    /** Everything that is not an app name, a verb or filler becomes the query. */
    private fun songQuery(text: String): String = text
        .replace(Regex("""\b(?:youtube|you tube|yt|spotify|jiosaavn|saavn|gaana app)\b"""), " ")
        .replace(
            Regex(
                """\b(?:search|dhundo|dhund|khojo|find|dikhao|chalao|chala do|chala|lagao|laga do|laga|legana|lagana|play|bajao|baja do|sunao|suna do|sunna hai)\b"""
            ),
            " "
        )
        .replace(Regex("""\b(?:song|gana|gaana|gane|track|music|video|shorts?)\b"""), " ")
        .replace(LEAD, " ")
        .replace(GLUE, " ")
        .replace(Regex("""\b(?:koi bhi|koi|kuch bhi|kuch|random|any|mujhe|mereko|ye|yeh|wo|woh)\b"""), " ")
        .replace(Regex("""[\"'\.,!?]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** "spotify pe next gana" -> target that player specifically. */
    private fun musicApp(t: String): String = when {
        t.contains("spotify") -> "spotify"
        t.contains("yt music") || t.contains("youtube music") -> "youtube music"
        t.contains("youtube") -> "youtube"
        else -> ""
    }

    private fun whatsapp(contact: String, message: String): ToolCall {
        val text = message.trim()
        return ToolCall(
            if (text.isBlank()) "whatsapp" else "whatsapp_send",
            mapOf("contact" to contact.trim(), "message" to text)
        )
    }
}
