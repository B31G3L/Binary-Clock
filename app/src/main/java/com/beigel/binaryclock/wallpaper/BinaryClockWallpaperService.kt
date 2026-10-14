package com.beigel.binaryclock.wallpaper

import android.app.KeyguardManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import com.beigel.binaryclock.appContainer
import com.beigel.binaryclock.clock.ClockRenderer
import com.beigel.binaryclock.clock.AppSettings
import com.beigel.binaryclock.clock.ClockSettings
import com.beigel.binaryclock.clock.Screen
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
 * ausblenden können. Deshalb kann die Binäruhr dort eine eigene Position und Größe haben
 * und lässt das Datum weg.
 */
class BinaryClockWallpaperService : WallpaperService() {

    private val engines = mutableSetOf<ClockEngine>()

    override fun onCreateEngine(): Engine = ClockEngine()

    /** Hell/Dunkel des Systems hat sich geändert. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        engines.forEach { it.onThemeChanged() }
    }

    private inner class ClockEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val renderer = ClockRenderer()
        private val keyguard = getSystemService(KeyguardManager::class.java)
        private var settings: AppSettings = applicationContext.appContainer.settingsRepository.settings.value
        private var visible = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var lastColorKey: Long? = null

        private val tick = object : Runnable {
            override fun run() {
                drawFrame()
                scheduleNext()
            }
        }

        /** Sperren/Entsperren: sofort im passenden Layout neu zeichnen. */
        private val lockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateColorsIfNeeded()
                drawFrame()
                // Die Keyguard-Animation dauert etwas – kurz danach noch einmal zeichnen.
                handler.postDelayed({ drawFrame() }, 400)
            }
        }
        private var receiverRegistered = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            engines += this
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
                    updateColorsIfNeeded()
                    if (visible) restartTicking() else drawFrame()
                }
            }
        }

        fun onThemeChanged() {
            updateColorsIfNeeded()
            drawFrame()
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
            engines -= this
            handler.removeCallbacksAndMessages(null)
            if (receiverRegistered) {
                unregisterReceiver(lockReceiver)
                receiverRegistered = false
            }
            scope.cancel()
            super.onDestroy()
        }

        /**
         * Teilt dem System die Hintergrundfarbe mit, damit Statusleiste und Sperrbildschirm-Uhr
         * bei hellen Stilen dunkle Schrift verwenden.
         */
        @RequiresApi(Build.VERSION_CODES.O_MR1)
        override fun onComputeColors(): WallpaperColors {
            val cfg = current()
            val dark = isDark(cfg)
            val bg = Color.valueOf(cfg.style.palette(dark).background)
            val accent = Color.valueOf(cfg.accent(dark))
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hints = if (dark) WallpaperColors.HINT_SUPPORTS_DARK_THEME else WallpaperColors.HINT_SUPPORTS_DARK_TEXT
                WallpaperColors(bg, accent, null, hints)
            } else {
                WallpaperColors(bg, accent, null)
            }
        }

        private fun updateColorsIfNeeded() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
            val cfg = current()
            val dark = isDark(cfg)
            val key = cfg.style.palette(dark).background.toLong() shl 32 or
                (cfg.accent(dark).toLong() and 0xFFFFFFFFL)
            if (key != lastColorKey) {
                lastColorKey = key
                notifyColorsChanged()
            }
        }

        private fun isDark(cfg: ClockSettings) = cfg.themeMode.isDark(this@BinaryClockWallpaperService)

        /** Einstellungen des Bildschirms, der gerade zu sehen ist. */
        private fun current(): ClockSettings = settings[if (onLockScreen()) Screen.LOCK else Screen.HOME]

        private fun restartTicking() {
            handler.removeCallbacks(tick)
            tick.run()
        }

        private fun scheduleNext() {
            if (!visible) return
            val now = System.currentTimeMillis()
            val period = if (current().showSeconds) 1_000L else 60_000L
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
            val cfg = current()
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
                    settings = cfg,
                    now = Calendar.getInstance(),
                    dark = isDark(cfg),
                    placement = cfg.placement,
                )
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
