package com.irisx.ai

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.irisx.ai.core.agent.ToolCall
import com.irisx.ai.core.agent.ToolRegistry
import com.irisx.ai.data.SettingsStore
import com.irisx.ai.ui.AssistantViewModel
import com.irisx.ai.ui.IrisRoot
import com.irisx.ai.ui.SplashOverlay
import com.irisx.ai.ui.onboarding.OnboardingScreen
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.IrisTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        requestCorePermissions()

        val store = SettingsStore(this)

        setContent {
            IrisTheme {
                var showSplash by remember { mutableStateOf(true) }
                var showOnboarding by remember { mutableStateOf(!store.onboardingDone) }
                val vm: AssistantViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()

                Box(modifier = Modifier.fillMaxSize()) {
                    IrisRoot(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(IrisColors.Black),
                        state = state,
                        onToggleConnection = vm::toggleConnection,
                        onMicToggle = vm::toggleMic,
                        onVisionMode = vm::setVisionMode,
                        onSendText = vm::submitText,
                        onStopSpeaking = vm::stopSpeaking
                    )
                    if (showOnboarding && !showSplash) {
                        OnboardingScreen(onDone = { showOnboarding = false })
                    }
                    if (showSplash) {
                        SplashOverlay(onDone = { showSplash = false })
                    }
                }
            }
        }

        runMacroFrom(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runMacroFrom(intent)
    }

    /** Home-screen macro shortcut: run the sequence straight away. */
    private fun runMacroFrom(source: Intent?) {
        val macro = source?.getStringExtra(com.irisx.ai.util.MacroShortcuts.EXTRA_MACRO)
            ?.takeIf { it.isNotBlank() }
            ?: return
        source.removeExtra(com.irisx.ai.util.MacroShortcuts.EXTRA_MACRO)

        Toast.makeText(this, macro + " chal raha hai\u2026", Toast.LENGTH_SHORT).show()
        val appContext = applicationContext
        Thread {
            val result = runCatching {
                ToolRegistry(appContext).execute(ToolCall("macro_run", mapOf("name" to macro)))
            }.getOrNull()
            val message = result?.message ?: "Shortcut chal nahi paya."
            runOnUiThread { Toast.makeText(appContext, message, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun requestCorePermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            needed += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            needed += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        permissionLauncher.launch(needed.toTypedArray())
    }
}
