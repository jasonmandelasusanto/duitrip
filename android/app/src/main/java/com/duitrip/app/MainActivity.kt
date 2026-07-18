package com.duitrip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.duitrip.app.ui.DuitripRoot
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.theme.DuitripTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DuitripApp).container
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                DuitripTheme {
                    DuitripRoot()
                }
            }
        }
    }
}
