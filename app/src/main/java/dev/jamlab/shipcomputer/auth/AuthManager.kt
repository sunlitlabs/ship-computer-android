package dev.jamlab.shipcomputer.auth

import android.content.Context
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AuthManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ship_computer_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    val savedEmail: String? get() = prefs.getString(KEY_EMAIL, null)
    val savedPassword: String? get() = prefs.getString(KEY_PASSWORD, null)
    val isLoggedIn: Boolean get() = savedEmail != null && savedPassword != null

    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val loginPageReq = Request.Builder()
                .url("$BASE_URL/login")
                .get()
                .build()

            val csrfToken = client.newCall(loginPageReq).execute().use { resp ->
                val body = resp.body?.string()
                    ?: return@withContext Result.failure(Exception("Failed to load login page"))
                extractCsrfToken(body)
                    ?: return@withContext Result.failure(Exception("Could not extract CSRF token"))
            }

            val formBody = FormBody.Builder()
                .add("_token", csrfToken)
                .add("email", email)
                .add("password", password)
                .add("remember", "1")
                .build()

            val loginReq = Request.Builder()
                .url("$BASE_URL/login")
                .post(formBody)
                .header("Referer", "$BASE_URL/login")
                .build()

            client.newCall(loginReq).execute().use { resp ->
                val location = resp.header("Location") ?: ""
                if (resp.code !in 301..302 || location.contains("login")) {
                    return@withContext Result.failure(Exception("Invalid credentials"))
                }
                resp.headers("Set-Cookie").forEach { cookie ->
                    CookieManager.getInstance().setCookie(BASE_URL, cookie)
                }
                CookieManager.getInstance().flush()
            }

            prefs.edit()
                .putString(KEY_EMAIL, email)
                .putString(KEY_PASSWORD, password)
                .apply()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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

    private fun extractCsrfToken(html: String): String? {
        val pattern1 = Regex("""<meta[^>]+name=["']csrf-token["'][^>]+content=["']([^"']+)["']""")
        pattern1.find(html)?.let { return it.groupValues[1] }
        val pattern2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+name=["']csrf-token["']""")
        return pattern2.find(html)?.groupValues?.get(1)
    }

    companion object {
        const val BASE_URL = "https://computer.jamlab.dev"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
    }
}
