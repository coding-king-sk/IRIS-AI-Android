package com.irisx.ai.core.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.irisx.ai.data.SettingsStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Neural wake word detection with openWakeWord (ONNX Runtime).
 *
 * Why this exists: both earlier paths had a catch. The system recognizer only
 * gives short sessions (beep + mic blinking + missed words) and needs a
 * language pack; Vosk is smooth but only after a 40 MB download and it is a
 * full recognizer doing far more work than "did they say the wake word?".
 *
 * openWakeWord is a tiny always-on classifier that runs the same way whether
 * the phone is online or offline:
 *
 *   16 kHz mic -> melspectrogram.onnx -> embedding_model.onnx -> wake model
 *
 * Streaming shapes (fixed by the models themselves):
 *  - 1280 audio samples (80 ms) in, 5 mel frames of 32 bins out
 *  - every 8 new mel frames, the last 76 frames become one 96-value embedding
 *  - the last 16 embeddings become one score between 0 and 1
 *
 * Models are a few MB, so they are fetched once on demand instead of being
 * baked into the APK.
 */
class OpenWakeWord(context: Context) {

    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)

    private var env: OrtEnvironment? = null
    private var melSession: OrtSession? = null
    private var embSession: OrtSession? = null
    private var wakeSession: OrtSession? = null
    private var loadedWake: String = ""

    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var listening = false

    private val melFrames = ArrayDeque<FloatArray>()
    private val embeddings = ArrayDeque<FloatArray>()
    private var melSinceEmbedding = 0
    private var lastHit = 0L

    val modelDir: File get() = File(appContext.filesDir, "oww")

    /** File name (without .onnx) of the wake model currently selected. */
    val modelId: String get() = settings.wakeModel

    /** What the user actually has to say for the selected model. */
    val spokenPhrase: String
        get() = MODELS.entries.firstOrNull { it.value == modelId }?.key ?: "hey jarvis"

    val micGranted: Boolean
        get() = appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    val isRunning: Boolean get() = listening

    /** True once the three ONNX files for the selected wake word are present. */
    val isReady: Boolean
        get() = File(modelDir, MEL_FILE).exists() &&
            File(modelDir, EMB_FILE).exists() &&
            wakeFile().exists()

    private fun wakeFile(): File = File(modelDir, modelId + ".onnx")

    /** Blocking download of the three models. Call from a background thread. */
    fun downloadModels(onStatus: (String) -> Unit = {}): Boolean = runCatching {
        modelDir.mkdirs()
        val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()

        val wanted = listOf(MEL_FILE, EMB_FILE, modelId + ".onnx")
        for (fileName in wanted) {
            val target = File(modelDir, fileName)
            if (target.exists() && target.length() > 10000L) continue
            onStatus(fileName + " download ho rahi hai\u2026")
            val request = Request.Builder().url(BASE_URL + fileName).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching false
                val body = response.body ?: return@runCatching false
                target.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
        }
        isReady
    }.getOrDefault(false)

    fun deleteModels() {
        stop()
        closeSessions()
        runCatching { modelDir.deleteRecursively() }
    }

    /**
     * Starts always-on detection. Returns false when the models are missing or
     * the mic is unavailable, so the caller can fall back to another engine.
     */
    @SuppressLint("MissingPermission")
    fun start(onDetected: (Float) -> Unit, onError: (String) -> Unit): Boolean {
        if (listening) return true
        if (!micGranted) {
            onError("MIC PERMISSION")
            return false
        }
        if (!isReady) return false
        if (!openSessions()) {
            onError("MODEL LOAD FAIL")
            return false
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuffer > CHUNK * 4) minBuffer else CHUNK * 4
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrNull()

        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec?.release() }
            onError("MIC BUSY")
            return false
        }

        val started = runCatching { rec.startRecording() }.isSuccess &&
            rec.recordingState == AudioRecord.RECORDSTATE_RECORDING
        if (!started) {
            runCatching { rec.release() }
            onError("MIC BUSY")
            return false
        }

        recorder = rec
        resetBuffers()
        listening = true
        worker = thread(start = true, isDaemon = true, name = "iris-oww") {
            loop(rec, onDetected, onError)
        }
        return true
    }

    fun stop() {
        listening = false
        val rec = recorder
        recorder = null
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        worker = null
        resetBuffers()
    }

    // ---- Detection loop ----------------------------------------------------

    private fun loop(rec: AudioRecord, onDetected: (Float) -> Unit, onError: (String) -> Unit) {
        val shorts = ShortArray(CHUNK)
        val floats = FloatArray(CHUNK)
        var readFailures = 0

        while (listening) {
            val read = runCatching { rec.read(shorts, 0, CHUNK) }.getOrDefault(-1)
            if (read <= 0) {
                readFailures++
                if (readFailures >= 5) {
                    listening = false
                    onError("MIC READ FAIL")
                    break
                }
                continue
            }
            readFailures = 0
            // The models were trained on raw int16 values, not normalised audio.
            for (i in 0 until read) floats[i] = shorts[i].toFloat()

            val score = runCatching { score(floats, read) }.getOrDefault(0f)
            if (score >= THRESHOLD) {
                val now = System.currentTimeMillis()
                if (now - lastHit > COOLDOWN_MS) {
                    lastHit = now
                    resetBuffers()
                    onDetected(score)
                }
            }
        }
    }

    private fun score(samples: FloatArray, count: Int): Float {
        val environment = env ?: return 0f
        val mel = melSession ?: return 0f
        val emb = embSession ?: return 0f
        val wake = wakeSession ?: return 0f

        val audio = if (count == samples.size) samples else samples.copyOf(count)
        val melOut = infer(environment, mel, audio, longArrayOf(1, audio.size.toLong()))

        var offset = 0
        while (offset + MEL_BINS <= melOut.size) {
            val frame = FloatArray(MEL_BINS)
            for (bin in 0 until MEL_BINS) {
                // openWakeWord's fixed mel transform.
                frame[bin] = melOut[offset + bin] / 10f + 2f
            }
            melFrames.addLast(frame)
            melSinceEmbedding++
            offset += MEL_BINS
        }
        while (melFrames.size > MEL_WINDOW * 2) melFrames.removeFirst()

        var best = 0f
        while (melFrames.size >= MEL_WINDOW && melSinceEmbedding >= EMB_STRIDE) {
            melSinceEmbedding -= EMB_STRIDE

            val window = FloatArray(MEL_WINDOW * MEL_BINS)
            val start = melFrames.size - MEL_WINDOW
            for (f in 0 until MEL_WINDOW) {
                val frame = melFrames[start + f]
                System.arraycopy(frame, 0, window, f * MEL_BINS, MEL_BINS)
            }
            val embOut = infer(
                environment,
                emb,
                window,
                longArrayOf(1, MEL_WINDOW.toLong(), MEL_BINS.toLong(), 1)
            )
            embeddings.addLast(embOut.copyOf(EMB_SIZE))
            while (embeddings.size > EMB_WINDOW) embeddings.removeFirst()

            if (embeddings.size == EMB_WINDOW) {
                val input = FloatArray(EMB_WINDOW * EMB_SIZE)
                for (i in 0 until EMB_WINDOW) {
                    System.arraycopy(embeddings[i], 0, input, i * EMB_SIZE, EMB_SIZE)
                }
                val out = infer(
                    environment,
                    wake,
                    input,
                    longArrayOf(1, EMB_WINDOW.toLong(), EMB_SIZE.toLong())
                )
                val value = out.firstOrNull() ?: 0f
                if (value > best) best = value
            }
        }
        return best
    }

    private fun infer(
        environment: OrtEnvironment,
        session: OrtSession,
        data: FloatArray,
        shape: LongArray
    ): FloatArray {
        val inputName = session.inputNames.iterator().next()
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val first = result.iterator().next()
                return flatten(first.value.value)
            }
        }
    }

    /** ONNX gives back nested arrays whose depth differs per model. */
    private fun flatten(value: Any?): FloatArray {
        val out = ArrayList<Float>()
        collect(value, out)
        return out.toFloatArray()
    }

    private fun collect(value: Any?, out: MutableList<Float>) {
        when (value) {
            is FloatArray -> for (v in value) out.add(v)
            is Array<*> -> for (v in value) collect(v, out)
            is Float -> out.add(value)
            is Number -> out.add(value.toFloat())
            else -> Unit
        }
    }

    private fun openSessions(): Boolean = runCatching {
        val environment = env ?: OrtEnvironment.getEnvironment().also { env = it }
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(1)
        if (melSession == null) {
            melSession = environment.createSession(File(modelDir, MEL_FILE).absolutePath, options)
        }
        if (embSession == null) {
            embSession = environment.createSession(File(modelDir, EMB_FILE).absolutePath, options)
        }
        if (wakeSession == null || loadedWake != modelId) {
            runCatching { wakeSession?.close() }
            wakeSession = environment.createSession(wakeFile().absolutePath, options)
            loadedWake = modelId
        }
        true
    }.getOrDefault(false)

    private fun closeSessions() {
        runCatching { melSession?.close() }
        runCatching { embSession?.close() }
        runCatching { wakeSession?.close() }
        melSession = null
        embSession = null
        wakeSession = null
        loadedWake = ""
    }

    private fun resetBuffers() {
        melFrames.clear()
        embeddings.clear()
        melSinceEmbedding = 0
    }

    companion object {
        /** Spoken phrase -> model file name. These are the pretrained models. */
        val MODELS: Map<String, String> = linkedMapOf(
            "hey jarvis" to "hey_jarvis_v0.1",
            "alexa" to "alexa_v0.1",
            "hey mycroft" to "hey_mycroft_v0.1",
            "hey rhasspy" to "hey_rhasspy_v0.1"
        )

        private const val SAMPLE_RATE = 16000
        private const val CHUNK = 1280
        private const val MEL_BINS = 32
        private const val MEL_WINDOW = 76
        private const val EMB_STRIDE = 8
        private const val EMB_SIZE = 96
        private const val EMB_WINDOW = 16
        private const val THRESHOLD = 0.5f
        private const val COOLDOWN_MS = 2500L
        private const val MEL_FILE = "melspectrogram.onnx"
        private const val EMB_FILE = "embedding_model.onnx"
        private const val BASE_URL =
            "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/"
    }
}
