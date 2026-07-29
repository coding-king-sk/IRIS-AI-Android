package com.irisx.ai.ui.macros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.irisx.ai.core.agent.ToolCall
import com.irisx.ai.core.agent.ToolRegistry
import com.irisx.ai.core.macros.MacroStore
import com.irisx.ai.ui.components.GlassPanel
import com.irisx.ai.ui.components.PanelHeader
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoLabel
import com.irisx.ai.ui.theme.MonoTiny
import com.irisx.ai.util.MacroShortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manage voice shortcuts by hand: create, run, pin to the home screen, delete.
 * Everything is local (MacroStore JSON file).
 */
@Composable
fun MacrosScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { MacroStore(context) }

    var macros by remember { mutableStateOf(store.all()) }
    var name by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(
                title = "VOICE SHORTCUTS",
                subtitle = macros.size.toString() + " SAVED MACROS",
                trailing = { Text("LOCAL", style = MonoTiny, color = IrisColors.Accent) }
            )
            Text(
                "Ek naam ke neeche kai commands. Bolo bhi sakte ho, yahan se chala bhi sakte ho.",
                style = MonoTiny,
                color = IrisColors.Zinc500,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(title = "NEW SHORTCUT")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Naam (e.g. office mode)", style = MonoTiny) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            OutlinedTextField(
                value = steps,
                onValueChange = { steps = it },
                label = { Text("Steps, comma se alag", style = MonoTiny) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip("SAVE") {
                    val parsed = steps.split(",", ";", " phir ", " then ", " aur ")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    when {
                        name.isBlank() -> status = "Naam likho."
                        parsed.isEmpty() -> status = "Kam se kam ek step likho."
                        else -> {
                            store.save(name.trim(), parsed)
                            macros = store.all()
                            status = "'" + name.trim() + "' save ho gaya (" + parsed.size + " step)."
                            name = ""
                            steps = ""
                        }
                    }
                }
                Chip("CLEAR") {
                    name = ""
                    steps = ""
                    status = ""
                }
            }
        }

        if (status.isNotBlank()) {
            Text(status, style = MonoTiny, color = IrisColors.Accent)
        }

        if (macros.isEmpty()) {
            Text(
                "Abhi koi shortcut nahi hai. Upar se banao ya bolo: shortcut banao office mode: wifi on, silent karo",
                style = MonoTiny,
                color = IrisColors.Zinc600
            )
        }

        macros.forEach { macro ->
            GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 16) {
                PanelHeader(
                    title = macro.name.uppercase(),
                    subtitle = macro.steps.size.toString() + " STEPS"
                )
                macro.steps.forEachIndexed { index, step ->
                    Text(
                        (index + 1).toString() + ". " + step,
                        style = MonoTiny,
                        color = IrisColors.Zinc400,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Chip("RUN") {
                        status = macro.name + " chal raha hai\u2026"
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                ToolRegistry(context).execute(
                                    ToolCall("macro_run", mapOf("name" to macro.name))
                                )
                            }
                            status = result.message
                        }
                    }
                    Chip("PIN") {
                        status = if (MacroShortcuts.pin(context, macro.name)) {
                            "Home screen par shortcut add karne ka prompt aa gaya."
                        } else {
                            "Ye launcher pinned shortcuts support nahi karta."
                        }
                    }
                    Chip("DELETE") {
                        store.delete(macro.name)
                        macros = store.all()
                        status = macro.name + " hata diya."
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MonoLabel,
        color = IrisColors.Accent,
        modifier = Modifier
            .androidxChip(onClick)
    )
}

private fun Modifier.androidxChip(onClick: () -> Unit): Modifier = this
    .then(Modifier)
    .let { it }
    .composedChip(onClick)
