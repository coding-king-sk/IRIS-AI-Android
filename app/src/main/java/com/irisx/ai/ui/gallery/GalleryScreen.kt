package com.irisx.ai.ui.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.irisx.ai.ui.components.GlassPanel
import com.irisx.ai.ui.components.PanelHeader
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoLabel
import com.irisx.ai.ui.theme.MonoTiny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local device gallery — reads MediaStore, no network at all. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    val images by produceState(initialValue = emptyList<Uri>()) {
        value = withContext(Dispatchers.IO) { queryImages(context) }
    }
    var viewing by remember { mutableStateOf<Uri?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
                PanelHeader(
                    title = "OPTICAL ARCHIVE",
                    subtitle = images.size.toString() + " LOCAL IMAGES",
                    trailing = { Text("MEDIASTORE", style = MonoTiny, color = IrisColors.Accent) }
                )
                Text(
                    "Tap = 3D view · Long press = photo ka text copy (OCR)",
                    style = MonoTiny,
                    color = IrisColors.Zinc600,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (images.isEmpty()) {
                Text(
                    "No images indexed. Grant media permission from Settings.",
                    style = MonoTiny,
                    color = IrisColors.Zinc600
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(images) { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = { viewing = uri },
                                onLongClick = { copyPhotoText(context, uri) }
                            )
                    )
                }
            }
        }

        viewing?.let { uri ->
            Photo3DViewer(
                uri = uri,
                onClose = { viewing = null },
                onCopyText = { copyPhotoText(context, uri) }
            )
        }
    }
}

/**
 * iPhone-style 3D photo card: drag to tilt in space, pinch to zoom, release to
 * spring back. Pure Compose graphicsLayer, no extra libraries.
 */
@Composable
private fun Photo3DViewer(uri: Uri, onClose: () -> Unit, onCopyText: () -> Unit) {
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }

    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    val animTiltX by animateFloatAsState(targetValue = tiltX, animationSpec = springSpec, label = "tiltX")
    val animTiltY by animateFloatAsState(targetValue = tiltY, animationSpec = springSpec, label = "tiltY")
    val animZoom by animateFloatAsState(targetValue = zoom, animationSpec = springSpec, label = "zoom")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IrisColors.Scrim)
            .pointerInput(uri) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(0.8f, 3f)
                    tiltY = (tiltY + pan.x / 9f).coerceIn(-32f, 32f)
                    tiltX = (tiltX - pan.y / 9f).coerceIn(-32f, 32f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(0.78f)
                .graphicsLayer {
                    rotationX = animTiltX
                    rotationY = animTiltY
                    scaleX = animZoom
                    scaleY = animZoom
                    cameraDistance = 16f * density
                    shadowElevation = 28f
                    shape = RoundedCornerShape(20.dp)
                    clip = true
                }
                .border(1.dp, IrisColors.AccentBorder, RoundedCornerShape(20.dp))
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ViewerChip("COPY TEXT", onCopyText)
            ViewerChip("RESET") {
                tiltX = 0f
                tiltY = 0f
                zoom = 1f
            }
            ViewerChip("CLOSE", onClose)
        }

        Text(
            "DRAG = TILT · PINCH = ZOOM",
            style = MonoTiny,
            color = IrisColors.Zinc500,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 26.dp)
        )
    }
}

@Composable
private fun ViewerChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MonoLabel,
        color = IrisColors.Accent,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(IrisColors.AccentSoft)
            .border(1.dp, IrisColors.AccentBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/** On-device OCR: pulls all the text out of a photo into the clipboard. */
private fun copyPhotoText(context: Context, uri: Uri) {
    val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
    if (image == null) {
        Toast.makeText(context, "Photo padhi nahi ja saki.", Toast.LENGTH_SHORT).show()
        return
    }
    Toast.makeText(context, "Text nikal raha hoon…", Toast.LENGTH_SHORT).show()
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(image)
        .addOnSuccessListener { result ->
            val text = result.text.trim()
            if (text.isBlank()) {
                Toast.makeText(context, "Is photo me koi text nahi mila.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("IRIS OCR", text))
            Toast.makeText(
                context,
                "Text copy ho gaya (" + text.length + " characters).",
                Toast.LENGTH_LONG
            ).show()
        }
        .addOnFailureListener {
            Toast.makeText(context, "OCR fail hua: " + (it.message ?: "unknown"), Toast.LENGTH_LONG).show()
        }
}

private fun queryImages(context: Context): List<Uri> {
    val out = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC"
    runCatching {
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            var count = 0
            while (cursor.moveToNext() && count < 300) {
                val id = cursor.getLong(idColumn)
                out += Uri.withAppendedPath(collection, id.toString())
                count++
            }
        }
    }
    return out
}
