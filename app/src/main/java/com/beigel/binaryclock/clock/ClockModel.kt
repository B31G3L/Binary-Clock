package com.beigel.binaryclock.clock

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.beigel.binaryclock.R

/** Wie die Uhrzeit in Bits zerlegt wird. */
enum class Encoding {
    /** Jede Dezimalziffer einzeln (Binary Coded Decimal), 6 Spalten. */
    BCD,

    /** Stunde, Minute, Sekunde jeweils als eine Binärzahl, 3 Zeilen. */
    BINARY,
}

/** Farben eines Stils für eine Helligkeit (hell oder dunkel). */
data class Palette(
    val background: Int,
    /** Füllung inaktiver Bits. */
    val off: Int,
    /** Umriss inaktiver Bits (Stile mit Konturen). */
    val offStroke: Int,
    /** Gut lesbarer Hilfstext (Uhrzeit, Werte). */
    val text1: Int,
    /** Zurückhaltender Text (Datum, Stellenwerte). */
    val text2: Int,
    /** Gehäuse/Fläche hinter der Uhr (LED). */
    val panel: Int,
    val panelStroke: Int,
    /** Farbe aktiver Stunden-/Minuten-Bits bei Stilen ohne Akzent dafür (Papier). */
    val ink: Int,
    val accent: Int,
    /** Leuchteffekt um aktive Bits (wirkt nur auf dunklem Grund gut). */
    val glow: Boolean,
)

private fun c(hex: Long) = hex.toInt()

enum class ClockStyle(
    @StringRes val label: Int,
    val encoding: Encoding,
    val dark: Palette,
    val light: Palette,
) {
    CLASSIC(
        R.string.style_classic, Encoding.BCD,
        dark = Palette(c(0xFF0E0F13), 0x14FFFFFF, 0x14FFFFFF, c(0xFF8A8780), c(0xFF6B6963), 0, 0, 0, c(0xFFFFB347), true),
        light = Palette(c(0xFFF4F1EA), 0x17000000, 0x17000000, c(0xFF5E5A53), c(0xFF86827A), 0, 0, 0, c(0xFFE0890B), false),
    ),
    BINARY(
        R.string.style_binary, Encoding.BINARY,
        dark = Palette(c(0xFF050B14), 0, c(0xFF22344A), c(0xFF6F8296), c(0xFF4A5A6B), 0, 0, 0, c(0xFF5CE1E6), true),
        light = Palette(c(0xFFEEF3F7), 0, c(0xFFBFCDDA), c(0xFF4F6274), c(0xFF7A8B9B), 0, 0, 0, c(0xFF0B8C9C), false),
    ),
    LED(
        R.string.style_led, Encoding.BCD,
        dark = Palette(c(0xFF161815), c(0xFF1F231D), 0, c(0xFF8A9585), c(0xFF5A6356), c(0xFF0D0F0C), c(0xFF262A24), 0, c(0xFF7CFF6B), true),
        light = Palette(c(0xFFE4E8E1), c(0xFFDDE2D8), 0, c(0xFF5D6658), c(0xFF7C8576), c(0xFFF8F9F6), c(0xFFC9CFC4), 0, c(0xFF2E9E4A), false),
    ),
    PAPER(
        R.string.style_paper, Encoding.BINARY,
        dark = Palette(c(0xFF1B1A18), 0, 0x40EDE8DF, c(0xFFEDE8DF), c(0xFFA39E94), 0, 0, c(0xFFEDE8DF), c(0xFFFF6A4D), false),
        light = Palette(c(0xFFEDE8DF), 0, 0x381A1A1A, c(0xFF1A1A1A), c(0xFF5C574F), 0, 0, c(0xFF1A1A1A), c(0xFFC93A24), false),
    ),
    RINGS(
        R.string.style_rings, Encoding.BINARY,
        dark = Palette(c(0xFF0B0D12), c(0xFF1B1F28), 0, c(0xFFC9CCD6), c(0xFF6A7080), 0, 0, 0, c(0xFFFF6B9A), true),
        light = Palette(c(0xFFF7F3F5), c(0xFFE7DEE3), 0, c(0xFF2C2A30), c(0xFF7D7580), 0, 0, 0, c(0xFFD23A70), false),
    ),
    TERMINAL(
        R.string.style_terminal, Encoding.BINARY,
        dark = Palette(c(0xFF070B08), c(0xFF1C3324), 0, c(0xFF7FB892), c(0xFF3F6B4D), 0, 0, 0, c(0xFF39FF88), true),
        light = Palette(c(0xFFF0F4EE), c(0xFFC6D4C9), 0, c(0xFF2F5A3C), c(0xFF6E8574), 0, 0, 0, c(0xFF15803D), false),
    ),
    BARS(
        R.string.style_bars, Encoding.BCD,
        dark = Palette(c(0xFF111216), c(0xFF24262E), 0, c(0xFF9A98A6), c(0xFF696776), 0, 0, 0, c(0xFFB69CFF), true),
        light = Palette(c(0xFFF5F4F8), c(0xFFE2E0EA), 0, c(0xFF4C4A57), c(0xFF807E8C), 0, 0, 0, c(0xFF6A4FD6), false),
    ),
    ;

    fun palette(dark: Boolean): Palette = if (dark) this.dark else light
}

