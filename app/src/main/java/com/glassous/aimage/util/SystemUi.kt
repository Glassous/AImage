package com.glassous.aimage.util

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

/**
 * Configure the window to allow content to lay out behind a fully transparent
 * system navigation bar while keeping native navigation functional.
 */
fun ComponentActivity.configureTransparentNavigationBar() {
    // Edge-to-edge content: do not fit system windows
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // Ensure navigation bar is translucent/transparent
    @Suppress("DEPRECATION")
    window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    window.navigationBarColor = Color.TRANSPARENT

    // Lay out content behind navigation and status bars
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

    // Modern controller: keep bars visible and allow swipe-to-show behavior
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller: WindowInsetsController? = window.insetsController
        controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Do not force light/dark nav icons here; respect system defaults
    }
}