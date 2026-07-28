package com.irisx.ai.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.irisx.ai.MainActivity
import com.irisx.ai.R
import kotlin.math.abs

/**
 * Draggable floating IRIS bubble that sits on top of every app.
 *
 * - Tap: opens the assistant.
 * - Long press: reads the current screen and shows it right in the bubble
 *   subtitle ("ye kya likha hai").
 * - Live subtitle: whatever IRIS hears or says can be pushed here with
 *   [OverlayBubbleService.subtitle], so the text appears over any app.
 *
 * Needs the "Display over other apps" permission.
 */
class OverlayBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubble: ImageView? = null
    private var caption: TextView? = null
    private var captionParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideCaption = Runnable { setCaptionVisible(false) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val manager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (manager == null) {
            stopSelf()
            return
        }
        windowManager = manager

        val density = resources.displayMetrics.density
        val size = (58 * density).toInt()
        val padding = (12 * density).toInt()

        val view = ImageView(this)
        view.setImageResource(R.drawable.iris_logo)
        view.setBackgroundResource(R.drawable.bubble_bg)
        view.setPadding(padding, padding, padding, padding)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = (16 * density).toInt()
        params.y = (220 * density).toInt()

        // ---- live subtitle strip ------------------------------------------
        val text = TextView(this)
        text.setBackgroundResource(R.drawable.bubble_bg)
        text.setTextColor(Color.parseColor("#E4E4E7"))
        text.textSize = 13f
        text.maxLines = 4
        text.setPadding(
            (14 * density).toInt(),
            (10 * density).toInt(),
            (14 * density).toInt(),
            (10 * density).toInt()
        )
        text.visibility = View.GONE
        text.setOnClickListener { setCaptionVisible(false) }

        val textParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        textParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        textParams.y = (90 * density).toInt()
        textParams.horizontalMargin = 0.04f

        view.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downX = 0f
            private var downY = 0f
            private var downAt = 0L
            private var moved = false
            private var longFired = false
            private val longPress = Runnable {
                longFired = true
                speakScreen()
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        downX = event.rawX
                        downY = event.rawY
                        downAt = System.currentTimeMillis()
                        moved = false
                        longFired = false
                        handler.postDelayed(longPress, 550)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            moved = true
                            handler.removeCallbacks(longPress)
                        }
                        params.x = startX + dx
                        params.y = startY + dy
                        runCatching { windowManager?.updateViewLayout(v, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPress)
                        if (!moved && !longFired) openAssistant()
                        return true
                    }
                }
                return false
            }
        })

        runCatching { manager.addView(view, params) }
        runCatching { manager.addView(text, textParams) }
        bubble = view
        caption = text
        captionParams = textParams
        active = this
        show("IRIS ready · tap to open, long press to read screen", 2500)
    }

    /** "Ye kya likha hai" straight from the bubble. */
    private fun speakScreen() {
        val svc = IrisAccessibilityService.instance
        if (svc == null) {
            show("Accessibility off hai — Settings > System Access se on karo.", 4000)
            return
        }
        val screen = svc.readScreenText().trim()
        if (screen.isBlank()) {
            show("Is screen par padhne layak text nahi mila.", 3000)
            return
        }
        val trimmed = if (screen.length > 320) screen.take(320) + "…" else screen
        show(trimmed, 9000)
    }

    private fun show(text: String, holdMs: Long) {
        handler.post {
            caption?.text = text
            setCaptionVisible(true)
            handler.removeCallbacks(hideCaption)
            if (holdMs > 0) handler.postDelayed(hideCaption, holdMs)
        }
    }

    private fun setCaptionVisible(visible: Boolean) {
        caption?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun openAssistant() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(EXTRA_FROM_BUBBLE, true)
        runCatching { startActivity(intent) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        bubble?.let { view -> runCatching { windowManager?.removeView(view) } }
        caption?.let { view -> runCatching { windowManager?.removeView(view) } }
        bubble = null
        caption = null
        windowManager = null
        if (active === this) active = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FROM_BUBBLE = "from_bubble"

        @Volatile
        private var active: OverlayBubbleService? = null

        val running: Boolean
            get() = active != null

        /** Pushes a live subtitle line into the bubble (heard or spoken text). */
        fun subtitle(text: String, holdMs: Long = 5000) {
            if (text.isBlank()) return
            active?.show(text, holdMs)
        }

        fun start(context: Context) {
            runCatching {
                context.startService(Intent(context, OverlayBubbleService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, OverlayBubbleService::class.java))
            }
        }

        fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun requestPermission(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + context.packageName)
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}
