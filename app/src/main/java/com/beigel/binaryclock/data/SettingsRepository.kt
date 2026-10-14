package com.beigel.binaryclock.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.beigel.binaryclock.clock.ClockSettings
import com.beigel.binaryclock.clock.ClockStyle
import com.beigel.binaryclock.clock.Placement
import com.beigel.binaryclock.clock.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Einstellungen in SharedPreferences. Wallpaper-Service, Widget und App laufen im selben
 * Prozess und teilen sich diese eine Instanz über den [com.beigel.binaryclock.AppContainer].
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ClockSettings> = _settings.asStateFlow()

    // Als Feld halten, sonst räumt der GC den Listener weg.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun update(transform: (ClockSettings) -> ClockSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        prefs.edit {
            putString(KEY_STYLE, next.style.name)
            if (next.accentOverride != null) putInt(KEY_ACCENT, next.accentOverride) else remove(KEY_ACCENT)
            putBoolean(KEY_SECONDS, next.showSeconds)
            putBoolean(KEY_HINTS, next.showHints)
            putBoolean(KEY_DATE, next.showDate)
            putString(KEY_THEME, next.themeMode.name)
            putBoolean(KEY_LOCK_ADJUST, next.lockScreenAdjust)
            putPlacement(this, PREFIX_HOME, next.home)
            putPlacement(this, PREFIX_LOCK, next.lock)
        }
    }

    private fun putPlacement(editor: SharedPreferences.Editor, prefix: String, p: Placement) {
        editor.putFloat(prefix + "x", p.x)
        editor.putFloat(prefix + "y", p.y)
        editor.putFloat(prefix + "size", p.size)
    }

    private fun readPlacement(prefix: String, default: Placement) = Placement(
        x = prefs.getFloat(prefix + "x", default.x),
        y = prefs.getFloat(prefix + "y", default.y),
        size = prefs.getFloat(prefix + "size", default.size),
    ).clamped()

    private inline fun <reified E : Enum<E>> readEnum(key: String, default: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun read(): ClockSettings {
        val d = ClockSettings()
        return ClockSettings(
            style = readEnum(KEY_STYLE, d.style),
            accentOverride = if (prefs.contains(KEY_ACCENT)) prefs.getInt(KEY_ACCENT, 0) else null,
            showSeconds = prefs.getBoolean(KEY_SECONDS, d.showSeconds),
            showHints = prefs.getBoolean(KEY_HINTS, d.showHints),
            showDate = prefs.getBoolean(KEY_DATE, d.showDate),
            themeMode = readEnum(KEY_THEME, d.themeMode),
            home = readPlacement(PREFIX_HOME, d.home),
            lockScreenAdjust = prefs.getBoolean(KEY_LOCK_ADJUST, d.lockScreenAdjust),
            lock = readPlacement(PREFIX_LOCK, d.lock),
        )
    }

    private companion object {
        const val PREFS_NAME = "clock_settings"
        const val KEY_STYLE = "style"
        const val KEY_ACCENT = "accent"
        const val KEY_SECONDS = "show_seconds"
        const val KEY_HINTS = "show_hints"
        const val KEY_DATE = "show_date"
        const val KEY_THEME = "theme_mode"
        const val KEY_LOCK_ADJUST = "lock_screen_adjust"
        const val PREFIX_HOME = "home_"
        const val PREFIX_LOCK = "lock_"
    }
}
