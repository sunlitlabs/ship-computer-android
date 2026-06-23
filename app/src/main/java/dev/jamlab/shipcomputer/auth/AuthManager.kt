package dev.jamlab.shipcomputer.auth

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
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

                fun isLoginUrl(url: String) = url.contains("/login")

                webView.webViewClient = object : WebViewClient() {

                    // Catches Livewire SPA navigation (history.pushState / wire:navigate)
                    // which does NOT trigger onPageStarted or onPageFinished
                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (loginSubmitted && !isLoginUrl(url)) {
                            settle(Result.success(Unit))
                        }
                    }

                    // Catches full-page navigations at the earliest point
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        if (loginSubmitted && !isLoginUrl(url)) {
                            settle(Result.success(Unit))
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        when {
                            !loginSubmitted && isLoginUrl(url) -> {
                                loginSubmitted = true
                                fillAndSubmit(view, email, password)
                                // Fallback: if nothing navigates within 10s, credentials are wrong
                                timeoutHandler.postDelayed({
                                    settle(Result.failure(Exception("Invalid email or password")))
                                }, 10_000)
                            }
                            loginSubmitted && !isLoginUrl(url) -> settle(Result.success(Unit))
                            loginSubmitted && isLoginUrl(url) -> {
                                // Full-page redirect back to /login = wrong credentials
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
            // base64-encode credentials so no JS string escaping is needed at all
            val e64 = Base64.encodeToString(email.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val p64 = Base64.encodeToString(password.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            view.evaluateJavascript("""
                (function() {
                    var e = atob('$e64');
                    var p = atob('$p64');
                    var emailEl = document.getElementById('email');
                    var passEl  = document.getElementById('password');
                    if (!emailEl || !passEl) return;
                    // Native setter is required so Alpine/Livewire wire:model detects the change
                    var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                    setter.call(emailEl, e);
                    emailEl.dispatchEvent(new Event('input', { bubbles: true }));
                    setter.call(passEl, p);
                    passEl.dispatchEvent(new Event('input', { bubbles: true }));
                    setTimeout(function() {
                        var btn = document.querySelector('button[type="submit"]');
                        if (btn) btn.click();
                    }, 500);
                })();
            """.trimIndent(), null)
        }
    }
}
