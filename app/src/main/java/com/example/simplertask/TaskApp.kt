package com.example.simplertask

import android.app.Application
import android.content.Context
import com.example.simplertask.utils.LocaleManager

class TaskApp : Application() {
    private lateinit var localeManager: LocaleManager

    override fun attachBaseContext(base: Context) {
        localeManager = LocaleManager(base)
        super.attachBaseContext(localeManager.setAppLocale())
    }

    override fun onCreate() {
        super.onCreate()
        // Ensure the locale is set when the app starts
        localeManager.setAppLocale()
    }
}
