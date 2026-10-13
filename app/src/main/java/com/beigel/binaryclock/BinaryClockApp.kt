package com.beigel.binaryclock

import android.app.Application

class BinaryClockApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
