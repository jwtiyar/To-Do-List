package io.github.jwtiyar.simplertask.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.utils.LocaleManager

class LanguageSelectionDialog(private val activity: Activity, private val onLanguageSelected: () -> Unit) {
    private val localeManager = LocaleManager(activity)
    private var currentLanguage: String = localeManager.getLanguage()
    private var selectedLanguage: String = currentLanguage

    fun show() {
        val languages = arrayOf(
            activity.getString(R.string.language_english) to "en",
            activity.getString(R.string.language_arabic) to "ar",
            activity.getString(R.string.language_chinese_simplified) to "zh",
            activity.getString(R.string.language_chinese_traditional) to "zh-rTW",
            activity.getString(R.string.language_dutch) to "nl",
            activity.getString(R.string.language_french) to "fr",
            activity.getString(R.string.language_german) to "de",
            activity.getString(R.string.language_hindi) to "hi",
            activity.getString(R.string.language_italian) to "it",
            activity.getString(R.string.language_japanese) to "ja",
            activity.getString(R.string.language_korean) to "ko",
            activity.getString(R.string.language_kurdish) to "ku",
            activity.getString(R.string.language_portuguese) to "pt",
            activity.getString(R.string.language_russian) to "ru",
            activity.getString(R.string.language_spanish) to "es",
            activity.getString(R.string.language_turkish) to "tr",
            activity.getString(R.string.language_ukrainian) to "uk"
        )

        val languageNames = languages.map { it.first }.toTypedArray()
        val languageCodes = languages.map { it.second }
        
        val checkedItem = languageCodes.indexOf(currentLanguage).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(activity)
            .setTitle(R.string.select_language)
            .setSingleChoiceItems(languageNames, checkedItem) { dialog, which ->
                selectedLanguage = languageCodes[which]
            }
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                if (selectedLanguage != currentLanguage) {
                    localeManager.setNewLocale(selectedLanguage)
                    // Restart the entire app to properly apply language changes
                    restartApp()
                    onLanguageSelected()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun restartApp() {
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
        activity.finish()
    }
}
