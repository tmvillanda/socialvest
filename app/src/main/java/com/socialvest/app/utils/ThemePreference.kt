package com.socialvest.app.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.socialvest.app.R

/**
 * User-selectable appearance. Persisted locally because it is a device
 * preference, not account data that belongs in Firestore.
 */
enum class AppTheme(val storageValue: String, @StringRes val labelRes: Int) {
    SYSTEM("system", R.string.drawer_theme_system),
    LIGHT("light", R.string.drawer_theme_light),
    DARK("dark", R.string.drawer_theme_dark);

    val nightMode: Int
        get() = when (this) {
            SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }

    companion object {
        fun fromStorage(value: String?): AppTheme =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

object ThemePreference {

    private const val PREFS_NAME = "socialvest_preferences"
    private const val KEY_THEME = "app_theme"

    fun current(context: Context): AppTheme {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppTheme.fromStorage(prefs.getString(KEY_THEME, null))
    }

    fun save(context: Context, theme: AppTheme) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.storageValue)
            .apply()
    }

    /** Applies the stored theme. Called from [com.socialvest.app.SocialvestApp]. */
    fun applySaved(context: Context) {
        AppCompatDelegate.setDefaultNightMode(current(context).nightMode)
    }

    /** Applies a theme immediately; AppCompat recreates the current activity. */
    fun apply(theme: AppTheme) {
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
    }
}
