package com.ozzybozy.qrhub

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate

object ThemeUtils {
    const val PREFS_NAME = "theme_prefs"
    const val KEY_THEME = "theme_preference"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    fun applyTheme(theme: String) {
        when (theme) {
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    fun applyThemeChangeAndRecreate(activity: Activity) {
        activity.recreate()
    }
}