package dev.jamlab.shipcomputer.ui

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.jamlab.shipcomputer.BuildConfig
import dev.jamlab.shipcomputer.auth.AuthManager
import dev.jamlab.shipcomputer.bluetooth.BadgeButtonManager
import dev.jamlab.shipcomputer.service.AudioForegroundService
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainScreen(
    authManager: AuthManager,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val showSettings = remember { mutableStateOf(false) }
    val isLoading = remember { mutableStateOf(true) }
    val loadError = remember { mutableStateOf<String?>(null) }

    val badgeManager = remember { BadgeButtonManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            badgeManager.release()
            AudioForegroundService.stop(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        AndroidView(
            factory = { ctx ->
                // Subclass to prevent Chromium from natively handling media keys —
                // BadgeButtonManager dispatches them via JS, so native handling
                // would cause the voice app to receive the event twice.
                object : WebView(ctx) {
                    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                        if (event?.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                            event?.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                            event?.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) return true
                        return super.dispatchKeyEvent(event)
                    }
                }.apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = false
                        userAgentString = "ShipComputerAndroid/${BuildConfig.VERSION_NAME} $userAgentString"
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            request.grant(request.resources)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            if (request.url.toString().contains("/login")) {
                                scope.launch {
                                    val result = authManager.reAuthenticate()
                                    if (result.isSuccess) {
                                        view.loadUrl(AuthManager.BASE_URL + "/live")
                                    } else {
                                        onLogout()
                                    }
                                }
                                return true
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            isLoading.value = false
                            loadError.value = null
                            if (url.contains("/live")) {
                                AudioForegroundService.start(context, view)
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                isLoading.value = false
                                loadError.value = "Cannot connect to computer.jamlab.dev"
                            }
                        }
                    }

                    loadUrl("${AuthManager.BASE_URL}/live")
                    badgeManager.attach(this)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        )

        if (isLoading.value) {
            CircularProgressIndicator(
                color = Color(0xFF00E5FF),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        loadError.value?.let { error ->
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error, color = Color.White)
                Spacer(modifier = Modifier.size(12.dp))
                Button(
                    onClick = {
                        loadError.value = null
                        isLoading.value = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Retry", color = Color(0xFF0A0A0F))
                }
            }
        }

        FloatingActionButton(
            onClick = { showSettings.value = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
                .size(40.dp),
            containerColor = Color(0xFF00E5FF).copy(alpha = 0.4f),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(20.dp)
            )
        }

        if (showSettings.value) {
            SettingsSheet(
                authManager = authManager,
                onDismiss = { showSettings.value = false },
                onLogout = {
                    showSettings.value = false
                    AudioForegroundService.stop(context)
                    authManager.logout()
                    onLogout()
                }
            )
        }
    }
}
