package com.beigel.binaryclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.beigel.binaryclock.ui.BinaryClockTheme
import com.beigel.binaryclock.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = appContainer.settingsRepository
        setContent {
            BinaryClockTheme {
                MainScreen(repository = repository)
            }
        }
    }
}
