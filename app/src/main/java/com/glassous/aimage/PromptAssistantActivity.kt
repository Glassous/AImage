package com.glassous.aimage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.glassous.aimage.ui.screens.PromptAssistantScreen
import com.glassous.aimage.ui.theme.AImageTheme

class PromptAssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AImageTheme {
                PromptAssistantScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}