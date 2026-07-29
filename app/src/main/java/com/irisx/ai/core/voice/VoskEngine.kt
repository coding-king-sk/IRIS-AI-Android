package com.irisx.ai.core.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * True offline speech recognition with Vosk.
 *
 * The acoustic model is ~40 MB, far too big to ship inside the APK, so it is
 * fetched once on demand and unpacked into app storage. After that the engine
 * needs no network at all — and, unlike the system recognizer, it keeps the mic
 * open continuously without the constant beep / mic-icon flicker.
 */
class VoskEngine(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var speech: SpeechService? = null

    val modelDir: File get() = File(appContext.filesDir, "vosk-model")

    val isReady: Boolean
        get() = File(modelDir, "conf").exists() || File(modelDir, "am").exists()

    /** Blocking download + unpack. Call from a background thread. */
    fun downloadModel(onStatus: (String) -> Unit = {}): Boolean = runCatching {
        onStatus("Model download ho raha hai (~40 MB)\u2026")
        val zip = File(appContext.cacheDir, "vosk-model.zip")
        val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .build()

        http.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { response ->
            if (!response.isSuccessful) return@runCatching false
            val body = response.body ?: return@runCatching false
            zip.outputStream().use { out -> body.byteStream().copyTo(out) }
        }

        onStatus("Unzip ho raha hai\u2026")
        if (modelDir.exists()) modelDir.deleteRecursively()
        modelDir.mkdirs()
        unzip(zip, modelDir)
        zip.delete()
        flatten(modelDir)
        isReady
    }.getOrDefault(false)

    fun deleteModel() {
        stop()
        model = null
        runCatching { modelDir.deleteRecursively() }
    }

    /**
     * Listens for a single utterance. Returns false when the engine cannot be
     * started, so the caller can fall back to the system recognizer.
     */
    fun listenOnce(
        onPartial: (String) -> Unit,
        onText: (String) -> Unit,
        onFail: (String) -> Unit
    ): Boolean = startInternal(oneShot = true, onPartial = onPartial, onText = onText, onFail = onFail)

    /**
     * Keeps the mic open and reports every phrase it hears. Used by the wake
     * word loop so there is no start/stop beep on every cycle.
     */
    fun startStream(
        onPartial: (String) -> Unit,
        onText: (String) -> Unit,
        onFail: (String) -> Unit
    ): Boolean = startInternal(oneShot = false, onPartial = onPartial, onText = onText, onFail = onFail)

    private fun startInternal(
        oneShot: Boolean,
        onPartial: (String) -> Unit,
        onText: (String) -> Unit,
        onFail: (String) -> Unit
    ): Boolean {
        if (!isReady) return false
        val loaded = ensureModel() ?: return false
        stop()

        return runCatching {
            val recognizer = Recognizer(loaded, SAMPLE_RATE)
            val service = SpeechService(recognizer, SAMPLE_RATE)
            speech = service

            var finished = false
            val timeout = Runnable {
                if (oneShot && !finished) {
                    finished = true
                    stop()
                    onFail("SILENCE")
                }
            }
            if (oneShot) main.postDelayed(timeout, LISTEN_WINDOW_MS)

            fun deliver(text: String?, failure: String?) {
                if (oneShot) {
                    if (finished) return
                    finished = true
                    main.removeCallbacks(timeout)
                    stop()
                }
                if (failure != null) onFail(failure) else onText(text.orEmpty())
            }

            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    valueOf(hypothesis, "partial")
                        ?.takeIf { it.isNotBlank() }
                        ?.let(onPartial)
                }

                override fun onResult(hypothesis: String?) {
                    val text = valueOf(hypothesis, "text")
                    if (!text.isNullOrBlank()) deliver(text, null)
                }

                override fun onFinalResult(hypothesis: String?) {
                    val text = valueOf(hypothesis, "text")
                    if (!text.isNullOrBlank()) {
                        deliver(text, null)
                    } else if (oneShot) {
                        deliver(null, "NOTHING HEARD")
                    }
                }

                override fun onError(exception: Exception?) {
                    if (!oneShot) stop()
                    deliver(null, "VOSK ERROR")
                }

                override fun onTimeout() {
                    if (oneShot) deliver(null, "SILENCE")
                }
            })
            true
        }.getOrElse {
            stop()
            false
        }
    }

    fun stop() {
        val service = speech ?: return
        speech = null
        runCatching { service.stop() }
        runCatching { service.shutdown() }
    }

    private fun ensureModel(): Model? {
        model?.let { return it }
        val loaded = runCatching { Model(modelDir.absolutePath) }.getOrNull()
        model = loaded
        return loaded
    }

    private fun valueOf(json: String?, key: String): String? = runCatching {
        if (json.isNullOrBlank()) null else JSONObject(json).optString(key).trim()
    }.getOrNull()

    private fun unzip(zip: File, target: File) {
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val outFile = File(target, entry.name)
                // Zip-slip guard
                if (!outFile.canonicalPath.startsWith(target.canonicalPath)) {
                    entry = input.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> input.copyTo(out) }
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
    }

    /** Vosk zips contain a single root folder; lift its contents up one level. */
    private fun flatten(dir: File) {
        val children = dir.listFiles()?.toList().orEmpty()
        if (children.size == 1 && children[0].isDirectory) {
            val inner = children[0]
            inner.listFiles()?.forEach { child ->
                child.renameTo(File(dir, child.name))
            }
            inner.deleteRecursively()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16000.0f
        const val LISTEN_WINDOW_MS = 10000L
        const val MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"
    }
}
