package dev.jamlab.shipcomputer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.jamlab.shipcomputer.auth.AuthManager

@Composable
fun AppNavigation(authManager: AuthManager) {
    var isLoggedIn by remember { mutableStateOf(authManager.isLoggedIn) }

    if (isLoggedIn) {
        MainScreen(
            authManager = authManager,
            onLogout = { isLoggedIn = false }
        )
    } else {
        LoginScreen(
            authManager = authManager,
            onLoginSuccess = { isLoggedIn = true }
        )
    }
}
