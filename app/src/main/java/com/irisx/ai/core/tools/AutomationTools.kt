package com.irisx.ai.core.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.core.automation.Automator
import com.irisx.ai.service.IrisAccessibilityService

/** Opens the camera and (if accessibility is on) presses the shutter. */
class CameraTool : IrisTool {
    override val name = "camera"
    override val description = "Open the camera and optionally take a photo or switch to selfie/video."
    override val params = mapOf("mode" to "photo, selfie or video")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val mode = args["mode"].orEmpty().lowercase()
        val action = when {
            mode.contains("video") -> MediaStore.ACTION_VIDEO_CAPTURE
            else -> MediaStore.ACTION_IMAGE_CAPTURE
        }
        val intent = Intent(action)
        if (mode.contains("selfie") || mode.contains("front")) {
            intent.putExtra("android.intent.extras.CAMERA_FACING", 1)
            intent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        }
        if (!context.startExternal(intent)) {
            return ToolResult(false, "Camera app nahi khul paya.")
        }
        if (!Automator.available()) {
            return ToolResult(true, "Camera khol diya. Shutter khud dabao (accessibility off hai).")
        }
        Automator.sleep(1800)
        val clicked = Automator.waitAndClick(
            "shutter", "take photo", "capture", "take picture", "record", "camera shutter"
        )
        return if (clicked) {
            ToolResult(true, "Photo le li.")
        } else {
            ToolResult(true, "Camera khol diya, shutter button nahi mila to khud dabao.")
        }
    }
}

/** Sends a WhatsApp message end to end: open chat, type, tap send. */
class WhatsAppSendTool : IrisTool {
    override val name = "whatsapp_send"
    override val description = "Open a WhatsApp chat, type the message and tap send automatically."
    override val params = mapOf(
        "contact" to "Contact name or number",
        "message" to "Message text"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val query = args["contact"].orEmpty()
        val message = args["message"].orEmpty()
        if (message.isBlank()) return ToolResult(false, "Message khali hai, kya bhejna hai bolo.")

        val contact = ContactResolver.resolve(context, query)
            ?: return ToolResult(false, "'$query' contact me nahi mila (contacts permission check karo).")
        val number = contact.number.filter { it.isDigit() }
        if (number.isBlank()) return ToolResult(false, "${contact.name} ka number nahi mila.")

        val url = "https://wa.me/" + number + "?text=" + Uri.encode(message)
        if (!context.startExternal(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
            return ToolResult(false, "WhatsApp khul nahi paya.")
        }

        if (!Automator.available()) {
            return ToolResult(
                true,
                "${contact.name} ka chat message ke saath khol diya. Auto-send ke liye Accessibility on karo."
            )
        }

        Automator.waitForApp("com.whatsapp")
        Automator.sleep(1200)
        val sent = Automator.tapSend()
        return if (sent) {
            ToolResult(true, "${contact.name} ko message bhej diya.")
        } else {
            ToolResult(true, "${contact.name} ka chat taiyar hai, send button nahi mila — ek tap kar do.")
        }
    }
}

/** Multi step: pick a photo from the gallery and share it to a contact. */
class SendPhotoTool : IrisTool {
    override val name = "send_photo"
    override val description = "Pick a photo from the gallery and send it to a contact on WhatsApp."
    override val params = mapOf(
        "contact" to "Contact name",
        "which" to "Optional: latest (default) or a search word"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val who = args["contact"].orEmpty().trim()
        if (who.isBlank()) return ToolResult(false, "Kisko bhejna hai ye nahi samjha.")

        val uri = latestImage(context)
            ?: return ToolResult(false, "Gallery me koi photo nahi mili (storage permission check karo).")

        val share = Intent(Intent.ACTION_SEND)
            .setType("image/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .setPackage("com.whatsapp")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        if (!context.startExternal(share)) {
            return ToolResult(false, "WhatsApp share screen nahi khul payi.")
        }

        if (!Automator.available()) {
            return ToolResult(
                true,
                "Latest photo WhatsApp share screen par khol di. $who ko choose karke bhej do (auto ke liye Accessibility on karo)."
            )
        }

        Automator.waitForApp("com.whatsapp")
        Automator.sleep(1500)
        val picked = Automator.waitAndClick(who)
        if (!picked) {
            return ToolResult(true, "Share screen khul gayi lekin '$who' list me nahi mila — khud select kar lo.")
        }
        Automator.sleep(900)
        Automator.click("ok", "next", "aage")
        Automator.sleep(1200)
        val sent = Automator.tapSend()
        return if (sent) {
            ToolResult(true, "$who ko photo bhej di.")
        } else {
            ToolResult(true, "$who select ho gaya, ab bas send dabana baaki hai.")
        }
    }

    private fun latestImage(context: Context): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sort = MediaStore.Images.Media.DATE_ADDED + " DESC"
        return runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sort
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                } else null
            }
        }.getOrNull()
    }
}

/** Reads the visible form fields and fills them from saved values. */
class FillFormTool : IrisTool {
    override val name = "fill_form"
    override val description = "Fill the visible form fields on screen with the given values."
    override val params = mapOf("values" to "Comma separated values in field order")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val svc = IrisAccessibilityService.instance
            ?: return ToolResult(false, Automator.UNAVAILABLE)

        val fields = svc.describeFields()
        if (fields.isEmpty()) return ToolResult(false, "Is screen par koi text field nahi dikh rahi.")

        val raw = args["values"].orEmpty().trim()
        if (raw.isBlank()) {
            return ToolResult(
                true,
                "Screen par " + fields.size + " field hai: " + fields.joinToString(", ") +
                    ". Values comma se bolo, main bhar dunga."
            )
        }

        val values = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val filled = svc.fillFields(values)
        return if (filled > 0) {
            ToolResult(true, filled.toString() + " field bhar di.")
        } else {
            ToolResult(false, "Field bhar nahi paya, shayad app editing block kar rahi hai.")
        }
    }
}

/** Types text into whatever field is focused on screen. */
class TypeTextTool : IrisTool {
    override val name = "type_text"
    override val description = "Type text into the field on the current screen."
    override val params = mapOf("text" to "Text to type")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        if (!Automator.available()) return ToolResult(false, Automator.UNAVAILABLE)
        val text = args["text"].orEmpty()
        if (text.isBlank()) return ToolResult(false, "Kya likhna hai wo nahi mila.")
        return if (Automator.typeFirst(text)) {
            ToolResult(true, "Likh diya.")
        } else {
            ToolResult(false, "Koi editable field nahi mili.")
        }
    }
}

/** Taps a button or item visible on screen by its label. */
class TapTool : IrisTool {
    override val name = "tap"
    override val description = "Tap a button or item on the current screen by its visible label."
    override val params = mapOf("label" to "Visible text of the button")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        if (!Automator.available()) return ToolResult(false, Automator.UNAVAILABLE)
        val label = args["label"].orEmpty().trim()
        if (label.isBlank()) return ToolResult(false, "Kis cheez par tap karna hai?")
        return if (Automator.click(label)) {
            ToolResult(true, "'" + label + "' par tap kar diya.")
        } else {
            ToolResult(false, "'" + label + "' screen par nahi mila.")
        }
    }
}
