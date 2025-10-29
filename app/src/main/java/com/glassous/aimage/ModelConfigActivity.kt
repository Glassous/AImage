package com.glassous.aimage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.glassous.aimage.ui.screens.ModelConfigScreen
import com.glassous.aimage.ui.theme.AImageTheme

class ModelConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AImageTheme {
                ModelConfigScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}