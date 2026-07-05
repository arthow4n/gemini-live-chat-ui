package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.ChatApp
import com.example.ui.ObsidianBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request Notification Permission on Android 13+ to ensure background controls are visible
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val notificationPermission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, notificationPermission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (!isGranted) {
                        Toast.makeText(this, "Notification permission is recommended to control background voice sessions.", Toast.LENGTH_LONG).show()
                    }
                }.launch(notificationPermission)
            }
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBackground
                ) {
                    // Modern Jetpack Compose runtime permission handler
                    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            viewModel.startRecordingVoice()
                        } else {
                            Toast.makeText(
                                this,
                                "Microphone access is required for real-time voice chat.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    ChatApp(
                        viewModel = viewModel,
                        requestMicrophonePermission = { onPermissionGranted ->
                            val permission = Manifest.permission.RECORD_AUDIO
                            val isGranted = ContextCompat.checkSelfPermission(
                                this,
                                permission
                            ) == PackageManager.PERMISSION_GRANTED

                            if (isGranted) {
                                onPermissionGranted()
                            } else {
                                recordAudioPermissionLauncher.launch(permission)
                            }
                        }
                    )
                }
            }
        }
    }
}
