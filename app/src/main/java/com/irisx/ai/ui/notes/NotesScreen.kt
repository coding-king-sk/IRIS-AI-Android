package com.irisx.ai.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irisx.ai.data.Note
import com.irisx.ai.data.NotesStore
import com.irisx.ai.ui.components.GlassPanel
import com.irisx.ai.ui.components.PanelHeader
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoTiny
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 100% offline notes module — stored as JSON in app-private storage. */
@Composable
fun NotesScreen() {
    val context = LocalContext.current
    val store = remember { NotesStore(context) }
    var notes by remember { mutableStateOf(store.all()) }
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(
                title = "MEMORY VAULT",
                subtitle = notes.size.toString() + " LOCAL NOTES",
                trailing = {
                    Text("OFFLINE", style = MonoTiny, color = IrisColors.Accent)
                }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(IrisColors.Zinc950)
                    .border(1.dp, IrisColors.GlassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(
                        color = IrisColors.Zinc100,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(IrisColors.Accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text("new note…", style = MonoTiny, color = IrisColors.Zinc600)
                        }
                        inner()
                    }
                )
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "add note",
                    tint = if (draft.isBlank()) IrisColors.Zinc600 else IrisColors.Accent,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(enabled = draft.isNotBlank()) {
                            store.add(draft.trim())
                            notes = store.all()
                            draft = ""
                        }
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notes, key = { it.id }) { note ->
                NoteCard(note = note, onDelete = {
                    store.delete(note.id)
                    notes = store.all()
                })
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()) }
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 14, contentPadding = 12) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(note.text, color = IrisColors.Zinc200, fontSize = 13.sp)
                Text(fmt.format(Date(note.createdAt)), style = MonoTiny, color = IrisColors.Zinc600)
            }
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = "delete",
                tint = IrisColors.Zinc600,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onDelete)
            )
        }
    }
}
