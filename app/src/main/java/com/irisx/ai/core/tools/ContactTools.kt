package com.irisx.ai.core.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult

/** Offline contact number lookup straight from the device contacts provider. */
class ContactInfoTool : IrisTool {
    override val name = "contact_info"
    override val description = "Look up a saved contact's phone number"
    override val params = mapOf("contact" to "Contact name to look up")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val query = (args["contact"] ?: args["query"] ?: "").trim()
        if (query.isEmpty()) return ToolResult(false, "Kiska number chahiye?")
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return ToolResult(false, "Contacts ki permission nahi hai")

        val results = ArrayList<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?"
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                arrayOf("%" + query + "%"),
                null
            )?.use { cursor ->
                while (cursor.moveToNext() && results.size < 5) {
                    val name = cursor.getString(0) ?: ""
                    val number = cursor.getString(1) ?: ""
                    if (name.isBlank() || number.isBlank()) continue
                    val line = name + ": " + number
                    if (!results.contains(line)) results.add(line)
                }
            }
        }

        if (results.isEmpty()) return ToolResult(false, query + " naam ka contact nahi mila")
        return ToolResult(true, results.joinToString(", "))
    }
}
