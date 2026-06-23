package com.wwwaker.warer_android

import android.annotation.SuppressLint
import android.app.Application
import android.webkit.WebView
import com.wwwaker.warer_android.data.db.AppDatabase
import com.wwwaker.warer_android.data.settings.SettingsManager

class WarerApplication : Application() {

    lateinit var settingsManager: SettingsManager
        private set
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsManager = SettingsManager(this)
        database = AppDatabase.getInstance(this)
        warmUpWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun warmUpWebView() {
        try {
            WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl("about:blank")
            }
        } catch (_: Exception) {
            // WebView pre-warm is best-effort
        }
    }

    companion object {
        @Volatile
        private var instance: WarerApplication? = null

        fun getInstance(): WarerApplication {
            return instance ?: throw IllegalStateException("WarerApplication not initialized")
        }
    }
}
