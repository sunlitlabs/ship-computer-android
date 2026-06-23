# Ship Computer — Android App

## What This Is

A native Android companion app for the Ship Computer AI voice assistant backend at
`https://computer.jamlab.dev`. Provides the same experience as the web client — push-to-talk
AI conversation using a Bluetooth combadge button — with native Android integration for
background audio, system-level Bluetooth handling, and self-update from GitHub Releases.

---

## Backend

| Detail | Value |
|---|---|
| Production URL | `https://computer.jamlab.dev` |
| Tech | Laravel 13 + Livewire + SQLite |
| Auth | Standard Laravel session auth (email + password) |
| GitHub | `https://github.com/sunlitlabs/ship-computer` |

### Routes the app uses

| Route | Auth | Purpose |
|---|---|---|
| `GET /login` | — | Login page (extract CSRF token) |
| `POST /login` | — | Authenticate; sets session cookie |
| `POST /logout` | session | End session |
| `GET /live` | session | Main voice interface (loaded in WebView) |
| `POST /api/session` | session | Get ephemeral OpenAI Realtime token |
| `GET /api/config` | — | Public config (VAD settings, voice, etc.) |
| `GET /voice/access` | — | Access-code gate (if configured) |

**Note:** If an access code is configured on the server, a logged-in admin/user session
bypasses it automatically. The app should always use session-based auth, not access codes.

---

## Architecture — WebView Hybrid

The app loads the existing web voice interface in an Android WebView, with native layers for:

1. **Login** — native login screen handles auth, stores the session cookie in the WebView
2. **Bluetooth badge button** — native `MediaSession` → JavaScript bridge into WebView
3. **Background audio** — foreground `Service` keeps WebView alive when backgrounded
4. **Self-update** — GitHub Releases API + APK download + install

This approach reuses all existing web-app logic (WebRTC, OpenAI Realtime connection, chirp
sounds, VAD, session management, UI) without reimplementing it natively. When the web app
is updated, the Android app automatically gets those changes without a new APK release.

---

## Authentication Flow

```
1. App shows native Login screen (email + password fields)
2. GET https://computer.jamlab.dev/login
   → Extract CSRF token from <meta name="csrf-token"> or the X-CSRF-TOKEN cookie
3. POST https://computer.jamlab.dev/login
   Body: _token=<csrf>, email=<email>, password=<password>, remember=1
4. On 302 redirect to /admin or / — auth succeeded
   The response sets a laravel_session cookie
5. Sync all cookies into the Android WebView's CookieManager (setAcceptCookie = true)
6. Load https://computer.jamlab.dev/live in the WebView
   The session cookie grants access; no redirect to /voice/access
```

Store credentials securely in `EncryptedSharedPreferences` (from Jetpack Security) so the
app can re-authenticate automatically on session expiry without prompting the user again.

---

## Bluetooth Badge Button

The TNG-style combadge sends AVRCP play/pause events when its button is pressed — the same
signal as a Bluetooth headset's play/pause button.

### Android handling

```
1. Create a MediaSessionCompat with FLAG_HANDLES_MEDIA_BUTTONS
2. Register the session; set it to ACTIVE state
3. Set a MediaSessionCompat.Callback that handles onPlay() and onPause()
4. In both callbacks, bridge the event into the WebView:
   webView.evaluateJavascript(
     "window.dispatchEvent(new KeyboardEvent('keydown', { key: 'MediaPlayPause', bubbles: true }))",
     null
   )
5. The existing web app keydown handler catches 'MediaPlayPause' and calls toggleListening()
```

Keep the MediaSession active with a persistent foreground notification for the duration of a
session. Without an active MediaSession, Android may route badge button presses elsewhere.

The web app handles its own A2DP keep-alive (a silent 20 Hz tone routed to the headset) and
SMTC/AVRCP compliance internally via the Web MediaSession API. The native Android MediaSession
is separate and handles the OS-level button routing.

---

## Foreground Service

To keep audio running when the user backgrounds the app:

```
- AudioForegroundService extends Service
- Started when the user connects a voice session (Go Live)
- Shows a persistent notification: "Ship Computer — Session Active"
  Notification actions: Mute / End Session
- Stopped when the session disconnects
- Manifest: android:foregroundServiceType="microphone|mediaPlayback"
```

The notification's Mute action should inject the toggle event into the WebView the same way
the badge button does.

---

## WebView Configuration

```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    mediaPlaybackRequiresUserGesture = false  // critical — allows WebRTC autoplay
    allowFileAccess = false
    userAgentString = "ShipComputerAndroid/1.0 " + userAgentString
}
webView.webChromeClient = object : WebChromeClient() {
    // Grant mic/camera permissions to the web app without prompting
    override fun onPermissionRequest(request: PermissionRequest) {
        request.grant(request.resources)
    }
}
// Hardware acceleration is required for WebRTC — set in AndroidManifest:
// android:hardwareAccelerated="true" on the Activity
```

---

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

Request `RECORD_AUDIO` at runtime (Android 6+). The WebView's `onPermissionRequest` will
also fire for the web-side mic request — grant it unconditionally since the user already
granted at the OS level.

---

## Self-Update Mechanism

The app checks GitHub Releases for a newer APK:

```
API: GET https://api.github.com/repos/sunlitlabs/ship-computer-android/releases/latest
     Header: Accept: application/vnd.github+json

Response fields used:
  tag_name      — e.g. "v1.2.0"
  assets[0].browser_download_url — direct APK download URL
  body          — release notes (show in update dialog)
```

