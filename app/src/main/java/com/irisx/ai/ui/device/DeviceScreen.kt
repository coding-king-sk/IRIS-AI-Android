package com.irisx.ai.ui.device

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irisx.ai.core.tools.AppLauncher
import com.irisx.ai.ui.components.GlassPanel
import com.irisx.ai.ui.components.PanelHeader
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoLabel
import com.irisx.ai.ui.theme.MonoTiny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android counterpart of the desktop "APP" + "Phone" views:
 * installed-app index (voice-launchable) plus device telemetry.
 */
@Composable
fun DeviceScreen() {
    val context = LocalContext.current
    val apps by produceState(initialValue = emptyList<AppLauncher.AppItem>()) {
        value = withContext(Dispatchers.IO) { AppLauncher.installedApps(context) }
    }
    val battery = remember { batteryLevel(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GlassPanel(modifier = Modifier.weight(1f), radius = 14, contentPadding = 10) {
                Text("BATTERY", style = MonoTiny, color = IrisColors.Zinc600)
                Text("$battery%", style = MonoLabel, color = IrisColors.Accent)
            }
            GlassPanel(modifier = Modifier.weight(1f), radius = 14, contentPadding = 10) {
                Text("ANDROID", style = MonoTiny, color = IrisColors.Zinc600)
                Text(Build.VERSION.RELEASE, style = MonoLabel, color = IrisColors.Accent)
            }
            GlassPanel(modifier = Modifier.weight(1f), radius = 14, contentPadding = 10) {
                Text("APPS", style = MonoTiny, color = IrisColors.Zinc600)
                Text(apps.size.toString(), style = MonoLabel, color = IrisColors.Accent)
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18, contentPadding = 12) {
            PanelHeader(
                title = "SYSTEM APPLICATIONS",
                subtitle = "INDEXED · VOICE LAUNCHABLE",
                trailing = { Text("LOCAL", style = MonoTiny, color = IrisColors.Accent) }
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(apps) { app ->
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { AppLauncher.launch(context, app.packageName) },
                    radius = 14,
                    contentPadding = 12
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, color = IrisColors.Zinc200, fontSize = 13.sp)
                            Text(app.packageName, style = MonoTiny, color = IrisColors.Zinc600)
                        }
                        Text("OPEN", style = MonoTiny, color = IrisColors.Accent)
                    }
                }
            }
        }
    }
}

private fun batteryLevel(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
}
