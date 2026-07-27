package com.irisx.ai.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.irisx.ai.MainActivity
import com.irisx.ai.R
import kotlin.math.abs

/**
 * Draggable floating IRIS bubble that sits on top of every app.
 * Tap opens the assistant, drag moves it. Needs "Display over other apps".
 */
class OverlayBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubble: ImageView? = null

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

        view.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downX = 0f
            private var downY = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        downX = event.rawX
                        downY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        params.x = startX + dx
                        params.y = startY + dy
                        runCatching { windowManager?.updateViewLayout(v, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) openAssistant()
                        return true
                    }
                }
                return false
            }
        })

        runCatching { manager.addView(view, params) }
        bubble = view
    }

    private fun openAssistant() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(EXTRA_FROM_BUBBLE, true)
        runCatching { startActivity(intent) }
    }

    override fun onDestroy() {
        bubble?.let { view -> runCatching { windowManager?.removeView(view) } }
        bubble = null
        windowManager = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FROM_BUBBLE = "from_bubble"

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
