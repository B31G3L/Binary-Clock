package com.beigel.binaryclock.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview

/**
 * Bittet um eine Bewertung über die In-App-Review-Funktion von Google Play.
 *
 * Gefragt wird erst, wenn die App mehrfach geöffnet wurde und schon ein paar Tage installiert
 * ist, und danach höchstens alle paar Monate. Ob der Dialog tatsächlich erscheint, entscheidet
 * Google Play selbst (Kontingent pro Nutzer) – die App erfährt das nicht.
 */
class ReviewPrompter(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Bei jedem Start der App aufrufen. */
    fun recordLaunch() {
        prefs.edit {
            if (!prefs.contains(KEY_FIRST_LAUNCH)) putLong(KEY_FIRST_LAUNCH, System.currentTimeMillis())
            putInt(KEY_LAUNCHES, prefs.getInt(KEY_LAUNCHES, 0) + 1)
        }
    }

    private fun shouldAsk(): Boolean {
        val now = System.currentTimeMillis()
        val launches = prefs.getInt(KEY_LAUNCHES, 0)
        val first = prefs.getLong(KEY_FIRST_LAUNCH, now)
        val last = prefs.getLong(KEY_LAST_PROMPT, 0L)
        return launches >= MIN_LAUNCHES &&
            now - first >= MIN_INSTALLED_MS &&
            (last == 0L || now - last >= REPROMPT_MS)
    }

    /** Zeigt – wenn die Bedingungen passen – den Bewertungsdialog von Google Play. */
    suspend fun maybeAsk(activity: Activity) {
        if (!shouldAsk()) return
        prefs.edit { putLong(KEY_LAST_PROMPT, System.currentTimeMillis()) }
        try {
            val manager = ReviewManagerFactory.create(activity)
            val info = manager.requestReview()
            manager.launchReview(activity, info)
        } catch (_: Exception) {
            // Kein Play Store, offline oder Kontingent erschöpft – dann eben nicht.
        }
    }

    /** Öffnet die Store-Seite direkt (für den dauerhaften „App bewerten“-Eintrag). */
    fun openStorePage(context: Context) {
        val pkg = context.packageName
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "review"
        const val KEY_LAUNCHES = "launches"
        const val KEY_FIRST_LAUNCH = "first_launch"
        const val KEY_LAST_PROMPT = "last_prompt"

        const val MIN_LAUNCHES = 3
        const val MIN_INSTALLED_MS = 2L * 24 * 60 * 60 * 1000 // 2 Tage
        const val REPROMPT_MS = 90L * 24 * 60 * 60 * 1000 // 90 Tage
    }
}
