package com.glassous.aimage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.glassous.aimage.ui.screens.PromptParamsScreen
import com.glassous.aimage.ui.theme.AImageTheme

class PromptParamsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AImageTheme {
                PromptParamsScreen(onBackClick = { finish() })
            }
        }
    }
}