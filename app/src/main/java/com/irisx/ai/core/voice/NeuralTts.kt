package com.irisx.ai.core.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Fully on-device neural text to speech.
 *
 * Runs a VITS model (the same family Piper uses) through ONNX Runtime, which
 * the app already ships for the neural wake word. The model is NOT bundled in
 * the APK - it is downloaded once on demand (~30 MB) into filesDir/ntts, so a
 * fresh install stays small and everything after that works offline.
 *
 * Pipeline: text -> words -> lexicon lookup (word to phoneme tokens) -> token
 * ids -> VITS -> float PCM -> AudioTrack.
 *
 * The lexicon based model was chosen on purpose: Piper's own voices need the
 * native espeak-ng phonemiser, which cannot be done in pure Kotlin. This one
 * ships its own lexicon.txt, so no native phonemiser is required.
 *
 * If anything at all fails (no model, download error, unknown ONNX shape) the
 * caller falls back to the phone's own TTS engine.
 */
object NeuralTts {

    private const val BASE =
        "https://huggingface.co/csukuangfj/vits-icefall-en_US-ljspeech/resolve/main/"

    private val FILES = listOf("model.onnx", "tokens.txt", "lexicon.txt")

    private val worker = Executors.newSingleThreadExecutor()

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var tokens: Map<String, Long> = emptyMap()
    private var lexicon: Map<String, List<String>> = emptyMap()
    private var sampleRate = 22050

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var cancelled = false

    @Volatile
    var downloading = false
        private set

    val loaded: Boolean get() = session != null

    fun dir(context: Context): File = File(context.filesDir, "ntts")

    fun installed(context: Context): Boolean =
        FILES.all { File(dir(context), it).length() > 1024L }

    fun sizeMb(context: Context): Long {
        var total = 0L
        for (f in FILES) total += File(dir(context), f).length()
        return total / (1024L * 1024L)
    }

    fun delete(context: Context) {
        stop()
        runCatching { session?.close() }
        session = null
        runCatching { dir(context).deleteRecursively() }
    }

    /** Blocking download of the three model files. Safe to call off the UI thread. */
    fun download(context: Context, onFile: (String) -> Unit = {}): Boolean {
        if (downloading) return false
        downloading = true
        val target = dir(context)
        if (!target.exists()) target.mkdirs()
        val ok = runCatching {
            for (name in FILES) {
                val out = File(target, name)
                if (out.length() > 1024L) continue
                onFile(name)
                val part = File(target, name + ".part")
                val conn = URL(BASE + name).openConnection() as HttpURLConnection
                conn.connectTimeout = 30000
                conn.readTimeout = 180000
                conn.instanceFollowRedirects = true
                conn.inputStream.use { input ->
                    part.outputStream().use { output -> input.copyTo(output) }
                }
                conn.disconnect()
                if (out.exists()) out.delete()
                part.renameTo(out)
            }
            true
        }.getOrElse { false }
        downloading = false
        return ok
    }

    /** Loads the model + dictionaries into memory. Returns false when unavailable. */
    @Synchronized
    fun prepare(context: Context): Boolean {
        if (session != null) return true
        if (!installed(context)) return false
        return runCatching {
            val e = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions()
            runCatching { opts.setIntraOpNumThreads(2) }
            val s = e.createSession(File(dir(context), "model.onnx").absolutePath, opts)
            runCatching {
                val meta = s.metadata.customMetadata
                meta["sample_rate"]?.toIntOrNull()?.let { sampleRate = it }
            }
            tokens = readTokens(File(dir(context), "tokens.txt"))
            lexicon = readLexicon(File(dir(context), "lexicon.txt"))
            env = e
            session = s
            tokens.isNotEmpty() && lexicon.isNotEmpty()
        }.getOrElse { false }
    }

    private fun readTokens(file: File): Map<String, Long> {
        val map = HashMap<String, Long>()
        file.forEachLine { raw ->
            val line = raw.trimEnd('\n', '\r')
            if (line.isEmpty()) return@forEachLine
            val cut = line.lastIndexOf(' ')
            if (cut <= 0) return@forEachLine
            val symbol = line.substring(0, cut)
            val id = line.substring(cut + 1).trim().toLongOrNull() ?: return@forEachLine
            map[symbol] = id
        }
        return map
    }

    private fun readLexicon(file: File): Map<String, List<String>> {
        val map = HashMap<String, List<String>>()
        file.forEachLine { raw ->
            val parts = raw.trim().split(Regex("\\s+"))
            if (parts.size < 2) return@forEachLine
            map[parts[0].lowercase(Locale.ROOT)] = parts.drop(1)
        }
        return map
    }