### Version comparison
- `BuildConfig.VERSION_NAME` (e.g. `"1.1.0"`) is set in `app/build.gradle`
- Strip the leading `v` from `tag_name` before comparing
- Use semantic version comparison (not string comparison)

### Install flow
1. Download APK to `getExternalCacheDir()/update.apk` (or `DownloadManager`)
2. Use `FileProvider` to create a content URI for the APK file
3. Fire `Intent(Intent.ACTION_VIEW)` with the content URI and `application/vnd.android.package-archive`
4. Android prompts the user to install (they need "Install unknown apps" enabled for this app)

The Settings screen should guide the user to enable "Install unknown apps" if they haven't.

### When to check
- On app startup (silent check, notify if update available)
- On manual "Check for Updates" button in Settings

---

## Release Process — Claude Does This Automatically

After completing **every task** that changes app behavior or fixes a bug, Claude must:

1. **Bump the version** in `app/build.gradle.kts`:
   - Increment `versionCode` by 1 (integer, monotonically increasing)
   - Increment `versionName` patch segment (e.g. `1.0.0` → `1.0.1`); increment minor for new features
2. **Build the signed release APK**:
   ```bash
   cd C:\Users\xtrao\Development\Android\ship-computer
   ./gradlew assembleRelease
   # Output: app/release/app-release.apk
   ```
3. **Commit and push** code + version bump (do NOT commit the APK itself):
   ```bash
   git add -A
   git commit -m "..."
   git push
   ```
4. **Create a GitHub Release** with the APK attached:
   ```bash
   gh release create v{versionName} app/release/app-release.apk \
     --repo sunlitlabs/ship-computer-android \
     --title "Ship Computer Android v{versionName}" \
     --notes "{brief description of what changed}"
   ```

The app's self-update checker polls `api.github.com/repos/sunlitlabs/ship-computer-android/releases/latest`
and will surface this release to users automatically.

**Do not commit APK files to the repo** — Releases only. APKs are large binaries that
don't diff well; Releases keep the repo clean.

### One-time prerequisites (set up manually before first Claude session)

These must be in place before Claude can run the release workflow:

**1. GitHub repo**
Create `https://github.com/sunlitlabs/ship-computer-android` and push the initial project.
Authenticate `gh` CLI: `gh auth login`.

**2. Signing keystore**
```bash
keytool -genkey -v \
  -keystore C:\Users\xtrao\Development\Android\ship-computer\ship-computer.jks \
  -alias ship-computer \
  -keyalg RSA -keysize 2048 -validity 10000
```
Create `keystore.properties` in the project root (never commit this file):
```properties
storeFile=C:/Users/xtrao/Development/Android/ship-computer/ship-computer.jks
storePassword=<your password>
keyAlias=ship-computer
keyPassword=<your password>
```
Add to `.gitignore`:
```
keystore.properties
*.jks
*.keystore
```
Wire `keystore.properties` into `app/build.gradle.kts`:
```kotlin
val keystoreProps = java.util.Properties().also { props ->
    val f = rootProject.file("keystore.properties")
    if (f.exists()) props.load(f.inputStream())
}
android {
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps["storeFile"] as String)
            storePassword = keystoreProps["storePassword"] as String
            keyAlias = keystoreProps["keyAlias"] as String
            keyPassword = keystoreProps["keyPassword"] as String
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

**3. Android SDK / Gradle**
Android Studio must be installed and the SDK path set in `local.properties`:
```properties
sdk.dir=C\:\\Users\\xtrao\\AppData\\Local\\Android\\Sdk
```
(Android Studio writes this automatically when you open the project.)

---

## Screen / Navigation Structure

```
SplashScreen (auto-login attempt)
  ├── LoginScreen       — email/password, "Sign In" button
  └── MainScreen        — hosts the WebView for /live
        └── SettingsSheet  — overlay/bottom sheet:
              ├── Logged-in-as: <email>   Sign Out button
              ├── Server URL (read-only display)
              ├── Check for Updates button
              └── Version number
```

The app should have no nav bar or chrome of its own — the WebView fills the screen. A
discreet floating gear icon (or swipe gesture) opens the Settings sheet without disrupting
the voice session.

---

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| WebView | `android.webkit.WebView` (standard) |
| HTTP (auth) | OkHttp 4.x |
| JSON | kotlinx.serialization or Gson |
| Encrypted prefs | `androidx.security:security-crypto` |
| Background service | `android.app.Service` (foreground) |
| Media button | `androidx.media:media` (`MediaSessionCompat`) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |

---

## Known Constraints and Gotchas

- `mediaPlaybackRequiresUserGesture = false` is essential — without it the WebView
  won't autoplay the keep-alive audio and WebRTC will fail to start.
- Hardware acceleration (`android:hardwareAccelerated="true"`) is required on the Activity
  for WebRTC video/audio to work in WebView.
- On Android 12+, `BLUETOOTH_CONNECT` is a runtime permission — request it before
  trying to handle badge button presses.
- The WebView's `CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)`
  may be needed for the session cookie to persist correctly.
- After calling `POST /login`, sync cookies to the WebView:
  `CookieManager.getInstance().flush()`
- Session expiry: the Laravel session lasts ~2 hours by default. If the WebView gets
  a redirect to /login, catch it in `WebViewClient.shouldOverrideUrlLoading` and
  trigger re-auth using the stored credentials from EncryptedSharedPreferences.
- The `/live` page uses Alpine.js and Livewire — these require JavaScript to be enabled
  and domStorage to be true.
