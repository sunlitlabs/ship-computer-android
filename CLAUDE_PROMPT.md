# Prompt: Build Ship Computer Android App

Use this prompt verbatim with a fresh Claude instance to build the app.
The INSTRUCTIONS.md in this folder is the reference doc — attach it (or paste its contents)
alongside this prompt so Claude has full context.

---

## Prompt

Build a complete, production-ready Android app called **Ship Computer** in Kotlin using
Jetpack Compose. The full specification is in the attached INSTRUCTIONS.md. Read it
carefully before writing any code.

### What to build

A WebView-hybrid Android app that:
1. Shows a native login screen, authenticates the user against a Laravel backend at
   `https://computer.jamlab.dev`, and stores the session cookie
2. Loads the voice interface at `https://computer.jamlab.dev/live` in a full-screen WebView
3. Bridges native Android Bluetooth AVRCP media button events (from a Bluetooth combadge)
   into the WebView as `KeyboardEvent('keydown', { key: 'MediaPlayPause' })` calls, which
   the existing web app already handles
4. Runs a foreground service to keep audio alive when backgrounded, with a persistent
   notification showing session status and a Mute toggle
5. Has a Settings bottom sheet (opened via a floating gear icon) showing the logged-in
   user's email, a Sign Out button, version info, and a **Check for Updates** button
6. Implements self-update: the Check for Updates button calls the GitHub Releases API at
   `https://api.github.com/repos/sunlitlabs/ship-computer-android/releases/latest`,
   compares the `tag_name` with `BuildConfig.VERSION_NAME`, and if newer downloads the APK
   from the release asset URL and installs it via FileProvider + ACTION_VIEW intent

### Deliver

A complete, compilable Android project with the following files at minimum:

```
app/
  src/main/
    java/dev/jamlab/shipcomputer/
      MainActivity.kt
      ui/
        LoginScreen.kt
        MainScreen.kt         (WebView host + floating gear icon)
        SettingsSheet.kt
      service/
        AudioForegroundService.kt
      bluetooth/
        BadgeButtonManager.kt  (MediaSessionCompat setup + JS bridge)
      update/
        UpdateChecker.kt       (GitHub Releases API check + download + install)
      auth/
        AuthManager.kt         (OkHttp login, cookie sync, EncryptedSharedPreferences)
      ShipComputerApp.kt       (Application class)
    res/
      values/strings.xml
      values/colors.xml
      drawable/ic_notification.xml   (simple vector icon)
    AndroidManifest.xml
  build.gradle.kts             (app-level, with all dependencies)
build.gradle.kts               (project-level)
settings.gradle.kts
gradle/libs.versions.toml      (version catalog)
.gitignore                     (include keystore.properties, *.jks, *.keystore)
```

### Key implementation requirements

**Login (AuthManager.kt)**
- Use OkHttp (not Retrofit) for the two HTTP calls: GET /login (extract CSRF from
  `<meta name="csrf-token" content="...">`) then POST /login
- After successful POST, call `CookieManager.getInstance().flush()`
- Store email + password in `EncryptedSharedPreferences` (androidx.security:security-crypto)
  so the app can silently re-authenticate when the session expires
- On app start (SplashScreen / init), try to load `/live` directly; if the WebView
  redirects to `/login`, trigger silent re-auth with stored credentials

**WebView (MainScreen.kt)**
- Fill the entire screen — no app bars, no nav bars
- `webSettings.mediaPlaybackRequiresUserGesture = false` — CRITICAL for WebRTC autoplay
- `webSettings.domStorageEnabled = true`
- `webSettings.javaScriptEnabled = true`
- Set `userAgentString = "ShipComputerAndroid/1.0 " + defaultUserAgent`
- `WebChromeClient.onPermissionRequest` → grant all resources unconditionally
- `WebViewClient.shouldOverrideUrlLoading`: if URL contains `/login`, intercept and
  trigger re-auth, then reload `/live` after success
- `android:hardwareAccelerated="true"` on the Activity in AndroidManifest

**Bluetooth badge (BadgeButtonManager.kt)**
- Create `MediaSessionCompat(context, "ShipComputerBadge")`
- Set flags: `FLAG_HANDLES_MEDIA_BUTTONS or FLAG_HANDLES_TRANSPORT_CONTROLS`
- Set to active; set `PlaybackStateCompat` to STATE_PLAYING
- In `onPlay()` and `onPause()` callbacks, call:
  ```kotlin
  webView.post {
    webView.evaluateJavascript(
      "window.dispatchEvent(new KeyboardEvent('keydown',{key:'MediaPlayPause',bubbles:true}))",
      null
    )
  }
  ```
- Keep the `MediaSessionCompat` alive for the duration of the app foreground lifetime
- Release in `onDestroy()`

**Foreground service (AudioForegroundService.kt)**
- Start when the WebView has loaded `/live` successfully
- Show notification with:
  - Title: "Ship Computer"
  - Text: "Voice session active"
  - Action: "End Session" → calls `webView.evaluateJavascript("window.voiceApp?.disconnect()", null)`
- `foregroundServiceType = FOREGROUND_SERVICE_TYPE_MICROPHONE or FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`
- The service holds a reference to the WebView via a singleton or bound service pattern

