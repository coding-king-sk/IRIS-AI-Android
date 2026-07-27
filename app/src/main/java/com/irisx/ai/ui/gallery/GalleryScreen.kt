package com.irisx.ai.ui.gallery

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.irisx.ai.ui.components.GlassPanel
import com.irisx.ai.ui.components.PanelHeader
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoTiny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local device gallery — reads MediaStore, no network at all. */
@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    val images by produceState(initialValue = emptyList<Uri>()) {
        value = withContext(Dispatchers.IO) { queryImages(context) }
    }

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
                )
            }
        }
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
