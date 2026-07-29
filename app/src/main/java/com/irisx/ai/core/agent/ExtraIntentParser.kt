package com.irisx.ai.core.agent

/**
 * Fast lane for the commands people actually use first: play a song, open a
 * video, WhatsApp / Instagram / Telegram messages. This runs BEFORE
 * LocalIntentParser so those phrases never fall through to a generic route.
 *
 * Every pattern here is deliberately narrow (it needs a "ko", a song word or an
 * app name) so plain commands like "camera kholo" still go to the old parser.
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
