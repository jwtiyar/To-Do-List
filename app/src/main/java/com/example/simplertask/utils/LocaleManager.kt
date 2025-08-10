package com.example.simplertask.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
// import android.preference.PreferenceManager
import java.util.*

class LocaleManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("simplertask_prefs", Context.MODE_PRIVATE)
    private val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"

    fun setAppLocale(): Context {
        val language = getLanguage()
        return setNewLocale(language)
    }
    
    fun setNewLocale(language: String): Context {
        persistLanguage(language)
        return updateResources(language)
    }

    fun getLanguage(): String {
        return prefs.getString(SELECTED_LANGUAGE, Locale.getDefault().language) ?: "en"
    }

    private fun persistLanguage(language: String) {
        prefs.edit().putString(SELECTED_LANGUAGE, language).apply()
    }

    private fun updateResources(language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return context.createConfigurationContext(config)
        } else {
            config.locale = locale
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
            return context
        }
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
