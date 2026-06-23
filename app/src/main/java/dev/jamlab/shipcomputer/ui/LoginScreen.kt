package dev.jamlab.shipcomputer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jamlab.shipcomputer.BuildConfig
import dev.jamlab.shipcomputer.auth.AuthManager
import dev.jamlab.shipcomputer.update.UpdateChecker
import dev.jamlab.shipcomputer.update.UpdateResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    authManager: AuthManager,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateChecker = remember { UpdateChecker() }
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    var email by remember { mutableStateOf(authManager.savedEmail ?: "") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<UpdateResult?>(null) }
    var showUpToDate by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    val emailNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.EmailAddress),
            onFill = { email = it }
        ).also { autofillTree += it }
    }
    val passwordNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { password = it }
        ).also { autofillTree += it }
    }

    fun doLogin() {
        if (email.isBlank() || password.isBlank() || isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = authManager.login(email.trim(), password)
            isLoading = false
            result.fold(
                onSuccess = { onLoginSuccess() },
                onFailure = { errorMessage = it.message ?: "Login failed" }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SHIP COMPUTER",
                color = Color(0xFF00E5FF),
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 6.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    focusedLabelColor = Color(0xFF00E5FF),
                    cursorColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF444466),
                    unfocusedLabelColor = Color(0xFF888899),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { emailNode.boundingBox = it.boundsInWindow() }
                    .onFocusChanged { state ->
                        autofill?.run {
                            if (state.isFocused) requestAutofillForNode(emailNode)
                            else cancelAutofillForNode(emailNode)
                        }
                    }
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { doLogin() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    focusedLabelColor = Color(0xFF00E5FF),
                    cursorColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF444466),
                    unfocusedLabelColor = Color(0xFF888899),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { passwordNode.boundingBox = it.boundsInWindow() }
                    .onFocusChanged { state ->
                        autofill?.run {
                            if (state.isFocused) requestAutofillForNode(passwordNode)
                            else cancelAutofillForNode(passwordNode)
                        }
                    }
            )

            errorMessage?.let {
                Text(text = it, color = Color(0xFFFF5252), fontSize = 14.sp)
            }

            Button(
                onClick = { doLogin() },
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF0A0A0F),
                    disabledContainerColor = Color(0xFF004D60),
                    disabledContentColor = Color(0xFF888899)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF0A0A0F),
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Signing in…")
                } else {
                    Text("SIGN IN", fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
                }
            }

            TextButton(
                onClick = {
                    isCheckingUpdate = true
                    scope.launch {
                        val result = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
                        isCheckingUpdate = false
                        if (result != null) {
                            pendingUpdate = result
                        } else {
                            showUpToDate = true
                        }
                    }
                },
                enabled = !isCheckingUpdate,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF444466))
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = Color(0xFF444466),
                        strokeWidth = 1.5.dp
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text("v${BuildConfig.VERSION_NAME}  •  Check for update", fontSize = 12.sp)
            }
        }
    }

    // Already up to date
    if (showUpToDate) {
        AlertDialog(
            onDismissRequest = { showUpToDate = false },
            containerColor = Color(0xFF12121A),
            title = { Text("Up to date", color = Color(0xFF00E5FF)) },
            text = { Text("v${BuildConfig.VERSION_NAME} is the latest version.", color = Color.White) },
            confirmButton = {
                TextButton(onClick = { showUpToDate = false }) {
                    Text("OK", color = Color(0xFF00E5FF))
                }
            }
        )
    }

    // Update available
    pendingUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            containerColor = Color(0xFF12121A),
            title = { Text("v${update.version} available", color = Color(0xFF00E5FF)) },
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