    /** Text to token ids, blank separated (standard VITS inference layout). */
    private fun encode(text: String): LongArray {
        val blank = tokens["_"] ?: 0L
        val ids = ArrayList<Long>()
        ids.add(blank)
        val words = text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9'.,?! ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        for (word in words) {
            val core = word.trim('.', ',', '?', '!')
            val phones = lexicon[core]
                ?: lexicon[core.trim('\'')]
                ?: core.map { it.toString() }.filter { tokens.containsKey(it) }
            for (p in phones) {
                val id = tokens[p] ?: continue
                ids.add(id)
                ids.add(blank)
            }
            val tail = word.lastOrNull()
            if (tail != null && !tail.isLetterOrDigit()) {
                tokens[tail.toString()]?.let {
                    ids.add(it)
                    ids.add(blank)
                }
            }
        }
        return ids.toLongArray()
    }

    private fun flatten(value: Any?, sink: MutableList<Float>) {
        when (value) {
            is FloatArray -> for (f in value) sink.add(f)
            is Array<*> -> for (v in value) flatten(v, sink)
            is Float -> sink.add(value)
            else -> Unit
        }
    }

    private fun synth(ids: LongArray, rate: Float): FloatArray? {
        val e = env ?: return null
        val s = session ?: return null
        if (ids.size < 3) return null
        return runCatching {
            val names = s.inputNames
            val inputs = HashMap<String, OnnxTensor>()
            inputs["x"] = OnnxTensor.createTensor(
                e, LongBuffer.wrap(ids), longArrayOf(1L, ids.size.toLong())
            )
            val lenName = when {
                names.contains("x_length") -> "x_length"
                names.contains("x_lengths") -> "x_lengths"
                else -> null
            }
            if (lenName != null) {
                inputs[lenName] = OnnxTensor.createTensor(
                    e,
                    LongBuffer.wrap(longArrayOf(ids.size.toLong())),
                    longArrayOf(1L)
                )
            }
            if (names.contains("noise_scale")) {
                inputs["noise_scale"] = OnnxTensor.createTensor(
                    e, FloatBuffer.wrap(floatArrayOf(0.667f)), longArrayOf(1L)
                )
            }
            if (names.contains("noise_scale_w")) {
                inputs["noise_scale_w"] = OnnxTensor.createTensor(
                    e, FloatBuffer.wrap(floatArrayOf(0.8f)), longArrayOf(1L)
                )
            }
            if (names.contains("length_scale")) {
                val scale = 1.0f / rate.coerceIn(0.6f, 1.6f)
                inputs["length_scale"] = OnnxTensor.createTensor(
                    e, FloatBuffer.wrap(floatArrayOf(scale)), longArrayOf(1L)
                )
            }
            if (names.contains("sid")) {
                inputs["sid"] = OnnxTensor.createTensor(
                    e, LongBuffer.wrap(longArrayOf(0L)), longArrayOf(1L)
                )
            }
            val sink = ArrayList<Float>()
            s.run(inputs).use { result ->
                for (entry in result) {
                    flatten(entry.value.value, sink)
                    break
                }
            }
            for (t in inputs.values) runCatching { t.close() }
            sink.toFloatArray()
        }.getOrNull()
    }

    private fun play(pcm: FloatArray) {
        if (pcm.isEmpty()) return
        val shorts = ShortArray(pcm.size) {
            (pcm[it].coerceIn(-1f, 1f) * 32000f).toInt().toShort()
        }
        val min = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(min * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        runCatching {
            t.play()
            var offset = 0
            val chunk = 2048
            while (offset < shorts.size && !cancelled) {
                val count = minOf(chunk, shorts.size - offset)
                val written = t.write(shorts, offset, count)
                if (written <= 0) break
                offset += written
            }
            if (!cancelled) runCatching { t.stop() }
        }
        runCatching { t.release() }
        if (track === t) track = null
    }

    /**
     * Speaks [text] with the neural voice. Returns false immediately when the
     * model is not ready, so the caller can fall back to the system engine.
     */
    fun speak(context: Context, text: String, rate: Float, onDone: () -> Unit): Boolean {
        if (!prepare(context)) return false
        cancelled = false
        worker.execute {
            runCatching {
                val sentences = text.split(Regex("(?<=[.!?])\\s+"))
                    .filter { it.isNotBlank() }
                    .ifEmpty { listOf(text) }
                for (sentence in sentences) {
                    if (cancelled) break
                    val pcm = synth(encode(sentence), rate) ?: continue
                    if (cancelled) break
                    play(pcm)
                }
            }
            onDone()
        }
        return true
    }

    fun stop() {
        cancelled = true
        val t = track ?: return
        runCatching { t.pause() }
        runCatching { t.flush() }
        runCatching { t.stop() }
    }
}
