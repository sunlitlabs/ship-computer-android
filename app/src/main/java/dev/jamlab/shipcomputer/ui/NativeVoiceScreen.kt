package dev.jamlab.shipcomputer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jamlab.shipcomputer.auth.AuthManager

private val Cyan   = Color(0xFF00E5FF)
private val BgDark = Color(0xFF0A0A0F)
private val BgCard = Color(0xFF12121A)
private val Dim    = Color(0xFF888899)

@Composable
fun NativeVoiceScreen(
    authManager: AuthManager,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val vm: VoiceViewModel = viewModel { VoiceViewModel(authManager) }

    val status   by vm.status.collectAsState()
    val muted    by vm.muted.collectAsState()
    val error    by vm.error.collectAsState()
    val persona  by vm.persona.collectAsState()
    val activity by vm.activity.collectAsState()

    var showSettings by remember { mutableStateOf(false) }

    // Wire badge button to mic toggle
    LaunchedEffect(Unit) {
        dev.jamlab.shipcomputer.service.AudioForegroundService.onBadgePress = {
            vm.toggleMic()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SHIP COMPUTER",
                    color = Cyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, "Settings", tint = Dim)
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Status orb ──────────────────────────────────────────────────
            StatusOrb(status)

            Spacer(Modifier.height(12.dp))

            Text(
                persona,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                statusLabel(status, muted),
                color = Dim,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )

            // ── Error banner ─────────────────────────────────────────────────
            error?.let { msg ->
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A0A0A), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(msg, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Activity log ─────────────────────────────────────────────────
            val listState = rememberLazyListState()
            LaunchedEffect(activity.size) {
                if (activity.isNotEmpty()) listState.animateScrollToItem(activity.lastIndex)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(BgCard, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                if (activity.isEmpty()) {
                    Text(
                        "Activity will appear here",
                        color = Dim,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(state = listState) {
                        items(activity) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    entry.time,
                                    color = Dim,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    entry.text,
                                    color = if (entry.isError) Color(0xFFFF6B6B) else Color.White,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Buttons ───────────────────────────────────────────────────────
            when (status) {
                VoiceStatus.DISCONNECTED, VoiceStatus.ERROR -> {
                    Button(
                        onClick = { vm.connect(context) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan)
                    ) {
                        Text("GO LIVE", color = BgDark, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }

                VoiceStatus.CONNECTING -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BgCard)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Cyan,
                            strokeWidth = 2.dp
                        )
                    }
                }

                else -> {
                    // Mic toggle
                    Button(
                        onClick = { vm.toggleMic() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (muted) Color(0xFF2A1A1A) else Cyan.copy(alpha = 0.15f)
                        )
                    ) {
                        Text(
                            if (muted) "UNMUTE MIC" else "MUTE MIC",
                            color = if (muted) Color(0xFFFF6B6B) else Cyan,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { vm.disconnect(context) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Dim)
                    ) {
                        Text("END SESSION", fontSize = 13.sp, letterSpacing = 1.sp)
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }

    if (showSettings) {
        SettingsSheet(
            authManager = authManager,
            onDismiss   = { showSettings = false },
            onLogout    = {
                showSettings = false
                vm.disconnect(context)
                authManager.logout()
                onLogout()
            }
        )
    }
}

@Composable
private fun StatusOrb(status: VoiceStatus) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue  = 1.18f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    val (color, shouldPulse) = when (status) {
        VoiceStatus.DISCONNECTED -> Dim to false
        VoiceStatus.CONNECTING   -> Cyan.copy(alpha = 0.5f) to true
        VoiceStatus.READY        -> Cyan.copy(alpha = 0.35f) to false
        VoiceStatus.USER_SPEAKING -> Color(0xFF69FF47) to true
        VoiceStatus.RESPONDING   -> Cyan to true
        VoiceStatus.ERROR        -> Color(0xFFFF5252) to false
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(if (shouldPulse) pulse else 1f)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    when (status) {
                        VoiceStatus.USER_SPEAKING -> Color(0xFF2A5A1A)
                        VoiceStatus.RESPONDING    -> Color(0xFF0A2A33)
                        VoiceStatus.CONNECTING    -> Color(0xFF0A1A2A)
                        VoiceStatus.ERROR         -> Color(0xFF2A0A0A)
                        else                      -> BgCard
                    },
                    CircleShape
                )
        )
    }
}

private fun statusLabel(status: VoiceStatus, muted: Boolean) = when {
    status == VoiceStatus.DISCONNECTED -> "tap GO LIVE to connect"
    status == VoiceStatus.CONNECTING   -> "connecting…"
    status == VoiceStatus.ERROR        -> "connection error"
    muted                              -> "mic muted"
    status == VoiceStatus.USER_SPEAKING -> "you're speaking…"
    status == VoiceStatus.RESPONDING   -> "responding…"
    else                               -> "listening"
}