enum class ThemeMode(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    ;

    fun isDark(context: Context): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
}

/** Wo die Uhr sitzt (Mittelpunkt, 0..1 relativ zur Fläche) und wie groß sie ist (1 = Standard). */
data class Placement(
    val x: Float = 0.5f,
    val y: Float = 0.45f,
    val size: Float = 1f,
) {
    fun clamped() = Placement(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), size.coerceIn(MIN_SIZE, MAX_SIZE))

    companion object {
        const val MIN_SIZE = 0.4f
        const val MAX_SIZE = 1.8f
        val HOME_DEFAULT = Placement(0.5f, 0.45f, 1f)

        /** Unter der Systemuhr und etwas kleiner. */
        val LOCK_DEFAULT = Placement(0.5f, 0.66f, 0.8f)
    }
}

/** Einstellungen für einen Bildschirm (Homescreen oder Sperrbildschirm). */
data class ClockSettings(
    val style: ClockStyle = ClockStyle.CLASSIC,
    /** null = Standardfarbe des Stils. */
    val accentOverride: Int? = null,
    val showSeconds: Boolean = true,
    val showHints: Boolean = true,
    val showDate: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val placement: Placement = Placement.HOME_DEFAULT,
) {
    fun accent(dark: Boolean): Int = accentOverride ?: style.palette(dark).accent

    companion object {
        /** Farbauswahl in der App (zusätzlich zur Stil-Standardfarbe). */
        val ACCENT_PRESETS: List<Int> = listOf(
            0xFFFFB347.toInt(), // Bernstein
            0xFF5CE1E6.toInt(), // Cyan
            0xFF7CFF6B.toInt(), // Grün
            0xFFFF6B9A.toInt(), // Pink
            0xFFB69CFF.toInt(), // Lila
            0xFFFF5A4E.toInt(), // Rot
            0xFF3B82F6.toInt(), // Blau
        )

        val HOME_DEFAULT = ClockSettings()

        /** Sperrbildschirm: ohne Datum (zeigt das System schon) und unter der Systemuhr. */
        val LOCK_DEFAULT = ClockSettings(showDate = false, placement = Placement.LOCK_DEFAULT)
    }
}

enum class Screen { HOME, LOCK }

/** Homescreen und Sperrbildschirm sind komplett unabhängig voneinander einstellbar. */
data class AppSettings(
    val home: ClockSettings = ClockSettings.HOME_DEFAULT,
    val lock: ClockSettings = ClockSettings.LOCK_DEFAULT,
) {
    operator fun get(screen: Screen): ClockSettings = if (screen == Screen.HOME) home else lock

    fun with(screen: Screen, settings: ClockSettings): AppSettings =
        if (screen == Screen.HOME) copy(home = settings) else copy(lock = settings)
}

data class ClockTime(val hour: Int, val minute: Int, val second: Int)

/** Eine Ziffer bzw. Zahl mit der Anzahl der Bits, die dafür nötig sind. */
data class BitGroup(val value: Int, val bits: Int) {
    fun isOn(bitIndex: Int): Boolean = (value shr bitIndex) and 1 == 1
}

object BinaryTime {
    /** BCD: HH MM (SS) als einzelne Ziffern. Zehnerstellen brauchen weniger Bits. */
    fun bcd(time: ClockTime, withSeconds: Boolean): List<BitGroup> = buildList {
        add(BitGroup(time.hour / 10, 2))
        add(BitGroup(time.hour % 10, 4))
        add(BitGroup(time.minute / 10, 3))
        add(BitGroup(time.minute % 10, 4))
        if (withSeconds) {
            add(BitGroup(time.second / 10, 3))
            add(BitGroup(time.second % 10, 4))
        }
    }

    /** Echt binär: Stunde (5 Bit), Minute (6 Bit), Sekunde (6 Bit). */
    fun binary(time: ClockTime, withSeconds: Boolean): List<BitGroup> = buildList {
        add(BitGroup(time.hour, 5))
        add(BitGroup(time.minute, 6))
        if (withSeconds) add(BitGroup(time.second, 6))
    }
}
