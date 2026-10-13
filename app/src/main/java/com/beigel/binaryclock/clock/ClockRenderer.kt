package com.beigel.binaryclock.clock

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import androidx.core.graphics.withTranslation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Zeichnet die Binäruhr auf einen normalen Android-[Canvas].
 *
 * Wird von Live Wallpaper, Widget (Bitmap) und App-Vorschau (Compose) gleichermaßen
 * genutzt, damit alle drei identisch aussehen. Alle Maße unten sind „Design-Einheiten“
 * (entsprechen dp auf einem 390 dp breiten Handy) und werden beim Zeichnen skaliert.
 *
 * Nicht thread-safe: jede Komponente nutzt ihre eigene Instanz.
 */
class ClockRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var dateLocale: Locale? = null
    private var dateFormat: SimpleDateFormat? = null

    private val mono: Typeface = Typeface.MONOSPACE
    private val monoBold: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val sans: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val sansBold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private data class Dim(val w: Float, val h: Float)

    /**
     * @param centerYFraction vertikale Position der Uhr (0.5 = Mitte).
     * @param cornerRadius > 0 zeichnet den Hintergrund als abgerundetes Rechteck (Widget).
     * @param maxScale Obergrenze für die Skalierung, damit die Uhr auf großen Flächen nicht riesig wird.
     */
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        settings: ClockSettings,
        now: Calendar,
        centerYFraction: Float = 0.5f,
        cornerRadius: Float = 0f,
        maxScale: Float = width / 390f,
    ) {
        val style = settings.style
        val time = ClockTime(
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            now.get(Calendar.SECOND),
        )
        val date = if (settings.showDate) formatDate(now) else null

        // Hintergrund
        fill.clearShadowLayer()
        fill.color = style.background
        if (cornerRadius > 0f) {
            rect.set(0f, 0f, width, height)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fill)
        } else {
            canvas.drawColor(style.background)
        }

        val dim = measure(settings, date != null)
        val scale = minOf(width * 0.86f / dim.w, height * 0.82f / dim.h, maxScale)
        if (scale <= 0f) return

        val blockH = dim.h * scale
        val top = (height * centerYFraction - blockH / 2f)
            .coerceIn(0f, (height - blockH).coerceAtLeast(0f))

        canvas.withTranslation(width / 2f - dim.w * scale / 2f, top) {
            scale(scale, scale)
            when (style) {
                ClockStyle.CLASSIC -> drawClassic(this, settings, time, date, dim)
                ClockStyle.BINARY -> drawBinary(this, settings, time, date)
                ClockStyle.LED -> drawLed(this, settings, time, date, dim)
                ClockStyle.PAPER -> drawPaper(this, settings, time, date, dim)
            }
        }
        fill.clearShadowLayer()
    }

    // ---------------------------------------------------------------- Maße

    private fun measure(s: ClockSettings, hasDate: Boolean): Dim = when (s.style) {
        ClockStyle.CLASSIC -> {
            val n = if (s.showSeconds) 6 else 4
            val w = n * CL_DOT + (0 until n - 1).sumOf { classicGap(it).toDouble() }.toFloat()
            var h = 4 * CL_DOT + 3 * CL_VGAP
            if (s.showHints || hasDate) h += 36f
            if (s.showHints) h += 20f
            if (s.showHints && hasDate) h += 6f
            if (hasDate) h += 16f
            Dim(maxOf(w, if (hasDate) 230f else 0f), h)
        }

        ClockStyle.BINARY -> {
            val rows = if (s.showSeconds) 3 else 2
            var h = rows * BI_PILL_H + (rows - 1) * BI_ROW_GAP
            if (s.showHints) h += 14f + 22f + 14f + 16f
            if (hasDate) h += (if (s.showHints) 8f else 14f) + 14f
            Dim(BI_LABEL_W + BI_GAP + 6 * BI_PILL_W + 5 * BI_GAP, h)
        }

        ClockStyle.LED -> {
            val panel = ledPanel(s)
            Dim(panel.w, panel.h + if (hasDate) 34f else 0f)
        }

        ClockStyle.PAPER -> {
            val rows = if (s.showSeconds) 3 else 2
            var h = rows * PA_DOT + (rows - 1) * PA_ROW_GAP
            if (hasDate) h += 16f + 40f
            val w = 6 * PA_DOT + 5 * PA_GAP + if (s.showHints) 24f + 52f else 0f
            Dim(w, h)
        }
    }

    // ------------------------------------------------------------- Klassisch

    private fun classicGap(i: Int) = if (i % 2 == 1) 28f else 14f

    private fun drawClassic(c: Canvas, s: ClockSettings, t: ClockTime, date: String?, dim: Dim) {
        val groups = BinaryTime.bcd(t, s.showSeconds)
        val gridW = groups.size * CL_DOT + (0 until groups.size - 1).sumOf { classicGap(it).toDouble() }.toFloat()
        var x = (dim.w - gridW) / 2f
        val r = CL_DOT / 2f
        groups.forEachIndexed { i, g ->
            for (row in 0..3) {
                val bit = 3 - row
                if (bit >= g.bits) continue
                val cx = x + r
                val cy = row * (CL_DOT + CL_VGAP) + r
                if (g.isOn(bit)) {
                    glowFill(s.accent, 18f, 0x99)
                } else {
                    plainFill(0x14FFFFFF)
                }
                c.drawCircle(cx, cy, r, fill)
            }
            x += CL_DOT + if (i < groups.size - 1) classicGap(i) else 0f
        }

        var y = 4 * CL_DOT + 3 * CL_VGAP + 36f
        if (s.showHints) {
            setText(mono, 15f, 0xFF8A8780.toInt(), Paint.Align.CENTER, 0.27f)
            c.drawText(timeText(t, s.showSeconds), dim.w / 2f, y + 15f, text)
            y += 26f
        }
        if (date != null) {
            setText(mono, 12f, 0xFF6B6963.toInt(), Paint.Align.CENTER, 0f)
            c.drawText(date, dim.w / 2f, y + 12f, text)
        }
    }

    // ----------------------------------------------------------------- Binär

    private fun drawBinary(c: Canvas, s: ClockSettings, t: ClockTime, date: String?) {
        val groups = BinaryTime.binary(t, s.showSeconds)
        val labels = listOf("H", "M", "S")
        val x0 = BI_LABEL_W + BI_GAP
        var y = 0f

        if (s.showHints) {
            setText(mono, 11f, 0xFF4A5A6B.toInt(), Paint.Align.CENTER, 0f)
            for (i in 0 until 6) {
                val weight = 1 shl (5 - i)
                c.drawText(weight.toString(), x0 + i * (BI_PILL_W + BI_GAP) + BI_PILL_W / 2f, 11f, text)
            }
            y = 14f + 22f
        }

        groups.forEachIndexed { k, g ->
            setText(monoBold, 14f, 0xFF6F8296.toInt(), Paint.Align.LEFT, 0f)
            c.drawText(labels[k], 0f, y + 16f, text)
            for (i in 0 until 6) {
                val bit = 5 - i
                if (bit >= g.bits) continue
                val px = x0 + i * (BI_PILL_W + BI_GAP)
                if (g.isOn(bit)) {
                    rect.set(px, y, px + BI_PILL_W, y + BI_PILL_H)
                    glowFill(s.accent, 14f, 0x80)
                    c.drawRoundRect(rect, BI_PILL_H / 2f, BI_PILL_H / 2f, fill)
                } else {
                    rect.set(px + 0.75f, y + 0.75f, px + BI_PILL_W - 0.75f, y + BI_PILL_H - 0.75f)
                    stroke.color = 0xFF22344A.toInt()
                    stroke.strokeWidth = 1.5f
                    c.drawRoundRect(rect, BI_PILL_H / 2f, BI_PILL_H / 2f, stroke)
                }
            }
            y += BI_PILL_H + BI_ROW_GAP
        }
        y -= BI_ROW_GAP

        if (s.showHints) {
            setText(mono, 13f, 0xFF6F8296.toInt(), Paint.Align.LEFT, 0.23f)
            c.drawText(timeText(t, s.showSeconds), x0, y + 14f + 13f, text)
            y += 14f + 16f
        }
        if (date != null) {
            y += if (s.showHints) 8f else 14f
            setText(mono, 12f, 0xFF4A5A6B.toInt(), Paint.Align.LEFT, 0f)
            c.drawText(date, x0, y + 12f, text)
        }
    }

    // ------------------------------------------------------------------- LED

    private fun ledGap(i: Int) = if (i % 2 == 1) 20f else 10f

    private fun ledPanel(s: ClockSettings): Dim {
        val n = if (s.showSeconds) 6 else 4
        val gridW = n * LED_CELL + (0 until n - 1).sumOf { ledGap(it).toDouble() }.toFloat()
        val gridH = 4 * LED_CELL + 3 * LED_GAP
        val w = LED_PAD_H * 2 + (if (s.showHints) 24f else 0f) + gridW
        val h = LED_PAD_V * 2 + gridH + if (s.showHints) 38f else 0f
        return Dim(w, h)
    }

    private fun drawLed(c: Canvas, s: ClockSettings, t: ClockTime, date: String?, dim: Dim) {
        val panel = ledPanel(s)
        rect.set(0f, 0f, panel.w, panel.h)
        plainFill(0xFF0D0F0C.toInt())
        c.drawRoundRect(rect, 24f, 24f, fill)
        rect.set(0.5f, 0.5f, panel.w - 0.5f, panel.h - 0.5f)
        stroke.color = 0xFF262A24.toInt()
        stroke.strokeWidth = 1f
        c.drawRoundRect(rect, 24f, 24f, stroke)

        var x = LED_PAD_H
        if (s.showHints) {
            setText(mono, 10f, 0xFF5A6356.toInt(), Paint.Align.CENTER, 0f)
            for (row in 0..3) {
                val weight = 1 shl (3 - row)
                c.drawText(weight.toString(), x + 7f, LED_PAD_V + row * (LED_CELL + LED_GAP) + LED_CELL / 2f + 4f, text)
            }
            x += 24f
        }

        val groups = BinaryTime.bcd(t, s.showSeconds)
        groups.forEachIndexed { i, g ->
            for (row in 0..3) {
                val bit = 3 - row
                if (bit >= g.bits) continue
                val cy = LED_PAD_V + row * (LED_CELL + LED_GAP)
                rect.set(x, cy, x + LED_CELL, cy + LED_CELL)
                if (g.isOn(bit)) {
                    glowFill(s.accent, 16f, 0x88)
                } else {
                    plainFill(0xFF1F231D.toInt())
                }
                c.drawRoundRect(rect, 8f, 8f, fill)
            }
            if (s.showHints) {
                setText(monoBold, 20f, s.accent, Paint.Align.CENTER, 0f)
                val baseline = LED_PAD_V + 4 * LED_CELL + 3 * LED_GAP + 8f + 24f
                c.drawText(g.value.toString(), x + LED_CELL / 2f, baseline, text)
            }
            x += LED_CELL + if (i < groups.size - 1) ledGap(i) else 0f
        }

        if (date != null) {
            setText(mono, 11f, 0xFF5A6356.toInt(), Paint.Align.CENTER, 0.18f)
            c.drawText(date.uppercase(), dim.w / 2f, panel.h + 20f + 11f, text)
        }
    }

    // ----------------------------------------------------------------- Papier

    private fun drawPaper(c: Canvas, s: ClockSettings, t: ClockTime, date: String?, dim: Dim) {
        var y = 0f
        if (date != null) {
            setText(sans, 13f, 0xFF5C574F.toInt(), Paint.Align.LEFT, 0.23f)
            c.drawText(date.uppercase(), 0f, 13f, text)
            y = 16f + 40f
        }

        val ink = 0xFF1A1A1A.toInt()
        val groups = BinaryTime.binary(t, s.showSeconds)
        val r = PA_DOT / 2f
        groups.forEachIndexed { k, g ->
            val color = if (k == 2) s.accent else ink
            for (i in 0 until 6) {
                val bit = 5 - i
                if (bit >= g.bits) continue
                val cx = i * (PA_DOT + PA_GAP) + r
                val cy = y + r
                if (g.isOn(bit)) {
                    plainFill(color)
                    c.drawCircle(cx, cy, r, fill)
                } else {
                    stroke.color = 0x381A1A1A
                    stroke.strokeWidth = 2f
                    c.drawCircle(cx, cy, r - 1f, stroke)
                }
            }
            if (s.showHints) {
                setText(sansBold, 30f, color, Paint.Align.RIGHT, 0f)
                c.drawText("%02d".format(g.value), dim.w, y + r + 11f, text)
            }
            y += PA_DOT + PA_ROW_GAP
        }
    }

    // ----------------------------------------------------------------- Hilfen

    private fun plainFill(color: Int) {
        fill.clearShadowLayer()
        fill.color = color
    }

    private fun glowFill(color: Int, radius: Float, alpha: Int) {
        fill.color = color
        fill.setShadowLayer(radius, 0f, 0f, (color and 0x00FFFFFF) or (alpha shl 24))
    }

    private fun setText(face: Typeface, size: Float, color: Int, align: Paint.Align, spacing: Float) {
        text.typeface = face
        text.textSize = size
        text.color = color
        text.textAlign = align
        text.letterSpacing = spacing
    }

    private fun timeText(t: ClockTime, seconds: Boolean): String =
        if (seconds) "%02d:%02d:%02d".format(t.hour, t.minute, t.second)
        else "%02d:%02d".format(t.hour, t.minute)

    private fun formatDate(now: Calendar): String {
        val locale = Locale.getDefault()
        val format = dateFormat?.takeIf { dateLocale == locale }
            ?: SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).also {
                dateFormat = it
                dateLocale = locale
            }
        format.timeZone = now.timeZone
        return format.format(now.time)
    }

    private companion object {
        const val CL_DOT = 34f
        const val CL_VGAP = 14f

        const val BI_LABEL_W = 26f
        const val BI_GAP = 10f
        const val BI_PILL_W = 38f
        const val BI_PILL_H = 22f
        const val BI_ROW_GAP = 22f

        const val LED_CELL = 38f
        const val LED_GAP = 8f
        const val LED_PAD_H = 20f
        const val LED_PAD_V = 22f

        const val PA_DOT = 34f
        const val PA_GAP = 12f
        const val PA_ROW_GAP = 28f
    }
}
