package com.beigel.binaryclock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import com.beigel.binaryclock.MainActivity
import com.beigel.binaryclock.R
import com.beigel.binaryclock.appContainer
import com.beigel.binaryclock.clock.ClockRenderer
import java.util.Calendar
import kotlin.math.sqrt

/**
 * Homescreen-Widget. Android erlaubt Widgets kein Live-Rendering, daher wird die Uhr
 * als Bitmap gezeichnet und per Alarm jede volle Minute neu gesetzt.
 */
class BinaryClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.updateWidget(context, manager, it) }
        WidgetUpdater.scheduleNextTick(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetUpdater.updateWidget(context, manager, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.scheduleNextTick(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.cancelTicks(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            WidgetUpdater.ACTION_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                WidgetUpdater.updateAll(context)
                WidgetUpdater.scheduleNextTick(context)
            }
        }
    }
}

object WidgetUpdater {

    const val ACTION_TICK = "com.beigel.binaryclock.action.WIDGET_TICK"

    private val renderer = ClockRenderer()

    private fun widgetIds(context: Context, manager: AppWidgetManager): IntArray =
        manager.getAppWidgetIds(ComponentName(context, BinaryClockWidget::class.java))

    fun hasWidgets(context: Context): Boolean =
        widgetIds(context, AppWidgetManager.getInstance(context)).isNotEmpty()

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        widgetIds(context, manager).forEach { updateWidget(context, manager, it) }
    }

    fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val density = context.resources.displayMetrics.density
        val options = manager.getAppWidgetOptions(id)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 120

        var w = widthDp * density
        var h = heightDp * density
        // RemoteViews haben ein Bitmap-Speicherlimit – große Widgets runterskalieren.
        val pixels = w * h
        if (pixels > MAX_PIXELS) {
            val f = sqrt(MAX_PIXELS / pixels)
            w *= f
            h *= f
        }
        val scaleToPx = w / (widthDp * density)

        val bitmap = createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
        val settings = context.appContainer.settingsRepository.settings.value
            .copy(showSeconds = false)
        renderer.draw(
            canvas = Canvas(bitmap),
            width = bitmap.width.toFloat(),
            height = bitmap.height.toFloat(),
            settings = settings,
            now = Calendar.getInstance(),
            dark = settings.themeMode.isDark(context),
            cornerRadius = 24f * density * scaleToPx,
            maxScale = Float.MAX_VALUE,
        )

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_clock).apply {
            setImageViewBitmap(R.id.widget_image, bitmap)
            setOnClickPendingIntent(R.id.widget_root, openApp)
        }
        manager.updateAppWidget(id, views)
    }

    fun scheduleNextTick(context: Context) {
        if (!hasWidgets(context)) return
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val nextMinute = now - now % 60_000L + 60_000L
        val pending = tickIntent(context)
        // RTC (ohne WAKEUP): bei ausgeschaltetem Display wird nichts geweckt,
        // der Alarm kommt beim Einschalten nach.
        if (canScheduleExact(context)) {
            alarmManager.setExact(AlarmManager.RTC, nextMinute, pending)
        } else {
            alarmManager.setWindow(AlarmManager.RTC, nextMinute, 60_000L, pending)
        }
    }

    fun cancelTicks(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, BinaryClockWidget::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private const val MAX_PIXELS = 900_000f
}
