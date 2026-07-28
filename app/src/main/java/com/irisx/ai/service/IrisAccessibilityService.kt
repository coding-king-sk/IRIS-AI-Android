package com.irisx.ai.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Gives IRIS hands and eyes on the screen: global gestures, reading the visible
 * text of the foreground app, finding nodes by text, tapping them and typing
 * into edit fields. Completely offline.
 */
class IrisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (!pkg.isNullOrBlank()) foregroundPackage = pkg
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun perform(action: String): Boolean = when (action) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "scroll" -> scrollForward()
        else -> false
    }

    private fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollable(root) ?: return false
        return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            findScrollable(node.getChild(i))?.let { return it }
        }
        return null
    }

    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val out = StringBuilder()
        collectText(root, out)
        return out.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append(". ") }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() && node.text == null }
            ?.let { out.append(it).append(". ") }
        for (i in 0 until node.childCount) collectText(node.getChild(i), out)
    }

    // ---------------------------------------------------------------- finding

    private fun walk(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null || out.size > 900) return
        out.add(node)
        for (i in 0 until node.childCount) walk(node.getChild(i), out)
    }

    fun allNodes(): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<AccessibilityNodeInfo>()
        walk(root, out)
        return out
    }

    private fun label(node: AccessibilityNodeInfo): String {
        val t = node.text?.toString().orEmpty()
        val d = node.contentDescription?.toString().orEmpty()
        return (t + " " + d).trim().lowercase()
    }

    /** Finds the best node whose text or description matches [query]. */
    fun find(query: String, clickableOnly: Boolean = false): AccessibilityNodeInfo? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val candidates = allNodes().filter { !clickableOnly || it.isClickable }
        return candidates.firstOrNull { label(it) == q }
            ?: candidates.firstOrNull { label(it).startsWith(q) }
            ?: candidates.firstOrNull { label(it).contains(q) }
    }

    private fun clickable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = node
        var hops = 0
        while (cur != null && hops < 8) {
            if (cur.isClickable) return cur
            cur = cur.parent
            hops++
        }
        return null
    }

    /** Taps the first node matching any of [queries]. */
    fun click(vararg queries: String): Boolean {
        for (q in queries) {
            val node = find(q) ?: continue
            val target = clickable(node) ?: continue
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    fun editableFields(): List<AccessibilityNodeInfo> =
        allNodes().filter { it.isEditable && it.isVisibleToUser }

    fun type(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Types [text] into the first editable field on screen. */
    fun typeIntoFirstField(text: String): Boolean {
        val field = editableFields().firstOrNull() ?: return false
        return type(field, text)
    }

    /** Fills visible editable fields in order with [values]. Returns filled count. */
    fun fillFields(values: List<String>): Int {
        val fields = editableFields()
        var filled = 0
        for (i in values.indices) {
            val field = fields.getOrNull(i) ?: break
            if (type(field, values[i])) filled++
        }
        return filled
    }

    /** Describes the editable fields on screen (hint / label text). */
    fun describeFields(): List<String> = editableFields().map { node ->
        val hint = node.hintText?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val txt = node.text?.toString().orEmpty()
        listOf(hint, desc, txt).firstOrNull { it.isNotBlank() } ?: "field"
    }

    companion object {
        @Volatile
        var instance: IrisAccessibilityService? = null
            private set

        @Volatile
        var foregroundPackage: String = ""
            private set
    }
}
