package dev.jamlab.shipcomputer.auth

import android.content.Context
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

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

    // Stores all cookies received during the login request so we can
    // inject the session cookie into the WebView's CookieManager.
    private val cookieStore = mutableListOf<Cookie>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                cookieStore.removeAll { e -> cookies.any { it.name == e.name } }
                cookieStore.addAll(cookies)
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            synchronized(cookieStore) { cookieStore.toList() }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .build()

    val savedEmail: String? get() = prefs.getString(KEY_EMAIL, null)
    val savedPassword: String? get() = prefs.getString(KEY_PASSWORD, null)
    val isLoggedIn: Boolean get() = savedEmail != null && savedPassword != null

    /**
     * Authenticates via POST /auth/mobile — a CSRF-exempt endpoint that
     * validates credentials, starts a Laravel session, and returns a session
     * cookie in the response. The cookie is injected into the system
     * CookieManager so the main WebView can load /live without further auth.
     */
    suspend fun login(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("email", email)
                    .add("password", password)
                    .build()

                val request = Request.Builder()
                    .url("$BASE_URL/auth/mobile")
                    .post(body)
                    .header("Accept", "application/json")
                    .build()

                val code = client.newCall(request).execute().use { it.code }

                when (code) {
                    200 -> {
                        // Inject the session cookie the server set into the WebView
                        withContext(Dispatchers.Main) {
                            synchronized(cookieStore) {
                                cookieStore.forEach { cookie ->
                                    CookieManager.getInstance().setCookie(
                                        BASE_URL,
                                        "${cookie.name}=${cookie.value}; path=/"
                                    )
                                }
                            }
                            CookieManager.getInstance().flush()
                        }
                        prefs.edit()
                            .putString(KEY_EMAIL, email)
                            .putString(KEY_PASSWORD, password)
                            .apply()
                        Result.success(Unit)
                    }
                    401 -> Result.failure(Exception("Invalid email or password"))
                    429 -> Result.failure(Exception("Too many attempts — try again later"))
                    else -> Result.failure(Exception("Server error ($code)"))
                }
            } catch (e: IOException) {
                Result.failure(Exception("Cannot connect to computer.jamlab.dev"))
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
    }
}
