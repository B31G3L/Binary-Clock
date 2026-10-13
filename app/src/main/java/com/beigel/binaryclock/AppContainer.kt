package com.beigel.binaryclock

import android.content.Context
import com.beigel.binaryclock.data.SettingsRepository

/** Einfache manuelle DI. */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context.applicationContext)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as BinaryClockApp).container
