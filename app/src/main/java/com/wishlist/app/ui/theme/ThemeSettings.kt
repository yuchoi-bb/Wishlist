package com.wishlist.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 밝게 is the opposite of 어둡게; 시스템 설정 follows whatever the phone or tablet is set to. */
enum class ThemeMode(val label: String) {
    SYSTEM("시스템 설정"),
    LIGHT("밝게"),
    DARK("어둡게"),
}

/**
 * Which theme this device uses. Deliberately local rather than synced through Firestore: the same
 * account is used on a tablet kept in dark mode and a phone kept light, so this is a property of
 * the device, not of the person.
 */
class ThemeSettings private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(read())

    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    private fun read(): ThemeMode {
        val stored = prefs.getString(KEY_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    companion object {
        private const val PREFS_NAME = "theme_settings"
        private const val KEY_MODE = "mode"

        @Volatile
        private var instance: ThemeSettings? = null

        /** One instance per process, so the setting screen and the theme see the same state. */
        fun get(context: Context): ThemeSettings =
            instance ?: synchronized(this) {
                instance ?: ThemeSettings(context.applicationContext).also { instance = it }
            }
    }
}
