package com.irisx.ai.core.agent

/**
 * Fully offline Hinglish + Hindi + English command parser.
 * This is the primary brain: if a command matches here, IRIS never touches the
 * network. The cloud model is only a fallback for open-ended questions.
 */
object LocalIntentParser {

    fun parse(raw: String): ToolCall? {
        val t = raw.lowercase().trim().replace(Regex("\\s+"), " ")

        // ---- App launching -------------------------------------------------
        Regex("^(open|launch|start|khol|kholo|chalu karo|chala do|shuru karo)\\s+(.+)")
            .find(t)?.let {
                return ToolCall("open_app", mapOf("query" to it.groupValues[2].clean()))
            }
        Regex("^(.+?)\\s+(kholo|khol do|open karo|chalu karo|chala do|start karo|shuru karo)$")
            .find(t)?.let {
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

        // ---- Reminders (before alarms and notes) ---------------------------
        if (Regex("reminder(s)?\\s*(kya|batao|dikhao|list|padho)").containsMatchIn(t) ||
            Regex("(pending|upcoming)\\s+reminder").containsMatchIn(t)
        ) {
            return ToolCall("read_reminders")
        }
        if (Regex("(remind|yaad dila|yaad dilana|reminder)").containsMatchIn(t)) {
            val delay = Regex("""(\d+)\s*(minute|minutes|min|mint)""").find(t)
                ?.groupValues?.get(1)
            val hourMatch = Regex("""(\d{1,2})[:. ]?(\d{2})?\s*(am|pm|baje|bje)""").find(t)
            val body = t
                .replace(
                    Regex(
                        """(please|iris|mujhe|mereko|remind me to|remind me|remind|yaad dilana|yaad dila do|yaad dila|reminder set karo|reminder|set)"""
                    ),
                    " "
                )
                .replace(Regex("""\d{1,2}[:. ]?\d{0,2}\s*(am|pm|baje|bje)?"""), " ")
                .replace(Regex("""\b(minute|minutes|min|mint|baad|me|mein|par|pe|at|in|ko)\b"""), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val args = HashMap<String, String>()
            args["text"] = if (body.isBlank()) "Reminder" else body
            when {
                delay != null -> args["in_minutes"] = delay
                hourMatch != null -> {
                    var h = hourMatch.groupValues[1].toIntOrNull() ?: 9
                    val marker = hourMatch.groupValues[3]
                    if (marker == "pm" && h < 12) h += 12
                    if (marker == "am" && h == 12) h = 0
                    args["hour"] = h.toString()
                    args["minute"] = (hourMatch.groupValues[2].toIntOrNull() ?: 0).toString()
                }
                else -> args["in_minutes"] = "10"
            }
            return ToolCall("reminder", args)
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
        if (t.matches(Regex(".*(torch|flashlight|batti).*(on|chalu|jala|jalao).*"))) {
            return ToolCall("flashlight", mapOf("state" to "on"))
        }
        if (t.matches(Regex(".*(torch|flashlight|batti).*(off|band|bujha|bujhao).*"))) {
            return ToolCall("flashlight", mapOf("state" to "off"))
        }

        // ---- Brightness ----------------------------------------------------
        if (Regex("brightness|roshni|screen light").containsMatchIn(t)) {
            val digits = Regex("""(\d{1,3})""").find(t)?.groupValues?.get(1)
            val level = when {
                digits != null -> digits
                Regex("max|full|puri|poori|sabse tez").containsMatchIn(t) -> "max"
                Regex("min|sabse kam|lowest").containsMatchIn(t) -> "min"
                Regex("up|badha|tez|zyada").containsMatchIn(t) -> "up"
                Regex("down|kam|dhimi|dhima").containsMatchIn(t) -> "down"
                else -> "up"
            }
            return ToolCall("brightness", mapOf("percent" to level))
        }

        // ---- Volume & media ------------------------------------------------
        if (t.contains("volume") || t.contains("awaaz") || t.contains("awaz")) {
            val dir = when {
                t.contains("up") || t.contains("badha") || t.contains("tez") ||
                    t.contains("zyada") -> "up"
                t.contains("down") || t.contains("kam") || t.contains("dhima") -> "down"
                t.contains("mute") || t.contains("silent") || t.contains("band") -> "mute"
                else -> "up"
            }
            return ToolCall("volume", mapOf("direction" to dir))
        }
        if (Regex("(music|song|gana|gaana|media|track)").containsMatchIn(t)) {
            val action = when {
                t.contains("pause") || t.contains("rok") || t.contains("band") -> "pause"
                t.contains("next") || t.contains("aage") || t.contains("agla") ||
                    t.contains("badlo") -> "next"
                t.contains("previous") || t.contains("peeche") || t.contains("pichla") -> "previous"
                else -> "play"
            }
            return ToolCall("media", mapOf("action" to action))
        }

        // ---- Notes & on-device memory --------------------------------------
        Regex("^(note|note karo|likh lo|likho|yaad rakho|remember)\\s+(.+)").find(t)?.let {
            return ToolCall("note", mapOf("text" to it.groupValues[2].clean()))
        }
        Regex("(?:notes|note|memory|history)\\s*(?:me|mein|se)\\s*(.+?)\\s*(?:dhundo|search karo|khojo|find karo)")
            .find(t)?.let {
                return ToolCall("memory_search", mapOf("query" to it.groupValues[1].clean()))
            }
        Regex("^(?:mujhe|maine)?\\s*(?:kya|kuch)?\\s*(?:bola tha|likha tha|save kiya tha)\\s*(.*)")
            .find(t)?.let {
                val q = it.groupValues[1].clean()
                if (q.isNotBlank()) return ToolCall("memory_search", mapOf("query" to q))
            }
        if (Regex("(mere|meri|my)?\\s*(notes|note)\\s*(padho|dikhao|read|show|batao)")
                .containsMatchIn(t)
        ) {
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

        // ---- Screen understanding ------------------------------------------
        if (Regex("(isko|ise|iska|screen|page)\\s*(matlab|samjhao|samjha do|explain|summary|summarize)")
                .containsMatchIn(t) ||
            Regex("(explain|samjhao)\\s*(this|ye|yeh|screen)").containsMatchIn(t)
        ) {
            return ToolCall("explain_screen")
        }
        if (Regex("(screen|screenshot)\\s*(padho|read|batao|sunao)").containsMatchIn(t)) {
            return ToolCall("read_screen")
        }

        // ---- Screenshot ----------------------------------------------------
        if (Regex("screenshot|screen shot|screen capture").containsMatchIn(t)) {
            return ToolCall("screenshot")
        }

        // ---- Notifications --------------------------------------------------
        if (Regex("(kya miss kiya|kya missed|what did i miss|digest)").containsMatchIn(t)) {
            return ToolCall("notification_digest")
        }
        if (Regex("notification(s)?\\s*(ka)?\\s*(digest|summary)").containsMatchIn(t)) {
            return ToolCall("notification_digest")
        }
        if (Regex("notification(s)?\\s*(padho|read|batao|dikhao|sunao)").containsMatchIn(t)) {
            return ToolCall("read_notifications")
        }
        if (Regex("notification(s)?\\s*(kholo|open|shade)").containsMatchIn(t)) {
            return ToolCall("screen", mapOf("action" to "notifications"))
        }

        // ---- Self diagnostics ----------------------------------------------
        if (Regex("(offline|voice|mic|permission)\\s*(status|check|test|ready)").containsMatchIn(t) ||
            Regex("(kya kya|kaunsi)\\s*permission").containsMatchIn(t)
        ) {
            return ToolCall("voice_status")
        }

        // ---- Percentages ---------------------------------------------------
        Regex("""(\d+(?:\.\d+)?)\s*(?:ka|of)\s*(\d+(?:\.\d+)?)\s*(?:percent|%)""").find(t)?.let {
            return ToolCall(
                "calculator",
                mapOf("expression" to it.groupValues[1] + "*" + it.groupValues[2] + "/100")
            )
        }
        Regex("""(\d+(?:\.\d+)?)\s*(?:percent|%)\s*(?:of|ka)\s*(\d+(?:\.\d+)?)""").find(t)?.let {
            return ToolCall(
                "calculator",
                mapOf("expression" to it.groupValues[1] + "*" + it.groupValues[2] + "/100")
            )
        }

        // ---- Unit conversion -----------------------------------------------
        Regex(
            """^(?:convert\s+)?(-?\d+(?:\.\d+)?)\s*([a-z]{1,12})\s*(?:ko|me|mein|to|in|into|=)\s*([a-z]{1,12})\??$"""
        ).find(t)?.let {
            return ToolCall(
                "unit_convert",
                mapOf(
                    "value" to it.groupValues[1],
                    "from" to it.groupValues[2],
                    "to" to it.groupValues[3]
                )
            )
        }

        // ---- Calculator ----------------------------------------------------
        Regex("^(calculate|calc|solve|hisaab|kitna hota hai)\\s+(.+)").find(t)?.let {
            return ToolCall("calculator", mapOf("expression" to it.groupValues[2]))
        }
        if (Regex("""^[\d\s.+\-*/^%()x]+$""").matches(t) &&
            t.any { it.isDigit() } &&
            Regex("""[+\-*/^x]""").containsMatchIn(t)
        ) {
            return ToolCall("calculator", mapOf("expression" to t))
        }

        // ---- Contact lookup ------------------------------------------------
        Regex("""^(.+?)\s+ka\s+(?:phone\s+)?(?:number|no)\b.*$""").find(t)?.let {
            val who = it.groupValues[1].clean()
            if (who.isNotBlank()) return ToolCall("contact_info", mapOf("contact" to who))
        }
        Regex("""^(?:number|contact|phone number)\s+(?:of|for)\s+(.+?)\??$""").find(t)?.let {
            return ToolCall("contact_info", mapOf("contact" to it.groupValues[1].clean()))
        }

        // ---- Calendar ------------------------------------------------------
        if (Regex("(calendar|schedule|meeting|event)").containsMatchIn(t) &&
            Regex("(aaj|today|kya hai|batao|dikhao|kitne)").containsMatchIn(t)
        ) {
            return ToolCall("calendar_today")
        }
        Regex(
            """(?:event|meeting|appointment)\s+(.+?)\s+(?:at|pe|par|ko)\s+(\d{1,2})[:. ]?(\d{2})?\s*(am|pm|baje)?"""
        ).find(t)?.let { m ->
            var h = m.groupValues[2].toIntOrNull() ?: 9
            val marker = m.groupValues[4]
            if (marker == "pm" && h < 12) h += 12
            if (marker == "am" && h == 12) h = 0
            return ToolCall(
                "calendar_add",
                mapOf(
                    "title" to m.groupValues[1].clean(),
                    "hour" to h.toString(),
                    "minute" to (m.groupValues[3].toIntOrNull() ?: 0).toString(),
                    "day_offset" to if (t.contains("kal") || t.contains("tomorrow")) "1" else "0"
                )
            )
        }

        // ---- Device info ---------------------------------------------------
        if (Regex("battery|charging|charge").containsMatchIn(t)) return ToolCall("battery")
        if (Regex("(time|samay|baje)\\s*(kya|kitna|batao)?").containsMatchIn(t) &&
            !t.contains("alarm")
        ) {
            return ToolCall("clock", mapOf("kind" to "time"))
        }
        if (Regex("(date|tareekh|din)").containsMatchIn(t)) {
            return ToolCall("clock", mapOf("kind" to "date"))
        }

        // ---- Web search (needs network, but intent is local) ---------------
        Regex("^(google|search|dhundo|khojo)\\s+(.+)").find(t)?.let {
            return ToolCall("web_search", mapOf("query" to it.groupValues[2].clean()))
        }

        // ---- Connectivity toggles ------------------------------------------
        if (Regex("wifi|wi-fi|hotspot|tether|airplane|flight mode|bluetooth|mobile data")
                .containsMatchIn(t)
        ) {
            val target = when {
                t.contains("hotspot") || t.contains("tether") -> "hotspot"
                t.contains("airplane") || t.contains("flight") -> "airplane"
                t.contains("blue") -> "bluetooth"
                t.contains("data") -> "data"
                else -> "wifi"
            }
            return ToolCall("connectivity", mapOf("target" to target))
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
        .removeSuffix("?")
        .removePrefix("the ")
        .removeSuffix(" app")
        .trim()
}
