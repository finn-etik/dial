package com.field.dial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 enforces edge-to-edge on Android 15; DialApp pads content
        // with WindowInsets.systemBars so nothing draws under the status bar
        enableEdgeToEdge()
        setContent { DialApp() }
    }
}
