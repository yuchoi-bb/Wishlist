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

/** The two shapes the main screen can take. Both show the same items and obey the same sort. */
enum class ViewMode(val label: String, val description: String) {
    TABLE("표", "모든 열을 한 눈에. 가로로 넘겨 봅니다."),
    LIST("목록", "항목 아래 세부항목. 가로 스크롤이 없습니다."),
}

/**
 * What this device shows and how. Deliberately local rather than synced through Firestore: the same
 * account is used on a tablet kept in dark mode with the wide table and a phone kept light on the
 * list, so these are properties of the device, not of the person.
 */
class DeviceSettings private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(readTheme())
    private val _viewMode = MutableStateFlow(readView())

    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setViewMode(mode: ViewMode) {
        prefs.edit().putString(KEY_VIEW, mode.name).apply()
        _viewMode.value = mode
    }

    private fun readTheme(): ThemeMode {
        val stored = prefs.getString(KEY_THEME, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private fun readView(): ViewMode {
        val stored = prefs.getString(KEY_VIEW, null) ?: return ViewMode.TABLE
        return runCatching { ViewMode.valueOf(stored) }.getOrDefault(ViewMode.TABLE)
    }

    companion object {
        // Unchanged from when this held the theme alone, so nobody's choice resets on upgrade.
        private const val PREFS_NAME = "theme_settings"
        private const val KEY_THEME = "mode"
        private const val KEY_VIEW = "view_mode"

        @Volatile
        private var instance: DeviceSettings? = null

        /** One instance per process, so the settings screen and the UI see the same state. */
        fun get(context: Context): DeviceSettings =
            instance ?: synchronized(this) {
                instance ?: DeviceSettings(context.applicationContext).also { instance = it }
            }
    }
}
