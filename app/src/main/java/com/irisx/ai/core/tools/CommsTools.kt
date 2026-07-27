package com.irisx.ai.core.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult

class OpenAppTool : IrisTool {
    override val name = "open_app"
    override val description = "Open an installed app on the phone by its name."
    override val params = mapOf("query" to "App name, e.g. WhatsApp, Camera, Settings")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val query = args["query"].orEmpty()
        val app = AppLauncher.find(context, query)
            ?: return ToolResult(false, "'$query' naam ka app nahi mila.")
        return if (AppLauncher.launch(context, app.packageName)) {
            ToolResult(true, "${app.label} khol diya.")
        } else {
            ToolResult(false, "${app.label} launch nahi ho paya.")
        }
    }
}

object ContactResolver {

    data class Contact(val name: String, val number: String)

    fun resolve(context: Context, query: String): Contact? {
        val digits = query.filter { it.isDigit() || it == '+' }
        if (digits.length >= 7) return Contact(query, digits)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%" + query.trim() + "%"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Contact(cursor.getString(0) ?: query, cursor.getString(1) ?: "")
                } else null
            }
        }.getOrNull()
    }
}

class CallTool : IrisTool {
    override val name = "call"
    override val description = "Place a phone call to a contact name or number."
    override val params = mapOf("contact" to "Contact name or phone number")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val query = args["contact"].orEmpty()
        val contact = ContactResolver.resolve(context, query)
            ?: return ToolResult(false, "'$query' contact me nahi mila (ya contacts permission nahi hai).")

        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val ok = context.startExternal(Intent(action, Uri.parse("tel:" + contact.number)))
        return if (ok) {
            ToolResult(true, "${contact.name} ko call lagaa raha hoon.")
        } else {
            ToolResult(false, "Call start nahi ho payi.")
        }
    }
}

class SmsTool : IrisTool {
    override val name = "sms"
    override val description = "Send an SMS to a contact."
    override val params = mapOf(
        "contact" to "Contact name or number",
        "message" to "Message body"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val contact = ContactResolver.resolve(context, args["contact"].orEmpty())
            ?: return ToolResult(false, "Contact nahi mila.")
        val body = args["message"].orEmpty()

        val canSend = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

        if (canSend && body.isNotBlank()) {
            return runCatching {
                val sms = context.getSystemService(SmsManager::class.java)
                    ?: SmsManager.getDefault()
                sms.sendTextMessage(contact.number, null, body, null, null)
                ToolResult(true, "${contact.name} ko SMS bhej diya.")
            }.getOrElse { ToolResult(false, "SMS bhejne me dikkat aayi.") }
        }

        // Fall back to the composer so nothing is sent silently without permission.
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + contact.number))
            .putExtra("sms_body", body)
        return if (context.startExternal(intent)) {
            ToolResult(true, "${contact.name} ke liye message draft khol diya.")
        } else {
            ToolResult(false, "Messaging app nahi khul paya.")
        }
    }
}

class WhatsAppTool : IrisTool {
    override val name = "whatsapp"
    override val description = "Open a WhatsApp chat with a contact, optionally prefilled."
    override val params = mapOf(
        "contact" to "Contact name or number",
        "message" to "Optional message text"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val query = args["contact"].orEmpty()
        val message = args["message"].orEmpty()
        val contact = ContactResolver.resolve(context, query)

        if (contact != null && contact.number.isNotBlank()) {
            val number = contact.number.filter { it.isDigit() }
            val url = "https://wa.me/" + number + "?text=" + Uri.encode(message)
            if (context.startExternal(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
                return ToolResult(true, "${contact.name} ka WhatsApp chat khol diya.")
            }
        }

        val app = AppLauncher.find(context, "whatsapp")
            ?: return ToolResult(false, "WhatsApp install nahi hai.")
        return if (AppLauncher.launch(context, app.packageName)) {
            ToolResult(true, "WhatsApp khol diya.")
        } else {
            ToolResult(false, "WhatsApp khul nahi paya.")
        }
    }
}
