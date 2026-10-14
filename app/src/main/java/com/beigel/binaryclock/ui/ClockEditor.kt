package com.beigel.binaryclock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.beigel.binaryclock.clock.ClockSettings
import com.beigel.binaryclock.clock.Placement
import com.beigel.binaryclock.clock.Screen
import kotlin.math.abs
import kotlin.math.roundToInt

/** Wie nah (Anteil der Fläche) die Uhr an die Mitte kommen muss, damit sie einrastet. */
private const val SNAP_POS = 0.03f

/** Größe rastet bei 100 % ein, wenn sie so nah dran ist. */
private const val SNAP_SIZE = 0.06f

private data class Snapped(val placement: Placement, val x: Boolean, val y: Boolean, val size: Boolean)

private fun snap(raw: Placement): Snapped {
    val sx = abs(raw.x - 0.5f) < SNAP_POS
    val sy = abs(raw.y - 0.5f) < SNAP_POS
    val ss = abs(raw.size - 1f) < SNAP_SIZE
    return Snapped(
        Placement(
            x = if (sx) 0.5f else raw.x,
            y = if (sy) 0.5f else raw.y,
            size = if (ss) 1f else raw.size,
        ),
        sx, sy, ss,
    )
}

/**
 * Vorschau, in der die Uhr mit einem Finger verschoben und mit zwei Fingern skaliert wird.
 * In der Mitte (horizontal/vertikal) und bei 100 % rastet sie ein – mit Hilfslinie und Vibration.
 */
@Composable
fun ClockEditor(
    settings: ClockSettings,
    dark: Boolean,
    screen: Screen,
    ghostLabel: String,
    description: String,
    onPlacementChange: (Placement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentPlacement by rememberUpdatedState(settings.placement)
    val onChange by rememberUpdatedState(onPlacementChange)
    var dragging by remember { mutableStateOf(false) }
    var guideX by remember { mutableStateOf(false) }
    var guideY by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)
    val guideColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .heightIn(max = 460.dp)
            .aspectRatio(9f / 19.5f)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .semantics { contentDescription = description }
            .pointerInput(screen) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // Ungerastete Position mitführen, damit man aus dem Einrasten wieder herauskommt.
                    var raw = currentPlacement
                    var last = snap(raw)
                    dragging = true
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f || pan != Offset.Zero) {
                            raw = Placement(
                                x = raw.x + pan.x / size.width,
                                y = raw.y + pan.y / size.height,
                                size = raw.size * zoom,
                            ).clamped()
                            val s = snap(raw)
                            if ((s.x && !last.x) || (s.y && !last.y) || (s.size && !last.size)) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            guideX = s.x
                            guideY = s.y
                            last = s
                            onChange(s.placement)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    dragging = false
                    guideX = false
                    guideY = false
                }
            },
    ) {
        ClockCanvas(
            settings = settings,
            dark = dark,
            placement = settings.placement,
            systemClockGhost = if (screen == Screen.LOCK) ghostLabel else null,
            modifier = Modifier.matchParentSize(),
        )

        // Hilfslinien beim Einrasten
        Canvas(Modifier.matchParentSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            val w = 1.5.dp.toPx()
            if (guideX) {
                drawLine(guideColor, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), w, pathEffect = dash)
            }
            if (guideY) {
                drawLine(guideColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), w, pathEffect = dash)
            }
        }

        // Größe während des Ziehens anzeigen
        if (dragging) {
            Text(
                text = "${(settings.placement.size * 100).roundToInt()} %",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x99000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
