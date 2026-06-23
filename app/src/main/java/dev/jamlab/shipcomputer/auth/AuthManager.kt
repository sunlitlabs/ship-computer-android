package dev.jamlab.shipcomputer.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AuthManager(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        "ship_computer_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val savedEmail: String? get() = prefs.getString(KEY_EMAIL, null)
    val savedPassword: String? get() = prefs.getString(KEY_PASSWORD, null)
    val isLoggedIn: Boolean get() = savedEmail != null && savedPassword != null

    /**
     * Authenticates by loading the real login page in a hidden WebView, filling
     * the Livewire form via JS, and detecting the post-login redirect.
     * Uses the system CookieManager, so the session cookie is automatically
     * available to the main WebView when this returns.
     */
    suspend fun login(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(appContext)
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                var loginSubmitted = false
                var settled = false
                val timeoutHandler = Handler(Looper.getMainLooper())

                fun settle(result: Result<Unit>) {
                    if (settled) return
                    settled = true
                    timeoutHandler.removeCallbacksAndMessages(null)
                    CookieManager.getInstance().flush()
                    webView.stopLoading()
                    webView.destroy()
                    if (result.isSuccess) {
                        prefs.edit()
                            .putString(KEY_EMAIL, email)
                            .putString(KEY_PASSWORD, password)
                            .apply()
                    }
                    continuation.resume(result)
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        when {
                            // Navigated away from /login — auth succeeded
                            loginSubmitted && !url.contains("/login") -> {
                                settle(Result.success(Unit))
                            }
                            // Login page loaded — fill and submit the Livewire form
                            !loginSubmitted && url.contains("/login") -> {
                                loginSubmitted = true
                                fillAndSubmit(view, email, password)
                                // Livewire auth is async; give it time to redirect on success.
                                // If it hasn't navigated away after 10s the credentials are wrong.
                                timeoutHandler.postDelayed({
                                    settle(Result.failure(Exception("Invalid email or password")))
                                }, 10_000)
                            }
                            // Redirected back to /login after submitting — wrong credentials
                            loginSubmitted && url.contains("/login") -> {
                                settle(Result.failure(Exception("Invalid email or password")))
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        if (request.isForMainFrame) {
                            settle(Result.failure(Exception("Cannot connect to computer.jamlab.dev")))
                        }
                    }
                }

                webView.loadUrl("$BASE_URL/login")

                continuation.invokeOnCancellation {
                    settled = true
                    timeoutHandler.removeCallbacksAndMessages(null)
                    webView.destroy()
                }
            }
        }

    suspend fun reAuthenticate(): Result<Unit> {
        val email = savedEmail ?: return Result.failure(Exception("No saved credentials"))
        val password = savedPassword ?: return Result.failure(Exception("No saved credentials"))
        return login(email, password)
    }

    fun logout() {
        prefs.edit().clear().apply()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    companion object {
        const val BASE_URL = "https://computer.jamlab.dev"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"

        private fun fillAndSubmit(view: WebView, email: String, password: String) {
            // Escape for JS string literal
            val e = email.replace("\\", "\\\\").replace("'", "\\'")
            val p = password.replace("\\", "\\\\").replace("'", "\\'")
            view.evaluateJavascript("""
                (function() {
                    var emailEl = document.getElementById('email');
                    var passEl  = document.getElementById('password');
                    if (!emailEl || !passEl) return;
                    // Use native setter so Alpine/Livewire wire:model picks up the change
                    var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                    setter.call(emailEl, '$e');
                    emailEl.dispatchEvent(new Event('input', { bubbles: true }));
                    setter.call(passEl, '$p');
                    passEl.dispatchEvent(new Event('input', { bubbles: true }));
                    // Short delay so Livewire has time to sync state before submit
                    setTimeout(function() {
                        var btn = document.querySelector('button[type="submit"]');
                        if (btn) btn.click();
                    }, 400);
                })();
            """.trimIndent(), null)
        }
    }
}
