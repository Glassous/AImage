package com.glassous.aimage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.glassous.aimage.ui.screens.ImagePreviewScreen
import com.glassous.aimage.ui.theme.AImageTheme

class ImagePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent.getStringExtra("imageUrl") ?: ""
        setContent {
            AImageTheme {
                ImagePreviewScreen(
                    imageUrl = url,
                    onBackClick = { finish() }
                )
            }
        }
    }
}