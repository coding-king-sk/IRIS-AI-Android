package com.irisx.ai.core.agent

/**
 * Fast lane for the commands people actually use first: wake-word setup, the
 * everyday toggles (silent / DND / bluetooth / hotspot), music transport,
 * location sharing, songs, and WhatsApp / Instagram / Telegram messages.
 *
 * This runs BEFORE LocalIntentParser so those phrases never fall through to a
 * generic route. Every pattern is deliberately narrow (it needs a "ko", a song
 * word or an app name) so plain commands like "camera kholo" still go to the
 * old parser.
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

        // ---- Wake word engine ----------------------------------------------
        if (Regex("""wake\s*word\s*(?:ka\s*)?(?:status|haal|kaisa)""").containsMatchIn(t)) {
            return ToolCall("neural_wake_status", emptyMap())
        }
        if (Regex("""(?:neural|openwakeword|open wake word|smart|new)\s*wake\s*word""").containsMatchIn(t) ||
            Regex("""wake\s*word\s*(?:model\s*)?(?:setup|install|download|chalu|on|enable|theek)""").containsMatchIn(t)
        ) {
            val model = OpenWakeWordPhrases.firstOrNull { t.contains(it) }.orEmpty()
            return ToolCall("neural_wake_setup", mapOf("model" to model))
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

        // ---- Location sharing (before the generic "X ko ... bhejo" rules) ---
        Regex("""^(?:meri\s*)?(?:live\s*)?location\s*(?:bhejo|bhej do|share karo|send karo|do)\s+(.+?)(?:\s+ko)?$""")
            .find(t)?.let {
                return ToolCall("location_share", mapOf("contact" to it.groupValues[1].trim()))
            }
        Regex("""^(.+?)\s+ko\s+(?:meri\s*)?(?:live\s*)?location\s*(?:bhejo|bhej do|share karo|send karo|do)$""")
            .find(t)?.let {
                return ToolCall("location_share", mapOf("contact" to it.groupValues[1].trim()))
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

        // ---- Instagram DM --------------------------------------------------
        Regex("""^(?:instagram|insta|ig)\s*(?:pe|par|me|mein)?\s*(.+?)\s+ko\s*(?:message|msg|dm|likho|bolo)?\s*(?:bhejo|bhej do|karo|kar do|do)?\s*(?:ki|that|bolo)?\s*(.*)$""")
            .find(t)?.let {
                return instagram(it.groupValues[1], it.groupValues[2])
            }
        Regex("""^(.+?)\s+ko\s+(?:instagram|insta|ig)\s*(?:pe|par)?\s*(?:message|msg|dm)?\s*(?:bhejo|bhej do|karo|kar do|likho|bolo)\s*(?:ki|that)?\s*(.*)$""")
            .find(t)?.let {
                return instagram(it.groupValues[1], it.groupValues[2])
            }

        // ---- Telegram ------------------------------------------------------
        Regex("""^(?:telegram|tg)\s*(?:pe|par|me|mein)?\s*(.+?)\s+ko\s*(?:message|msg)?\s*(?:bhejo|bhej do|karo|likho)?\s*(?:ki|that)?\s*(.*)$""")
            .find(t)?.let {
                return ToolCall(
                    "telegram_send",
                    mapOf(
                        "user" to it.groupValues[1].trim(),
                        "message" to it.groupValues[2].trim()
                    )
                )
            }

        // ---- WhatsApp ------------------------------------------------------
        Regex("""^whatsapp\s*(?:pe|par|me|mein|se)?\s*(.+?)\s+ko\s*(?:message|msg|likho|bolo)?\s*(?:bhejo|bhej do|karo|kar do|do)?\s*(?:ki|that|bolo)?\s*(.*)$""")
            .find(t)?.let {
                return whatsapp(it.groupValues[1], it.groupValues[2])
            }
        Regex("""^(.+?)\s+ko\s+whatsapp\s*(?:pe|par)?\s*(?:message|msg|dm)?\s*(?:bhejo|bhej do|karo|kar do|likho|bolo)\s*(?:ki|that)?\s*(.*)$""")
            .find(t)?.let {
                return whatsapp(it.groupValues[1], it.groupValues[2])
            }

        // ---- Music / video -------------------------------------------------
        if (Regex("""^(?:koi bhi|kuch bhi|koi|random|any)?\s*(?:song|gana|gaana|gane|music|track)\s*(?:chalao|chala do|lagao|laga do|play karo|play kar do|play|bajao|baja do|sunao|suna do)$""")
                .containsMatchIn(t)
        ) {
            return ToolCall("play_music", mapOf("query" to ""))
        }
        Regex("""^(?:youtube|yt)\s*(?:pe|par|me|mein)?\s+(.+?)\s*(?:ka)?\s*(?:song|gana|gaana|video)?\s*(?:chalao|chala do|lagao|laga do|play karo|play kar do|play|bajao|sunao)$""")
            .find(t)?.let {
                return ToolCall(
                    "play_music",
                    mapOf("query" to it.groupValues[1].trim(), "app" to "youtube")
                )
            }
        Regex("""^(?:youtube|yt)\s*(?:pe|par|me|mein)?\s+(.+?)\s*(?:dikhao|dhundo|search karo|khojo)$""")
            .find(t)?.let {
                return ToolCall("youtube_search", mapOf("query" to it.groupValues[1].trim()))
            }
        Regex("""^(?:play|chalao|chala do|lagao|laga do|bajao|baja do|sunao)\s+(.+?)\s*(?:song|gana|gaana|track|video)?$""")
            .find(t)?.let {
                val q = it.groupValues[1].trim()
                val blocked = Regex("""alarm|timer|reminder|torch|flashlight|video call""")
                if (q.isNotBlank() && !blocked.containsMatchIn(q)) {
                    return ToolCall("play_music", mapOf("query" to q))
                }
            }
        Regex("""^(.+?)\s+(?:ka|ki)?\s*(?:song|gana|gaana|track)\s*(?:chalao|chala do|lagao|laga do|play karo|play|bajao|sunao)$""")
            .find(t)?.let {
                return ToolCall("play_music", mapOf("query" to it.groupValues[1].trim()))
            }

        return null
    }

    /** Wake phrases the pretrained openWakeWord models understand. */
    private val OpenWakeWordPhrases = listOf(
        "hey jarvis", "jarvis", "alexa", "hey mycroft", "mycroft", "hey rhasspy", "rhasspy"
    )

    /** "spotify pe next gana" -> target that player specifically. */
    private fun musicApp(t: String): String = when {
        t.contains("spotify") -> "spotify"
        t.contains("yt music") || t.contains("youtube music") -> "youtube music"
        t.contains("youtube") -> "youtube"
        else -> ""
    }

    private fun instagram(user: String, message: String): ToolCall = ToolCall(
        "instagram_send",
        mapOf("user" to user.trim(), "message" to message.trim())
    )

    private fun whatsapp(contact: String, message: String): ToolCall {
        val text = message.trim()
        return ToolCall(
            if (text.isBlank()) "whatsapp" else "whatsapp_send",
            mapOf("contact" to contact.trim(), "message" to text)
        )
    }
}
