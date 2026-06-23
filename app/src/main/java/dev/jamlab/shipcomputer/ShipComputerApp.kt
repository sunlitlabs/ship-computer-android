package dev.jamlab.shipcomputer

import android.app.Application
import android.webkit.WebView

class ShipComputerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