**Self-update (UpdateChecker.kt)**
- `suspend fun checkForUpdate(): UpdateResult?` (returns null if up-to-date or on error)
- Call `https://api.github.com/repos/sunlitlabs/ship-computer-android/releases/latest`
  with `Accept: application/vnd.github+json`
- Parse JSON: `tag_name` (strip leading "v"), `assets[0].browser_download_url`, `body`
- Compare using semver: split on ".", compare each component as Int
- `suspend fun downloadAndInstall(context, downloadUrl)`:
  1. Download to `context.externalCacheDir/update.apk` using OkHttp streaming
  2. Create FileProvider URI (provider authority: `dev.jamlab.shipcomputer.fileprovider`)
  3. Add `<provider>` to AndroidManifest with `android:grantUriPermissions="true"`
  4. Add `res/xml/file_paths.xml` with `<external-cache-path name="apk" path="." />`
  5. Fire `Intent(Intent.ACTION_VIEW)` with the URI, flag `FLAG_GRANT_READ_URI_PERMISSION`
  6. If ActivityNotFoundException, show a toast: "Enable 'Install unknown apps' for Ship Computer in Settings"

**Settings sheet (SettingsSheet.kt)**
- Bottom sheet modal using `ModalBottomSheet` (Compose Material3)
- Show: email address, server URL (`computer.jamlab.dev`), app version (`BuildConfig.VERSION_NAME`)
- "Check for Updates" button: calls `UpdateChecker.checkForUpdate()` in a coroutine;
  shows a dialog with release notes and a "Download & Install" button; shows "Up to date" snackbar if current
- "Sign Out" button: clears `EncryptedSharedPreferences`, clears WebView cookies
  (`CookieManager.getInstance().removeAllCookies(null)`), navigates to LoginScreen
- Floating gear icon in `MainScreen.kt`: a small `FloatingActionButton` in the
  bottom-right corner, semi-transparent (`alpha = 0.4f`), that opens the SettingsSheet

### Dependencies (gradle/libs.versions.toml)

```toml
[versions]
kotlin = "2.0.21"
agp = "8.7.3"
compose-bom = "2024.12.01"
okhttp = "4.12.0"
security-crypto = "1.1.0-alpha06"
media = "1.7.0"
coroutines = "1.9.0"
kotlinx-serialization = "1.7.3"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-activity = { group = "androidx.activity", name = "activity-compose", version = "1.9.3" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "security-crypto" }
media = { group = "androidx.media", name = "media", version.ref = "media" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
```

### App-level build.gradle.kts details

```kotlin
android {
    namespace = "dev.jamlab.shipcomputer"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.jamlab.shipcomputer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { buildConfig = true; compose = true }
}
```

### AndroidManifest.xml key details

```xml
<application android:hardwareAccelerated="true" ...>
  <activity
    android:name=".MainActivity"
    android:windowSoftInputMode="adjustResize"
    android:exported="true">
    <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
  </activity>
  <service
    android:name=".service.AudioForegroundService"
    android:foregroundServiceType="microphone|mediaPlayback"
    android:exported="false" />
  <provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="dev.jamlab.shipcomputer.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
      android:name="android.support.FILE_PROVIDER_PATHS"
      android:resource="@xml/file_paths" />
  </provider>
</application>
```

### UX notes

- The app should feel invisible — the WebView IS the app. No toolbars. No nav bars.
  The web page provides all UI.
- The floating gear icon should be barely visible (semi-transparent) so it doesn't
  obstruct the voice interface.
- The login screen should match the dark theme of the web app (dark background, cyan
  accent, similar font weight to the existing UI at computer.jamlab.dev/login).
- Show a loading indicator while the WebView is loading `/live` after login.
- If the connection to `computer.jamlab.dev` fails (no internet, server down), show
  an inline error with a Retry button rather than a crash.

### What NOT to build

- Do not reimplement the WebRTC logic — the WebView handles it
- Do not reimplement the voice UI — the web page handles it
- Do not reimplement the OpenAI integration — the web page handles it
- Do not add analytics, crash reporting, or any third-party SDKs beyond what's listed
- Do not add a login-with-Google or OAuth flow — email/password only

### Release workflow — do this after EVERY task

After completing any task (including the initial build), you must:

1. Bump `versionCode` (+1) and `versionName` patch segment in `app/build.gradle.kts`
2. Build the signed release APK:
   ```
   ./gradlew assembleRelease
   ```
3. Commit and push the code (do NOT include the APK in the commit):
   ```
   git add -A && git commit -m "..." && git push
   ```
4. Create a GitHub Release with the APK attached:
   ```
   gh release create v{versionName} app/release/app-release.apk \
     --repo sunlitlabs/ship-computer-android \
     --title "Ship Computer Android v{versionName}" \
     --notes "{what changed}"
   ```

This is mandatory — not optional. The APK in the GitHub Release is how the user installs
and updates the app on their phone. The one-time keystore setup required for signing is
documented in INSTRUCTIONS.md under "One-time prerequisites."

### Deliverable format

Write all files in full — no placeholders, no `// TODO`, no truncated code blocks.
Every file should be complete and compilable. After writing all files, run the release
workflow above to produce the first APK and GitHub Release.
