package com.irisx.ai.core.automation

import com.irisx.ai.service.IrisAccessibilityService

/**
 * Small helper layer that turns "do this, then that" voice requests into a
 * sequence of accessibility taps. Everything is polled with short sleeps
 * because UI needs time to appear after each step.
 *
 * These functions must be called from a background thread (tools already run
 * off the main thread).
 */
object Automator {

    const val UNAVAILABLE = "Accessibility service off hai. Settings > System Access > Accessibility se IRIS ko on karo, tabhi main tap kar paunga."

    fun available(): Boolean = IrisAccessibilityService.instance != null

    private fun service(): IrisAccessibilityService? = IrisAccessibilityService.instance

    fun sleep(ms: Long) {
        runCatching { Thread.sleep(ms) }
    }

    /** Waits until [check] is true or [timeoutMs] passes. */
    fun waitFor(timeoutMs: Long = 6000, stepMs: Long = 350, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (runCatching { check() }.getOrDefault(false)) return true
            sleep(stepMs)
        }
        return false
    }

    fun waitForApp(packagePrefix: String, timeoutMs: Long = 8000): Boolean =
        waitFor(timeoutMs) {
            IrisAccessibilityService.foregroundPackage.startsWith(packagePrefix)
        }

    fun waitAndClick(vararg queries: String): Boolean {
        val svc = service() ?: return false
        var done = false
        waitFor(6000) {
            done = svc.click(*queries)
            done
        }
        return done
    }

    fun click(vararg queries: String): Boolean = service()?.click(*queries) ?: false

    fun typeFirst(text: String): Boolean = service()?.typeIntoFirstField(text) ?: false

    fun screenText(): String = service()?.readScreenText().orEmpty()

    /** Common send-button labels across WhatsApp / SMS / mail apps. */
    val SEND_LABELS = arrayOf("send", "भेजें", "send message", "bhejein")

    /** Taps whichever send button is on screen. */
    fun tapSend(): Boolean = waitAndClick(*SEND_LABELS)
}
