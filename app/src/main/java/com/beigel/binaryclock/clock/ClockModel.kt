package com.beigel.binaryclock.clock

import androidx.annotation.StringRes
import com.beigel.binaryclock.R

/** Wie die Uhrzeit in Bits zerlegt wird. */
enum class Encoding {
    /** Jede Dezimalziffer einzeln (Binary Coded Decimal), 6 Spalten. */
    BCD,

    /** Stunde, Minute, Sekunde jeweils als eine Binärzahl, 3 Zeilen. */
    BINARY,
}

enum class ClockStyle(
    @StringRes val label: Int,
    val encoding: Encoding,
    val background: Int,
    val defaultAccent: Int,
    val isLight: Boolean = false,
) {
    CLASSIC(R.string.style_classic, Encoding.BCD, 0xFF0E0F13.toInt(), 0xFFFFB347.toInt()),
    BINARY(R.string.style_binary, Encoding.BINARY, 0xFF050B14.toInt(), 0xFF5CE1E6.toInt()),
    LED(R.string.style_led, Encoding.BCD, 0xFF161815.toInt(), 0xFF7CFF6B.toInt()),
    PAPER(R.string.style_paper, Encoding.BINARY, 0xFFEDE8DF.toInt(), 0xFFC93A24.toInt(), isLight = true),
}

data class ClockSettings(
    val style: ClockStyle = ClockStyle.CLASSIC,
    /** null = Standardfarbe des Stils. */
    val accentOverride: Int? = null,
    val showSeconds: Boolean = true,
    val showHints: Boolean = true,
    val showDate: Boolean = true,
) {
    val accent: Int get() = accentOverride ?: style.defaultAccent

    companion object {
        /** Farbauswahl in der App (zusätzlich zur Stil-Standardfarbe). */
        val ACCENT_PRESETS: List<Int> = listOf(
            0xFFFFB347.toInt(), // Bernstein
            0xFF5CE1E6.toInt(), // Cyan
            0xFF7CFF6B.toInt(), // Grün
            0xFFFF6B9A.toInt(), // Pink
            0xFFB69CFF.toInt(), // Lila
            0xFFFF5A4E.toInt(), // Rot
        )
    }
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
