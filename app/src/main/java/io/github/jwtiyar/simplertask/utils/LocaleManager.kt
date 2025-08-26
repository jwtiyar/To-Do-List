package io.github.jwtiyar.simplertask.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
// import android.preference.PreferenceManager
import java.util.*

class LocaleManager(private val context: Context) {
    // Language selection removed. Always use system language.
    fun setAppLocale(): Context {
        return context
    }

    companion object {
        fun getLocale(resources: Resources): Locale {
            val config = resources.configuration
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.locales[0]
            } else {
                config.locale
            }
        }
    }
}
