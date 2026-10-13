package com.beigel.binaryclock.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.beigel.binaryclock.clock.ClockSettings
import com.beigel.binaryclock.clock.ClockStyle
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
        _settings.value = next
        prefs.edit {
            putString(KEY_STYLE, next.style.name)
            if (next.accentOverride != null) putInt(KEY_ACCENT, next.accentOverride) else remove(KEY_ACCENT)
            putBoolean(KEY_SECONDS, next.showSeconds)
            putBoolean(KEY_HINTS, next.showHints)
            putBoolean(KEY_DATE, next.showDate)
            putBoolean(KEY_LOCK_ADJUST, next.lockScreenAdjust)
        }
    }

    private fun read(): ClockSettings {
        val defaults = ClockSettings()
        val style = prefs.getString(KEY_STYLE, null)
            ?.let { name -> ClockStyle.entries.firstOrNull { it.name == name } }
            ?: defaults.style
        return ClockSettings(
            style = style,
            accentOverride = if (prefs.contains(KEY_ACCENT)) prefs.getInt(KEY_ACCENT, 0) else null,
            showSeconds = prefs.getBoolean(KEY_SECONDS, defaults.showSeconds),
            showHints = prefs.getBoolean(KEY_HINTS, defaults.showHints),
            showDate = prefs.getBoolean(KEY_DATE, defaults.showDate),
            lockScreenAdjust = prefs.getBoolean(KEY_LOCK_ADJUST, defaults.lockScreenAdjust),
        )
    }

    private companion object {
        const val PREFS_NAME = "clock_settings"
        const val KEY_STYLE = "style"
        const val KEY_ACCENT = "accent"
        const val KEY_SECONDS = "show_seconds"
        const val KEY_HINTS = "show_hints"
        const val KEY_DATE = "show_date"
        const val KEY_LOCK_ADJUST = "lock_screen_adjust"
    }
}
