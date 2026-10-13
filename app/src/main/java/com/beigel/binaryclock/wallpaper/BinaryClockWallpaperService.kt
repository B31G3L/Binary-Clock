package com.beigel.binaryclock.wallpaper

import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.beigel.binaryclock.appContainer
import com.beigel.binaryclock.clock.ClockRenderer
import com.beigel.binaryclock.clock.ClockSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Live Wallpaper: zeichnet die Uhr jede Sekunde (bzw. jede Minute ohne Sekundenanzeige)
 * neu, aber nur solange der Hintergrund sichtbar ist.
 */
class BinaryClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    private inner class ClockEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val renderer = ClockRenderer()
        private var settings: ClockSettings = applicationContext.appContainer.settingsRepository.settings.value
        private var visible = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0

        private val tick = object : Runnable {
            override fun run() {
                drawFrame()
                scheduleNext()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            scope.launch {
                applicationContext.appContainer.settingsRepository.settings.collect {
                    settings = it
                    if (visible) restartTicking()
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) restartTicking() else handler.removeCallbacks(tick)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(tick)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick)
            scope.cancel()
            super.onDestroy()
        }

        private fun restartTicking() {
            handler.removeCallbacks(tick)
            tick.run()
        }

        private fun scheduleNext() {
            if (!visible) return
            val now = System.currentTimeMillis()
            val period = if (settings.showSeconds) 1_000L else 60_000L
            // Auf die nächste volle Sekunde/Minute ausrichten, damit nichts „nachhinkt“.
            handler.postDelayed(tick, period - now % period + 5)
        }

        private fun drawFrame() {
            if (surfaceWidth == 0 || surfaceHeight == 0) return
            val holder = surfaceHolder
            val canvas = try {
                holder.lockCanvas()
            } catch (e: IllegalStateException) {
                null
            } ?: return
            try {
                renderer.draw(
                    canvas = canvas,
                    width = surfaceWidth.toFloat(),
                    height = surfaceHeight.toFloat(),
                    settings = settings,
                    now = Calendar.getInstance(),
                    centerYFraction = 0.45f,
                )
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
