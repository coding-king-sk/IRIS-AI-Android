package com.irisx.ai.ui.vision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.irisx.ai.core.voice.TtsEngine
import com.irisx.ai.data.SettingsStore
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.IrisTheme
import com.irisx.ai.ui.theme.MonoLabel
import com.irisx.ai.ui.theme.MonoTiny
import java.io.File

/** Live camera understanding: labels the scene and reads any text aloud. */
class CameraVisionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IrisTheme {
                VisionScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun VisionScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val tts = remember { TtsEngine(context) }
    val settings = remember { SettingsStore(context) }
    var status by remember { mutableStateOf("Camera taiyaar\u2026 SCAN dabao.") }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    Box(modifier = Modifier.fillMaxSize().background(IrisColors.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    runCatching {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = status,
                style = MonoTiny,
                color = IrisColors.Zinc100,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(IrisColors.Scrim)
                    .border(1.dp, IrisColors.GlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VisionChip(if (busy) "SCANNING\u2026" else "SCAN") {
                    if (busy) return@VisionChip
                    busy = true
                    status = "Dekh raha hoon\u2026"
                    val photo = File(context.cacheDir, "iris-vision.jpg")
                    val options = ImageCapture.OutputFileOptions.Builder(photo).build()
                    imageCapture.takePicture(
                        options,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                analyse(context, photo) { text ->
                                    busy = false
                                    status = text
                                    if (settings.ttsEnabled) {
                                        tts.speak(text, settings.speechRate) {}
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                busy = false
                                status = "Capture fail hua: " + (exception.message ?: "unknown")
                            }
                        }
                    )
                }
                VisionChip("CLOSE", onClose)
            }
        }
    }
}

private fun analyse(
    context: android.content.Context,
    photo: File,
    onDone: (String) -> Unit
) {
    val image = runCatching {
        InputImage.fromFilePath(context, android.net.Uri.fromFile(photo))
    }.getOrNull()
    if (image == null) {
        onDone("Photo padhi nahi ja saki.")
        return
    }

    val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    labeler.process(image)
        .addOnSuccessListener { labels ->
            val names = labels
                .sortedByDescending { it.confidence }
                .take(4)
                .joinToString(", ") { it.text }

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val words = result.text.trim().replace("\n", " ").take(220)
                    val parts = mutableListOf<String>()
                    if (names.isNotBlank()) parts += "Saamne dikh raha hai: " + names
                    if (words.isNotBlank()) parts += "Likha hai: " + words
                    onDone(if (parts.isEmpty()) "Kuch pehchan nahi paya." else parts.joinToString(". "))
                }
                .addOnFailureListener {
                    onDone(
                        if (names.isBlank()) "Kuch pehchan nahi paya."
                        else "Saamne dikh raha hai: " + names
                    )
                }
        }
        .addOnFailureListener {
            onDone("Pehchan nahi paya. Roshni thodi behtar karo.")
        }
}

@Composable
private fun VisionChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MonoLabel,
        color = IrisColors.Accent,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(IrisColors.AccentSoft)
            .border(1.dp, IrisColors.AccentBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}
