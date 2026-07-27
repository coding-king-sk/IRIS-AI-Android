package com.irisx.ai

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.irisx.ai.ui.AssistantViewModel
import com.irisx.ai.ui.IrisRoot
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.IrisTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        requestCorePermissions()

        setContent {
            IrisTheme {
                val vm: AssistantViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
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
            }
        }
    }

    private fun requestCorePermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            needed += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            needed += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(needed.toTypedArray())
    }
}
