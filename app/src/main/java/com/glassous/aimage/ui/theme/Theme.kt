package com.glassous.aimage.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AIPrimaryDark,
    onPrimary = AIOnPrimaryDark,
    primaryContainer = AIPrimaryContainerDark,
    onPrimaryContainer = AIOnPrimaryContainerDark,
    secondary = AISecondaryDark,
    onSecondary = AIOnSecondaryDark,
    secondaryContainer = AISecondaryContainerDark,
    onSecondaryContainer = AIOnSecondaryContainerDark,
    tertiary = AITertiaryDark,
    onTertiary = AIOnTertiaryDark,
    tertiaryContainer = AITertiaryContainerDark,
    onTertiaryContainer = AIOnTertiaryContainerDark,
    error = AIErrorDark,
    onError = AIOnErrorDark,
    errorContainer = AIErrorContainerDark,
    onErrorContainer = AIOnErrorContainerDark,
    background = AIBackgroundDark,
    onBackground = AIOnBackgroundDark,
    surface = AISurfaceDark,
    onSurface = AIOnSurfaceDark,
    surfaceVariant = AISurfaceVariantDark,
    onSurfaceVariant = AIOnSurfaceVariantDark,
    outline = AIOutlineDark,
    outlineVariant = AIOutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = AIPrimary,
    onPrimary = AIOnPrimary,
    primaryContainer = AIPrimaryContainer,
    onPrimaryContainer = AIOnPrimaryContainer,
    secondary = AISecondary,
    onSecondary = AIOnSecondary,
    secondaryContainer = AISecondaryContainer,
    onSecondaryContainer = AIOnSecondaryContainer,
    tertiary = AITertiary,
    onTertiary = AIOnTertiary,
    tertiaryContainer = AITertiaryContainer,
    onTertiaryContainer = AIOnTertiaryContainer,
    error = AIError,
    onError = AIOnError,
    errorContainer = AIErrorContainer,
    onErrorContainer = AIOnErrorContainer,
    background = AIBackground,
    onBackground = AIOnBackground,
    surface = AISurface,
    onSurface = AIOnSurface,
    surfaceVariant = AISurfaceVariant,
    onSurfaceVariant = AIOnSurfaceVariant,
    outline = AIOutline,
    outlineVariant = AIOutlineVariant
)

@Composable
fun AImageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}