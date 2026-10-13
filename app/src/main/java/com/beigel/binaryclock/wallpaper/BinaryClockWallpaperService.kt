package com.beigel.binaryclock.wallpaper

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
 *
 * Auf dem Sperrbildschirm zeichnet Android seine eigene Uhr darüber, die wir nicht
 * ausblenden können. Deshalb rückt die Binäruhr dort (optional) nach unten und
 * lässt das Datum weg.
 */
class BinaryClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    private inner class ClockEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val renderer = ClockRenderer()
        private val keyguard = getSystemService(KeyguardManager::class.java)
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

        /** Sperren/Entsperren: sofort im passenden Layout neu zeichnen. */
        private val lockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                drawFrame()
                // Die Keyguard-Animation dauert etwas – kurz danach noch einmal zeichnen.
                handler.postDelayed({ drawFrame() }, 400)
            }
        }
        private var receiverRegistered = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(lockReceiver, filter)
            receiverRegistered = true
            scope.launch {
                applicationContext.appContainer.settingsRepository.settings.collect {
                    settings = it
                    if (visible) restartTicking() else drawFrame()
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
            handler.removeCallbacksAndMessages(null)
            if (receiverRegistered) {
                unregisterReceiver(lockReceiver)
                receiverRegistered = false
            }
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

        /** Wird gerade der Sperrbildschirm gezeigt (bzw. ist dies das Sperrbildschirm-Wallpaper)? */
        private fun onLockScreen(): Boolean {
            if (isPreview) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Ab Android 14 kann es getrennte Instanzen für Home und Sperrbildschirm geben.
                when (wallpaperFlags) {
                    WallpaperManager.FLAG_LOCK -> return true
                    WallpaperManager.FLAG_SYSTEM -> return false
                }
            }
            return keyguard?.isKeyguardLocked == true
        }

        private fun drawFrame() {
            if (surfaceWidth == 0 || surfaceHeight == 0) return
            val lock = settings.lockScreenAdjust && onLockScreen()
            val holder = surfaceHolder
            val canvas = try {
                holder.lockCanvas()
            } catch (e: IllegalStateException) {
                null
            } ?: return
            try {
                val w = surfaceWidth.toFloat()
                renderer.draw(
                    canvas = canvas,
                    width = w,
                    height = surfaceHeight.toFloat(),
                    settings = if (lock) settings.copy(showDate = false) else settings,
                    now = Calendar.getInstance(),
                    // Sperrbildschirm: unter die Systemuhr, etwas kleiner.
                    centerYFraction = if (lock) 0.66f else 0.45f,
                    maxScale = if (lock) w / 390f * 0.8f else w / 390f,
                )
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
