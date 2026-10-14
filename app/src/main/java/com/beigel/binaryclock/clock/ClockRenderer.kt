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

    /** Alles, was die einzelnen Stil-Zeichner brauchen. */
    private class Frame(
        val s: ClockSettings,
        val p: Palette,
        val accent: Int,
        val t: ClockTime,
        val date: String?,
        val dim: Dim,
    )

    /**
     * @param dark helle oder dunkle Palette des Stils.
     * @param placement Position und Größe; null = mittig, so groß wie möglich (Widget).
     * @param cornerRadius > 0 zeichnet den Hintergrund als abgerundetes Rechteck (Widget).
     * @param maxScale Skalierung bei Größe 1 – entspricht einem 390 dp breiten Handy.
     */
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        settings: ClockSettings,
        now: Calendar,
        dark: Boolean,
        placement: Placement? = null,
        cornerRadius: Float = 0f,
        maxScale: Float = width / 390f,
    ) {
        val palette = settings.style.palette(dark)
        val time = ClockTime(
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            now.get(Calendar.SECOND),
        )
        val date = if (settings.showDate) formatDate(now) else null

        // Hintergrund
        plainFill(palette.background)
        if (cornerRadius > 0f) {
            rect.set(0f, 0f, width, height)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fill)
        } else {
            canvas.drawColor(palette.background)
        }

        val dim = measure(settings, date != null)
        val fit = if (placement == null) {
            minOf(width * 0.86f / dim.w, height * 0.82f / dim.h)
        } else {
            minOf(width * 0.96f / dim.w, height * 0.96f / dim.h)
        }
        val scale = minOf(fit, maxScale * (placement?.size ?: 1f))
        if (scale <= 0f) return

        val blockW = dim.w * scale
        val blockH = dim.h * scale
        val left = (width * (placement?.x ?: 0.5f) - blockW / 2f)
            .coerceIn(0f, (width - blockW).coerceAtLeast(0f))
        val top = (height * (placement?.y ?: 0.5f) - blockH / 2f)
            .coerceIn(0f, (height - blockH).coerceAtLeast(0f))

        val f = Frame(settings, palette, settings.accent(dark), time, date, dim)
        canvas.withTranslation(left, top) {
            scale(scale, scale)
            when (settings.style) {
                ClockStyle.CLASSIC -> drawClassic(this, f)
                ClockStyle.BINARY -> drawBinary(this, f)
                ClockStyle.LED -> drawLed(this, f)
                ClockStyle.PAPER -> drawPaper(this, f)
                ClockStyle.RINGS -> drawRings(this, f)
                ClockStyle.TERMINAL -> drawTerminal(this, f)
                ClockStyle.BARS -> drawBars(this, f)
            }
        }
        fill.clearShadowLayer()
        stroke.clearShadowLayer()
        text.clearShadowLayer()
    }

    // ---------------------------------------------------------------- Maße

    private fun bcdGridWidth(n: Int, cell: Float, gap: (Int) -> Float) =
        n * cell + (0 until n - 1).sumOf { gap(it).toDouble() }.toFloat()

    private fun measure(s: ClockSettings, hasDate: Boolean): Dim {
        val rows = if (s.showSeconds) 3 else 2
        val cols = if (s.showSeconds) 6 else 4
        return when (s.style) {
            ClockStyle.CLASSIC -> {
                val w = bcdGridWidth(cols, CL_DOT, ::classicGap)
                var h = 4 * CL_DOT + 3 * CL_VGAP
                if (s.showHints || hasDate) h += 36f
                if (s.showHints) h += 20f
                if (s.showHints && hasDate) h += 6f
                if (hasDate) h += 16f
                Dim(maxOf(w, if (hasDate) 230f else 0f), h)
            }

            ClockStyle.BINARY -> {
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
                var h = rows * PA_DOT + (rows - 1) * PA_ROW_GAP
                if (hasDate) h += 16f + 40f
                Dim(6 * PA_DOT + 5 * PA_GAP + if (s.showHints) 24f + 52f else 0f, h)
            }

            ClockStyle.RINGS -> {
                val d = 2 * ringOuter(rows)
                Dim(maxOf(d, if (hasDate) 230f else 0f), d + if (hasDate) 24f + 14f else 0f)
            }

            ClockStyle.TERMINAL -> {
                var h = 13f + 18f + rows * TE_ROW_H + (rows - 1) * TE_ROW_GAP
                if (hasDate) h += 16f + 14f
                Dim(TE_LABEL_W + 6 * TE_SLOT + if (s.showHints) 92f else 0f, h)
            }

            ClockStyle.BARS -> {
                val w = bcdGridWidth(cols, BA_W, ::classicGap)
                var h = 4 * BA_H + 3 * BA_VGAP
                if (s.showHints) h += 14f + 18f
                if (hasDate) h += 14f + 14f
                Dim(maxOf(w, if (hasDate) 230f else 0f), h)
            }
        }
    }

    // ------------------------------------------------------------- Klassisch

    private fun classicGap(i: Int) = if (i % 2 == 1) 28f else 14f

    private fun drawClassic(c: Canvas, f: Frame) {
        val s = f.s
        val groups = BinaryTime.bcd(f.t, s.showSeconds)
        val gridW = bcdGridWidth(groups.size, CL_DOT, ::classicGap)
        var x = (f.dim.w - gridW) / 2f
        val r = CL_DOT / 2f
        groups.forEachIndexed { i, g ->
            for (row in 0..3) {
                val bit = 3 - row
                if (bit >= g.bits) continue
                if (g.isOn(bit)) onFill(f, 18f, 0x99) else plainFill(f.p.off)
                c.drawCircle(x + r, row * (CL_DOT + CL_VGAP) + r, r, fill)
            }
            x += CL_DOT + if (i < groups.size - 1) classicGap(i) else 0f
        }

        var y = 4 * CL_DOT + 3 * CL_VGAP + 36f
        if (s.showHints) {
            setText(mono, 15f, f.p.text1, Paint.Align.CENTER, 0.27f)
            c.drawText(timeText(f.t, s.showSeconds), f.dim.w / 2f, y + 15f, text)
            y += 26f
        }
        if (f.date != null) {
            setText(mono, 12f, f.p.text2, Paint.Align.CENTER, 0f)
            c.drawText(f.date, f.dim.w / 2f, y + 12f, text)
        }
    }

    // ----------------------------------------------------------------- Binär

    private fun drawBinary(c: Canvas, f: Frame) {
        val s = f.s
        val groups = BinaryTime.binary(f.t, s.showSeconds)
        val x0 = BI_LABEL_W + BI_GAP
        var y = 0f

        if (s.showHints) {
            setText(mono, 11f, f.p.text2, Paint.Align.CENTER, 0f)
            for (i in 0 until 6) {
                c.drawText((1 shl (5 - i)).toString(), x0 + i * (BI_PILL_W + BI_GAP) + BI_PILL_W / 2f, 11f, text)
            }
            y = 14f + 22f
        }

        groups.forEachIndexed { k, g ->
            setText(monoBold, 14f, f.p.text1, Paint.Align.LEFT, 0f)
            c.drawText(LABELS[k], 0f, y + 16f, text)
            for (i in 0 until 6) {
                val bit = 5 - i
                if (bit >= g.bits) continue
                val px = x0 + i * (BI_PILL_W + BI_GAP)
                if (g.isOn(bit)) {
                    rect.set(px, y, px + BI_PILL_W, y + BI_PILL_H)
                    onFill(f, 14f, 0x80)
                    c.drawRoundRect(rect, BI_PILL_H / 2f, BI_PILL_H / 2f, fill)
                } else {
                    rect.set(px + 0.75f, y + 0.75f, px + BI_PILL_W - 0.75f, y + BI_PILL_H - 0.75f)
                    plainStroke(f.p.offStroke, 1.5f)
                    c.drawRoundRect(rect, BI_PILL_H / 2f, BI_PILL_H / 2f, stroke)
                }
            }
            y += BI_PILL_H + BI_ROW_GAP
        }
        y -= BI_ROW_GAP

        if (s.showHints) {
            setText(mono, 13f, f.p.text1, Paint.Align.LEFT, 0.23f)
            c.drawText(timeText(f.t, s.showSeconds), x0, y + 14f + 13f, text)
            y += 14f + 16f
        }
        if (f.date != null) {
            y += if (s.showHints) 8f else 14f
            setText(mono, 12f, f.p.text2, Paint.Align.LEFT, 0f)
            c.drawText(f.date, x0, y + 12f, text)
        }
    }

    // ------------------------------------------------------------------- LED

    private fun ledGap(i: Int) = if (i % 2 == 1) 20f else 10f

    private fun ledPanel(s: ClockSettings): Dim {
        val n = if (s.showSeconds) 6 else 4
        val gridW = bcdGridWidth(n, LED_CELL, ::ledGap)
        val gridH = 4 * LED_CELL + 3 * LED_GAP
        val w = LED_PAD_H * 2 + (if (s.showHints) 24f else 0f) + gridW
        val h = LED_PAD_V * 2 + gridH + if (s.showHints) 38f else 0f
        return Dim(w, h)
    }

    private fun drawLed(c: Canvas, f: Frame) {
        val s = f.s
        val panel = ledPanel(s)
        rect.set(0f, 0f, panel.w, panel.h)
        plainFill(f.p.panel)
        c.drawRoundRect(rect, 24f, 24f, fill)
        rect.set(0.5f, 0.5f, panel.w - 0.5f, panel.h - 0.5f)
        plainStroke(f.p.panelStroke, 1f)
        c.drawRoundRect(rect, 24f, 24f, stroke)

        var x = LED_PAD_H
        if (s.showHints) {
            setText(mono, 10f, f.p.text2, Paint.Align.CENTER, 0f)
            for (row in 0..3) {
                c.drawText((1 shl (3 - row)).toString(), x + 7f, LED_PAD_V + row * (LED_CELL + LED_GAP) + LED_CELL / 2f + 4f, text)
            }
            x += 24f
        }

        val groups = BinaryTime.bcd(f.t, s.showSeconds)
        groups.forEachIndexed { i, g ->
            for (row in 0..3) {
                val bit = 3 - row
                if (bit >= g.bits) continue
                val cy = LED_PAD_V + row * (LED_CELL + LED_GAP)
                rect.set(x, cy, x + LED_CELL, cy + LED_CELL)
                if (g.isOn(bit)) onFill(f, 16f, 0x88) else plainFill(f.p.off)
                c.drawRoundRect(rect, 8f, 8f, fill)
            }
            if (s.showHints) {
                setText(monoBold, 20f, f.accent, Paint.Align.CENTER, 0f)
                c.drawText(g.value.toString(), x + LED_CELL / 2f, LED_PAD_V + 4 * LED_CELL + 3 * LED_GAP + 32f, text)
            }
            x += LED_CELL + if (i < groups.size - 1) ledGap(i) else 0f
        }

        if (f.date != null) {
            setText(mono, 11f, f.p.text2, Paint.Align.CENTER, 0.18f)
            c.drawText(f.date.uppercase(), f.dim.w / 2f, panel.h + 20f + 11f, text)
        }
    }

    // ----------------------------------------------------------------- Papier

    private fun drawPaper(c: Canvas, f: Frame) {
        val s = f.s
        var y = 0f
        if (f.date != null) {
            setText(sans, 13f, f.p.text2, Paint.Align.LEFT, 0.23f)
            c.drawText(f.date.uppercase(), 0f, 13f, text)
            y = 16f + 40f
        }

        val r = PA_DOT / 2f
        BinaryTime.binary(f.t, s.showSeconds).forEachIndexed { k, g ->
            val color = if (k == 2) f.accent else f.p.ink
            for (i in 0 until 6) {
                val bit = 5 - i
                if (bit >= g.bits) continue
                val cx = i * (PA_DOT + PA_GAP) + r
                if (g.isOn(bit)) {
                    plainFill(color)
                    c.drawCircle(cx, y + r, r, fill)
                } else {
                    plainStroke(f.p.offStroke, 2f)
                    c.drawCircle(cx, y + r, r - 1f, stroke)
                }
            }
            if (s.showHints) {
                setText(sansBold, 30f, color, Paint.Align.RIGHT, 0f)
                c.drawText(two(g.value), f.dim.w, y + r + 11f, text)
            }
            y += PA_DOT + PA_ROW_GAP
        }
    }

    // ------------------------------------------------------------------ Ringe

    /** Halber Außendurchmesser inkl. Platz für den Leuchteffekt. */
    private fun ringOuter(rings: Int) = RI_R0 + (rings - 1) * RI_STEP + RI_STROKE / 2f + 8f

    private fun drawRings(c: Canvas, f: Frame) {
        val s = f.s
        val groups = BinaryTime.binary(f.t, s.showSeconds)
        val center = f.dim.w / 2f
        val cy = ringOuter(groups.size)
        stroke.strokeCap = Paint.Cap.BUTT
        groups.forEachIndexed { k, g ->
            val radius = RI_R0 + k * RI_STEP
            rect.set(center - radius, cy - radius, center + radius, cy + radius)
            val seg = 360f / g.bits
            for (i in 0 until g.bits) {
                val bit = g.bits - 1 - i // oben beginnend, höchstwertiges Bit zuerst
                if (g.isOn(bit)) {
                    stroke.color = f.accent
                    stroke.strokeWidth = RI_STROKE
                    if (f.p.glow) stroke.setShadowLayer(14f, 0f, 0f, withAlpha(f.accent, 0x88)) else stroke.clearShadowLayer()
                } else {
                    plainStroke(f.p.off, RI_STROKE)
                }
                c.drawArc(rect, -90f + i * seg + RI_GAP_DEG / 2f, seg - RI_GAP_DEG, false, stroke)
            }
        }
        stroke.clearShadowLayer()

        if (s.showHints) {
            val size = if (s.showSeconds) 15f else 22f
            setText(monoBold, size, f.p.text1, Paint.Align.CENTER, 0f)
            c.drawText(timeText(f.t, s.showSeconds), center, cy + size * 0.36f, text)
        }
        if (f.date != null) {
            setText(mono, 12f, f.p.text2, Paint.Align.CENTER, 0f)
            c.drawText(f.date, center, 2 * cy + 24f + 12f, text)
        }
    }

    // --------------------------------------------------------------- Terminal

    private fun drawTerminal(c: Canvas, f: Frame) {
        val s = f.s
        setText(mono, 13f, f.p.text2, Paint.Align.LEFT, 0f)
        c.drawText("\$ binary-clock", 0f, 13f, text)
        var y = 13f + 18f

        BinaryTime.binary(f.t, s.showSeconds).forEachIndexed { k, g ->
            setText(monoBold, 14f, f.p.text2, Paint.Align.LEFT, 0f)
            c.drawText(LABELS[k], 0f, y + 27f, text)
            for (i in 0 until 6) {
                val bit = 5 - i
                if (bit >= g.bits) continue
                val on = g.isOn(bit)
                setText(monoBold, 34f, if (on) f.accent else f.p.off, Paint.Align.CENTER, 0f)
                if (on && f.p.glow) text.setShadowLayer(12f, 0f, 0f, withAlpha(f.accent, 0x99))
                c.drawText(if (on) "1" else "0", TE_LABEL_W + i * TE_SLOT + TE_SLOT / 2f, y + 32f, text)
                text.clearShadowLayer()
            }
            if (s.showHints) {
                setText(mono, 18f, f.p.text1, Paint.Align.LEFT, 0f)
                c.drawText("= " + two(g.value), TE_LABEL_W + 6 * TE_SLOT + 18f, y + 28f, text)
            }
            y += TE_ROW_H + TE_ROW_GAP
        }
        y -= TE_ROW_GAP

        if (f.date != null) {
            setText(mono, 12f, f.p.text2, Paint.Align.LEFT, 0f)
            c.drawText("# " + f.date, 0f, y + 16f + 12f, text)
        }
    }

    // ----------------------------------------------------------------- Balken

    private fun drawBars(c: Canvas, f: Frame) {
        val s = f.s
        val groups = BinaryTime.bcd(f.t, s.showSeconds)
        val gridW = bcdGridWidth(groups.size, BA_W, ::classicGap)
        var x = (f.dim.w - gridW) / 2f
        val gridH = 4 * BA_H + 3 * BA_VGAP
        groups.forEachIndexed { i, g ->
            for (row in 0..3) {
                val bit = 3 - row
                if (bit >= g.bits) continue
                val y = row * (BA_H + BA_VGAP)
                rect.set(x, y, x + BA_W, y + BA_H)
                if (g.isOn(bit)) onFill(f, 12f, 0x88) else plainFill(f.p.off)
                c.drawRoundRect(rect, BA_H / 2f, BA_H / 2f, fill)
            }
            if (s.showHints) {
                setText(mono, 14f, f.p.text1, Paint.Align.CENTER, 0f)
                c.drawText(g.value.toString(), x + BA_W / 2f, gridH + 14f + 14f, text)
            }
            x += BA_W + if (i < groups.size - 1) classicGap(i) else 0f
        }
        if (f.date != null) {
            val y = gridH + (if (s.showHints) 32f else 0f) + 14f
            setText(mono, 12f, f.p.text2, Paint.Align.CENTER, 0f)
            c.drawText(f.date, f.dim.w / 2f, y + 12f, text)
        }
    }

    // ----------------------------------------------------------------- Hilfen

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun plainFill(color: Int) {
        fill.clearShadowLayer()
        fill.color = color
    }

    private fun plainStroke(color: Int, width: Float) {
        stroke.clearShadowLayer()
        stroke.color = color
        stroke.strokeWidth = width
    }

    /** Aktives Bit: Akzentfarbe, auf dunklem Grund mit Leuchteffekt. */
    private fun onFill(f: Frame, radius: Float, alpha: Int) {
        fill.color = f.accent
        if (f.p.glow) fill.setShadowLayer(radius, 0f, 0f, withAlpha(f.accent, alpha)) else fill.clearShadowLayer()
    }

    private fun setText(face: Typeface, size: Float, color: Int, align: Paint.Align, spacing: Float) {
        text.typeface = face
        text.textSize = size
        text.color = color
        text.textAlign = align
        text.letterSpacing = spacing
    }

    private fun two(n: Int) = if (n < 10) "0$n" else n.toString()

    private fun timeText(t: ClockTime, seconds: Boolean): String =
        if (seconds) "${two(t.hour)}:${two(t.minute)}:${two(t.second)}"
        else "${two(t.hour)}:${two(t.minute)}"

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
        val LABELS = listOf("H", "M", "S")

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

        const val RI_R0 = 58f
        const val RI_STEP = 32f
        const val RI_STROKE = 20f
        const val RI_GAP_DEG = 6f

        const val TE_LABEL_W = 30f
        const val TE_SLOT = 28f
        const val TE_ROW_H = 40f
        const val TE_ROW_GAP = 8f

        const val BA_W = 38f
        const val BA_H = 14f
        const val BA_VGAP = 12f
    }
}
