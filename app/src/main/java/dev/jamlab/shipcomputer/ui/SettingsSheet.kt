package dev.jamlab.shipcomputer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jamlab.shipcomputer.BuildConfig
import dev.jamlab.shipcomputer.auth.AuthManager
import dev.jamlab.shipcomputer.update.UpdateChecker
import dev.jamlab.shipcomputer.update.UpdateResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    authManager: AuthManager,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateChecker = remember { UpdateChecker() }
    val snackbarHostState = remember { SnackbarHostState() }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<UpdateResult?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12121A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF00E5FF)
            )

            HorizontalDivider(color = Color(0xFF333344))

            SettingsRow("Signed in as", authManager.savedEmail ?: "")
            SettingsRow("Server", "computer.jamlab.dev")
            SettingsRow("Version", BuildConfig.VERSION_NAME)

            HorizontalDivider(color = Color(0xFF333344))

            Button(
                onClick = {
                    isCheckingUpdate = true
                    scope.launch {
                        val result = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
                        isCheckingUpdate = false
                        if (result != null) {
                            pendingUpdate = result
                        } else {
                            snackbarHostState.showSnackbar("Up to date")
                        }
                    }
                },
                enabled = !isCheckingUpdate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E1E2E),
                    contentColor = Color(0xFF00E5FF)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF00E5FF),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Check for Updates")
            }

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A1A1A),
                    contentColor = Color(0xFFFF5252)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign Out")
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    pendingUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            containerColor = Color(0xFF12121A),
            title = { Text("Update v${update.version} available", color = Color(0xFF00E5FF)) },
            text = {
                if (update.releaseNotes.isNotBlank()) {
                    Text(update.releaseNotes, color = Color.White, fontSize = 14.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingUpdate = null
                        isDownloading = true
                        scope.launch {
                            updateChecker.downloadAndInstall(context, update.downloadUrl)
                            isDownloading = false
                        }
                    },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Download & Install", color = Color(0xFF0A0A0F))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUpdate = null }) {
                    Text("Later", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF888899), fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}
