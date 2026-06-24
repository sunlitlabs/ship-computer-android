package dev.jamlab.shipcomputer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.jamlab.shipcomputer.auth.AuthManager
import dev.jamlab.shipcomputer.ui.AppNavigation
import dev.jamlab.shipcomputer.ui.theme.ShipComputerTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ASSISTANT_LAUNCH = "dev.jamlab.shipcomputer.ASSISTANT_LAUNCH"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* proceed regardless; WebView will re-request mic if needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyAssistantWindowFlags(intent)
        requestMissingPermissions()
        val authManager = AuthManager(this)
        setContent {
            ShipComputerTheme {
                AppNavigation(authManager = authManager)
            }
        }
    }

    // Called when the Activity is already running and brought back to front
    // (e.g. badge press while app is backgrounded with screen off).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyAssistantWindowFlags(intent)
    }

    private fun applyAssistantWindowFlags(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ASSISTANT_LAUNCH, false) != true) return
        // Show over the lock screen and turn the screen on when triggered as assistant.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun requestMissingPermissions() {
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
