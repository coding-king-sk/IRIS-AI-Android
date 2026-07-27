package com.irisx.ai.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.irisx.ai.IrisApp
import com.irisx.ai.MainActivity
import com.irisx.ai.R
import com.irisx.ai.core.agent.AgentEngine
import com.irisx.ai.core.voice.SttEngine
import com.irisx.ai.core.voice.TtsEngine
import com.irisx.ai.core.voice.WakeWordEngine
import com.irisx.ai.data.HistoryStore
import com.irisx.ai.data.SettingsStore
import com.irisx.ai.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Always-on layer: keeps the wake word alive when the UI is closed and runs the
 * full offline pipeline (wake word -> STT -> agent -> TTS) in the background.
 */
class IrisForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private lateinit var settings: SettingsStore
    private lateinit var history: HistoryStore
    private lateinit var wakeWord: WakeWordEngine
    private lateinit var stt: SttEngine
    private lateinit var tts: TtsEngine
    private lateinit var agent: AgentEngine
    private lateinit var network: NetworkMonitor
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        history = HistoryStore(this)
        stt = SttEngine(this)
        tts = TtsEngine(this)
        agent = AgentEngine(this, settings)
        network = NetworkMonitor(this)
        wakeWord = WakeWordEngine(this, settings)

        startForeground(NOTIF_ID, buildNotification("Standby · wake word active"))
        startPipeline()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPipeline() {
        wakeWord.start(
            onDetected = {
                wakeWord.pause()
                notify("Listening…")
                stt.listen(
                    preferOffline = settings.offlineFirst,
                    onResult = { command -> handle(command) },
                    onError = {
                        notify("Standby · wake word active")
                        wakeWord.resume()
                    }
                )
            },
            onStatus = { /* status is surfaced through the notification only */ }
        )
    }

    private fun handle(command: String) {
        notify("Thinking…")
        job?.cancel()
        job = scope.launch {
            val reply = agent.handle(command, network.online.value)
            history.log(command + " -> " + (reply.toolName ?: "chat"))
            if (settings.ttsEnabled) {
                tts.speak(reply.text, settings.speechRate) {
                    notify("Standby · wake word active")
                    wakeWord.resume()
                }
            } else {
                notify("Standby · wake word active")
                wakeWord.resume()
            }
        }
    }

    private fun notify(status: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE)
            as? android.app.NotificationManager
        manager?.notify(NOTIF_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, IrisApp.CHANNEL_ID)
            .setContentTitle("IRIS")
            .setContentText(status)
            .setSmallIcon(R.drawable.iris_logo)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        wakeWord.stop()
        stt.destroy()
        tts.shutdown()
        network.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, IrisForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IrisForegroundService::class.java))
        }
    }
}
