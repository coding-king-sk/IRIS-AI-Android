package com.irisx.ai.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Small vibration + tone cues so the voice loop feels physical. */
class Feedback(context: Context) {

    private val appContext = context.applicationContext

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
            as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun tick(durationMs: Long = 25L) {
        val target = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                target.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                target.vibrate(durationMs)
            }
        }
    }

    fun doubleTick() {
        val target = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                target.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0L, 20L, 60L, 20L), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                target.vibrate(longArrayOf(0L, 20L, 60L, 20L), -1)
            }
        }
    }

    fun wakeCue() = tone(ToneGenerator.TONE_PROP_BEEP, 110)

    fun doneCue() = tone(ToneGenerator.TONE_PROP_ACK, 140)

    fun errorCue() = tone(ToneGenerator.TONE_PROP_NACK, 180)

    private fun tone(type: Int, durationMs: Int) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
            generator.startTone(type, durationMs)
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { generator.release() } },
                (durationMs + 150).toLong()
            )
        }
    }
}
