package com.beigel.binaryclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.beigel.binaryclock.ui.BinaryClockTheme
import com.beigel.binaryclock.ui.MainScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = appContainer.settingsRepository
        val reviews = appContainer.reviewPrompter
        setContent {
            BinaryClockTheme {
                MainScreen(repository = repository, onRate = { reviews.openStorePage(this) })
            }
        }
        if (savedInstanceState == null) {
            reviews.recordLaunch()
            lifecycleScope.launch {
                // Nicht direkt beim Öffnen stören.
                delay(2_000)
                reviews.maybeAsk(this@MainActivity)
            }
        }
    }
}
