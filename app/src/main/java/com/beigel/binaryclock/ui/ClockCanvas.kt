package com.beigel.binaryclock.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.beigel.binaryclock.clock.ClockRenderer
import com.beigel.binaryclock.clock.ClockSettings
import com.beigel.binaryclock.clock.Placement
import kotlinx.coroutines.delay
import java.util.Calendar

/** Zeitstempel, der sich zu jeder vollen Sekunde ändert. */
@Composable
fun rememberSecondTicker(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        val now = System.currentTimeMillis()
        delay(1_000L - now % 1_000L + 5)
        value = System.currentTimeMillis()
    }
}

/**
 * Zeichnet die Uhr mit demselben Renderer wie Wallpaper und Widget.
 * @param live false = statische Beispielzeit (für die Stil-Auswahl).
 * @param placement null = mittig und maximal groß (Mini-Vorschau).
 * @param systemClockGhost Text, der eine angedeutete Systemuhr beschriftet (Sperrbildschirm-Vorschau).
 */
@Composable
fun ClockCanvas(
    settings: ClockSettings,
    dark: Boolean,
    modifier: Modifier = Modifier,
    live: Boolean = true,
    placement: Placement? = null,
    systemClockGhost: String? = null,
) {
    val renderer = remember { ClockRenderer() }
    val ghostPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
    }
    val ticker = rememberSecondTicker()
    Canvas(modifier) {
        val now = Calendar.getInstance()
        if (live) {
            // Nur im Draw-Lambda lesen: neu gezeichnet wird jede Sekunde, neu komponiert nicht.
            now.timeInMillis = ticker.value
        } else {
            now.set(Calendar.HOUR_OF_DAY, 13)
            now.set(Calendar.MINUTE, 37)
            now.set(Calendar.SECOND, 42)
        }
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            renderer.draw(
                canvas = canvas,
                width = size.width,
                height = size.height,
                settings = settings,
                now = now,
                dark = dark,
                placement = placement,
                // So skalieren, als wäre die Fläche ein 390 dp breites Handy.
                maxScale = size.width / 390f,
            )
            if (systemClockGhost != null) {
                // Grobe Andeutung, wo Android seine eigene Uhr hinsetzt.
                val ink = settings.style.palette(dark).text1
                ghostPaint.color = (ink and 0x00FFFFFF) or (0x55 shl 24)
                ghostPaint.textSize = size.width * 0.2f
                val y = size.height * 0.2f
                val hh = now.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
                val mm = now.get(Calendar.MINUTE).toString().padStart(2, '0')
                canvas.drawText("$hh:$mm", size.width / 2f, y, ghostPaint)
                ghostPaint.textSize = size.width * 0.04f
                canvas.drawText(systemClockGhost, size.width / 2f, y + size.width * 0.07f, ghostPaint)
            }
        }
    }
}
