package dev.jamlab.shipcomputer.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
     * Authenticates using a hidden WebView that loads the real Livewire login page,
     * fills the form via JS, then polls window.location via a JS interface to detect
     * the redirect that follows successful authentication.
     *
     * Pass an Activity context so the WebView initialises properly.
     */
    suspend fun login(activityContext: Context, email: String, password: String): Result<Unit> =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(activityContext)
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                var loginSubmitted = false
                var settled = false
                val mainHandler = Handler(Looper.getMainLooper())

                // Always call resume() first; clean up WebView on the next loop tick.
                fun settle(result: Result<Unit>) {
                    if (settled) return
                    settled = true
                    mainHandler.removeCallbacksAndMessages(null)
                    CookieManager.getInstance().flush()
                    if (result.isSuccess) {
                        prefs.edit()
                            .putString(KEY_EMAIL, email)
                            .putString(KEY_PASSWORD, password)
                            .apply()
                    }
                    continuation.resume(result)
                    // Defer WebView cleanup to avoid destroy() inside a WebView callback
                    mainHandler.post {
                        runCatching { webView.stopLoading() }
                        runCatching { webView.destroy() }
                    }
                }

                // JS → Android bridge: called by the polling loop injected into the page
                webView.addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onLoginSuccess() = mainHandler.post { settle(Result.success(Unit)) }

                        @JavascriptInterface
                        fun onLoginFailure(reason: String) = mainHandler.post {
                            settle(Result.failure(Exception(reason)))
                        }
                    },
                    "ShipAuth"
                )

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        when {
                            !loginSubmitted && url.contains("/login") -> {
                                loginSubmitted = true
                                fillSubmitAndPoll(view, email, password)
                                // Hard timeout: if the page never navigates away, credentials wrong
                                mainHandler.postDelayed({
                                    settle(Result.failure(Exception("Invalid email or password")))
                                }, 12_000)
                            }
                            // Full-page redirect away from /login — auth succeeded
                            loginSubmitted && !url.contains("/login") -> settle(Result.success(Unit))
                            // Full-page redirect BACK to /login — wrong credentials
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
                    mainHandler.removeCallbacksAndMessages(null)
                    runCatching { webView.destroy() }
                }
            }
        }

    suspend fun reAuthenticate(activityContext: Context): Result<Unit> {
        val email = savedEmail ?: return Result.failure(Exception("No saved credentials"))
        val password = savedPassword ?: return Result.failure(Exception("No saved credentials"))
        return login(activityContext, email, password)
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

        private fun fillSubmitAndPoll(view: WebView, email: String, password: String) {
            val e64 = Base64.encodeToString(email.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val p64 = Base64.encodeToString(password.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            view.evaluateJavascript("""
                (function() {
                    var e = atob('$e64');
                    var p = atob('$p64');
                    var emailEl = document.getElementById('email');
                    var passEl  = document.getElementById('password');
                    if (!emailEl || !passEl) {
                        ShipAuth.onLoginFailure('Login form not found on page');
                        return;
                    }
                    var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                    setter.call(emailEl, e);
                    emailEl.dispatchEvent(new Event('input', { bubbles: true }));
                    setter.call(passEl, p);
                    passEl.dispatchEvent(new Event('input', { bubbles: true }));

                    // Poll window.location — works for both SPA (pushState) and full-page redirects
                    var polls = 0;
                    var poll = setInterval(function() {
                        polls++;
                        if (window.location.pathname.indexOf('/login') === -1) {
                            clearInterval(poll);
                            ShipAuth.onLoginSuccess();
                        } else if (polls > 24) {
                            // 24 × 500ms = 12s — matches the Android timeout
                            clearInterval(poll);
                        }
                    }, 500);

                    setTimeout(function() {
                        var btn = document.querySelector('button[type="submit"]');
                        if (btn) btn.click();
                    }, 500);
                })();
            """.trimIndent(), null)
        }
    }
}
