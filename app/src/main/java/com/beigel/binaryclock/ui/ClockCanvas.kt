package com.beigel.binaryclock.ui

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
 */
@Composable
fun ClockCanvas(
    settings: ClockSettings,
    modifier: Modifier = Modifier,
    live: Boolean = true,
    centerYFraction: Float = 0.45f,
) {
    val renderer = remember { ClockRenderer() }
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
            renderer.draw(
                canvas = it.nativeCanvas,
                width = size.width,
                height = size.height,
                settings = settings,
                now = now,
                centerYFraction = centerYFraction,
                // So skalieren, als wäre die Fläche ein 390 dp breites Handy.
                maxScale = size.width / 390f,
            )
        }
    }
}
