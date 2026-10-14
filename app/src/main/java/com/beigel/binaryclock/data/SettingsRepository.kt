package com.beigel.binaryclock.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.beigel.binaryclock.clock.AppSettings
import com.beigel.binaryclock.clock.ClockSettings
import com.beigel.binaryclock.clock.Placement
import com.beigel.binaryclock.clock.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Einstellungen in SharedPreferences, getrennt für Homescreen ("home_…") und
 * Sperrbildschirm ("lock_…"). Wallpaper-Service, Widget und App laufen im selben Prozess
 * und teilen sich diese eine Instanz über den [com.beigel.binaryclock.AppContainer].
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Als Feld halten, sonst räumt der GC den Listener weg.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun update(screen: Screen, transform: (ClockSettings) -> ClockSettings) {
        val current = _settings.value
        val next = transform(current[screen])
        if (next == current[screen]) return
        _settings.value = current.with(screen, next)
        prefs.edit { write(this, prefix(screen), next) }
    }

    /** Übernimmt alle Homescreen-Einstellungen für den Sperrbildschirm (Position bleibt). */
    fun copyHomeToLock() = update(Screen.LOCK) { lock ->
        _settings.value.home.copy(placement = lock.placement)
    }

    private fun prefix(screen: Screen) = if (screen == Screen.HOME) "home_" else "lock_"

    private fun write(e: SharedPreferences.Editor, p: String, s: ClockSettings) {
        e.putString(p + KEY_STYLE, s.style.name)
        if (s.accentOverride != null) e.putInt(p + KEY_ACCENT, s.accentOverride) else e.remove(p + KEY_ACCENT)
        e.putBoolean(p + KEY_SECONDS, s.showSeconds)
        e.putBoolean(p + KEY_HINTS, s.showHints)
        e.putBoolean(p + KEY_DATE, s.showDate)
        e.putString(p + KEY_THEME, s.themeMode.name)
        e.putFloat(p + "x", s.placement.x)
        e.putFloat(p + "y", s.placement.y)
        e.putFloat(p + "size", s.placement.size)
    }

    private inline fun <reified E : Enum<E>> readEnum(key: String, legacyKey: String, default: E): E =
        (prefs.getString(key, null) ?: prefs.getString(legacyKey, null))
            ?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun readBool(key: String, legacyKey: String, default: Boolean) = when {
        prefs.contains(key) -> prefs.getBoolean(key, default)
        prefs.contains(legacyKey) -> prefs.getBoolean(legacyKey, default)
        else -> default
    }

    /**
     * Liest einen Bildschirm. Fehlen die neuen Schlüssel (Update von Version ≤ 0.0.2), werden
     * die bisherigen gemeinsamen Einstellungen als Ausgangswert für beide Bildschirme genutzt.
     */
    private fun readScreen(screen: Screen): ClockSettings {
        val p = prefix(screen)
        val d = if (screen == Screen.HOME) ClockSettings.HOME_DEFAULT else ClockSettings.LOCK_DEFAULT
        val accentKey = when {
            prefs.contains(p + KEY_ACCENT) -> p + KEY_ACCENT
            !prefs.contains(p + KEY_STYLE) && prefs.contains(KEY_ACCENT) -> KEY_ACCENT
            else -> null
        }
        return ClockSettings(
            style = readEnum(p + KEY_STYLE, KEY_STYLE, d.style),
            accentOverride = accentKey?.let { prefs.getInt(it, 0) },
            showSeconds = readBool(p + KEY_SECONDS, KEY_SECONDS, d.showSeconds),
            showHints = readBool(p + KEY_HINTS, KEY_HINTS, d.showHints),
            // Sperrbildschirm zeigt standardmäßig kein Datum – das tut das System schon.
            showDate = if (screen == Screen.HOME) readBool(p + KEY_DATE, KEY_DATE, d.showDate)
            else prefs.getBoolean(p + KEY_DATE, d.showDate),
            themeMode = readEnum(p + KEY_THEME, KEY_THEME, d.themeMode),
            placement = Placement(
                x = prefs.getFloat(p + "x", d.placement.x),
                y = prefs.getFloat(p + "y", d.placement.y),
                size = prefs.getFloat(p + "size", d.placement.size),
            ).clamped(),
        )
    }

    private fun read() = AppSettings(home = readScreen(Screen.HOME), lock = readScreen(Screen.LOCK))

    private companion object {
        const val PREFS_NAME = "clock_settings"
        const val KEY_STYLE = "style"
        const val KEY_ACCENT = "accent"
        const val KEY_SECONDS = "show_seconds"
        const val KEY_HINTS = "show_hints"
        const val KEY_DATE = "show_date"
        const val KEY_THEME = "theme_mode"
    }
}
