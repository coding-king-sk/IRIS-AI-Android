package com.irisx.ai.core.agent

/**
 * Fully offline Hinglish + English command parser.
 * This is the primary brain: if a command matches here, IRIS never touches the
 * network. The cloud model is only a fallback for open-ended questions.
 */
object LocalIntentParser {

    fun parse(raw: String): ToolCall? {
        val t = raw.lowercase().trim().replace("  ", " ")

        // ---- App launching -------------------------------------------------
        Regex("^(open|launch|start|khol|kholo|chalu karo)\\s+(.+)").find(t)?.let {
            return ToolCall("open_app", mapOf("query" to it.groupValues[2].clean()))
        }
        Regex("^(.+?)\\s+(kholo|khol do|open karo|chalu karo|start karo)$").find(t)?.let {
            return ToolCall("open_app", mapOf("query" to it.groupValues[1].clean()))
        }

        // ---- Calling -------------------------------------------------------
        Regex("^(call|phone|dial)\\s+(.+)").find(t)?.let {
            return ToolCall("call", mapOf("contact" to it.groupValues[2].clean()))
        }
        Regex("^(.+?)\\s+(ko)?\\s*(call|phone)\\s*(karo|kar do|lagao|milao)$").find(t)?.let {
            return ToolCall("call", mapOf("contact" to it.groupValues[1].clean()))
        }

        // ---- WhatsApp ------------------------------------------------------
        Regex("whatsapp\\s+(?:pe\\s+)?(.+?)\\s+(?:ko\\s+)?(?:message|msg|bhejo|likho)\\s*(.*)")
            .find(t)?.let {
                return ToolCall(
                    "whatsapp",
                    mapOf(
                        "contact" to it.groupValues[1].clean(),
                        "message" to it.groupValues[2].clean()
                    )
                )
            }

        // ---- SMS -----------------------------------------------------------
        Regex("^(?:send\\s+)?(?:sms|message|msg)\\s+(?:to\\s+)?(.+?)\\s+(?:saying|that|bolo|likho)\\s+(.+)")
            .find(t)?.let {
                return ToolCall(
                    "sms",
                    mapOf(
                        "contact" to it.groupValues[1].clean(),
                        "message" to it.groupValues[2].clean()
                    )
                )
            }
        Regex("^(.+?)\\s+ko\\s+(?:sms|message|msg)\\s+(?:bhejo|karo)\\s*(.*)").find(t)?.let {
            return ToolCall(
                "sms",
                mapOf(
                    "contact" to it.groupValues[1].clean(),
                    "message" to it.groupValues[2].clean()
                )
            )
        }

        // ---- Alarm & timer -------------------------------------------------
        Regex("(\\d{1,2})[:. ]?(\\d{2})?\\s*(am|pm|baje)?.*(alarm)").find(t)?.let { m ->
            return alarmCall(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
        Regex("alarm.*?(\\d{1,2})[:. ]?(\\d{2})?\\s*(am|pm|baje)?").find(t)?.let { m ->
            return alarmCall(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
        Regex("(\\d+)\\s*(minute|minutes|min|mint|second|seconds|sec|ghante|hour|hours)\\s*(ka)?\\s*timer")
            .find(t)?.let { m ->
                return ToolCall(
                    "timer",
                    mapOf("value" to m.groupValues[1], "unit" to m.groupValues[2])
                )
            }

        // ---- Torch ---------------------------------------------------------
        if (t.matches(Regex(".*(torch|flashlight|light|batti).*(on|chalu|jala).*"))) {
            return ToolCall("flashlight", mapOf("state" to "on"))
        }
        if (t.matches(Regex(".*(torch|flashlight|light|batti).*(off|band|bujha).*"))) {
            return ToolCall("flashlight", mapOf("state" to "off"))
        }

        // ---- Volume & media ------------------------------------------------
        if (t.contains("volume") || t.contains("awaaz")) {
            val dir = when {
                t.contains("up") || t.contains("badha") || t.contains("tez") -> "up"
                t.contains("down") || t.contains("kam") || t.contains("dhima") -> "down"
                t.contains("mute") || t.contains("silent") || t.contains("band") -> "mute"
                else -> "up"
            }
            return ToolCall("volume", mapOf("direction" to dir))
        }
        if (Regex("(music|song|gana|gaana|media)").containsMatchIn(t)) {
            val action = when {
                t.contains("pause") || t.contains("rok") || t.contains("band") -> "pause"
                t.contains("next") || t.contains("aage") || t.contains("agla") -> "next"
                t.contains("previous") || t.contains("peeche") || t.contains("pichla") -> "previous"
                else -> "play"
            }
            return ToolCall("media", mapOf("action" to action))
        }

        // ---- Notes ---------------------------------------------------------
        Regex("^(note|note karo|likh lo|yaad rakho|remember)\\s+(.+)").find(t)?.let {
            return ToolCall("note", mapOf("text" to it.groupValues[2].clean()))
        }
        if (Regex("(mere|meri|my)?\\s*(notes|note)\\s*(padho|dikhao|read|show)").containsMatchIn(t)) {
            return ToolCall("read_notes")
        }

        // ---- Screen control (AccessibilityService) -------------------------
        if (Regex("(back|peeche|wapas)\\s*(jao|chalo|karo)?").matches(t)) {
            return ToolCall("screen", mapOf("action" to "back"))
        }
        if (Regex("(home|ghar)\\s*(jao|chalo|karo)?").matches(t)) {
            return ToolCall("screen", mapOf("action" to "home"))
        }
        if (t.contains("recent") || t.contains("recents")) {
            return ToolCall("screen", mapOf("action" to "recents"))
        }
        if (t.contains("scroll")) {
            return ToolCall("screen", mapOf("action" to "scroll"))
        }
        if (Regex("(screen|screenshot)\\s*(padho|read|summarize|batao)").containsMatchIn(t)) {
            return ToolCall("read_screen")
        }

        // ---- Notifications --------------------------------------------------
        if (Regex("notification(s)?\\s*(padho|read|batao|dikhao)").containsMatchIn(t)) {
            return ToolCall("read_notifications")
        }

        // ---- Device info & settings ----------------------------------------
        if (Regex("battery|charging|charge").containsMatchIn(t)) return ToolCall("battery")
        if (Regex("(time|samay|baje)\\s*(kya|kitna|batao)?").containsMatchIn(t) &&
            !t.contains("alarm")
        ) {
            return ToolCall("clock", mapOf("kind" to "time"))
        }
        if (Regex("(date|tareekh|din)").containsMatchIn(t)) {
            return ToolCall("clock", mapOf("kind" to "date"))
        }
        if (t.contains("wifi") || t.contains("wi-fi")) {
            return ToolCall("settings_panel", mapOf("panel" to "wifi"))
        }
        if (t.contains("bluetooth")) {
            return ToolCall("settings_panel", mapOf("panel" to "bluetooth"))
        }

        // ---- Web search (needs network, but intent is local) ---------------
        Regex("^(google|search|dhundo|khojo)\\s+(.+)").find(t)?.let {
            return ToolCall("web_search", mapOf("query" to it.groupValues[2].clean()))
        }

        return null
    }

    private fun alarmCall(hour: String, minute: String, marker: String): ToolCall {
        var h = hour.toIntOrNull() ?: 7
        if (marker == "pm" && h < 12) h += 12
        if (marker == "am" && h == 12) h = 0
        val m = minute.toIntOrNull() ?: 0
        return ToolCall("alarm", mapOf("hour" to h.toString(), "minute" to m.toString()))
    }

    private fun String.clean(): String = trim()
        .removeSuffix(".")
        .removePrefix("the ")
        .removeSuffix(" app")
        .trim()
}
