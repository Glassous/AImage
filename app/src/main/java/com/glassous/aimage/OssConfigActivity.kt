package com.glassous.aimage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.glassous.aimage.ui.screens.OssConfigScreen
import com.glassous.aimage.ui.theme.AImageTheme
import com.glassous.aimage.util.configureTransparentNavigationBar

class OssConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureTransparentNavigationBar()
        setContent {
            AImageTheme {
                OssConfigScreen(onBackClick = { finish() })
            }
        }
    }
}