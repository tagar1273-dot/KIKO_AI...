package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.core.configureAppCheck
import com.example.ui.KikoAppNavigation
import com.example.ui.theme.KikoAiTheme
import com.example.update.UpdateCheckWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        // Schedule periodic HTTPS background update checks via WorkManager
        UpdateCheckWorker.schedulePeriodicChecks(this)

        enableEdgeToEdge()
        setContent {
            KikoAiTheme {
                KikoAppNavigation()
            }
        }
    }
}
