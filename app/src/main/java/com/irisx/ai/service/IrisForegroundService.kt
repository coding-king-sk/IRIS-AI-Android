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
 * full pipeline (wake word -> STT -> agent -> TTS) in the background.
 *
 * It also exposes a "listen now" entry point so the Quick Settings tile, the
 * home-screen widget and the notification button can open the mic without ever
 * opening the app. In live mode the mic reopens after every answer, so a
 * conversation keeps going like a phone call.
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

        startForeground(NOTIF_ID, buildNotification("Standby \u00b7 wake word active"))
        startPipeline()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LISTEN) listenNow()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Swiping the app away should not kill the assistant. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching {
            val restart = Intent(applicationContext, IrisForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restart)
            } else {
                applicationContext.startService(restart)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun startPipeline() {
        wakeWord.start(
            onDetected = {
                tts.stop()
                listenNow()
            },
            onStatus = { /* status is surfaced through the notification only */ }
        )
    }

    /** Open the mic straight away — tile, widget, notification or wake word. */
    private fun listenNow() {
        wakeWord.pause()
        notify("Bolo\u2026 sun raha hoon")
        stt.listen(
            preferOffline = settings.offlineFirst,
            onResult = { command -> handle(command) },
            onError = {
                notify("Standby \u00b7 wake word active")
                wakeWord.resume()
            }
        )
    }

    private fun handle(command: String) {
        notify("Soch raha hoon\u2026")
        job?.cancel()
        job = scope.launch {
            val reply = agent.handle(command, network.online.value)
            history.log(command + " -> " + (reply.toolName ?: "chat"))
            notify(reply.text.take(60))
            if (settings.ttsEnabled) {
                tts.speak(reply.text, settings.speechRate) { afterReply() }
            } else {
                afterReply()
            }
        }
    }

    /** Live mode keeps the conversation open instead of going back to standby. */
    private fun afterReply() {
        if (settings.liveMode) {
            listenNow()
        } else {
            notify("Standby \u00b7 wake word active")
            wakeWord.resume()
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
        val listen = PendingIntent.getForegroundService(
            this,
            2,
            Intent(this, IrisForegroundService::class.java).setAction(ACTION_LISTEN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, IrisApp.CHANNEL_ID)
            .setContentTitle("IRIS")
            .setContentText(status)
            .setSmallIcon(R.drawable.iris_logo)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(R.drawable.iris_logo, "Bolo", listen)
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
        const val ACTION_LISTEN = "com.irisx.ai.action.LISTEN_NOW"

        fun start(context: Context) {
            val intent = Intent(context, IrisForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Open the mic without opening the app. */
        fun listenNow(context: Context) {
            val intent = Intent(context, IrisForegroundService::class.java)
                .setAction(ACTION_LISTEN)
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
