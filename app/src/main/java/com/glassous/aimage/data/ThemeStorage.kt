package com.glassous.aimage.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { System, Light, Dark }

object ThemeStorage {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DEFAULT_ASPECT_RATIO = "default_aspect_ratio"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _themeModeFlow: MutableStateFlow<ThemeMode?> = MutableStateFlow(null)
    val themeModeFlow: StateFlow<ThemeMode?> = _themeModeFlow

    fun loadThemeMode(context: Context): ThemeMode {
        val raw = prefs(context).getString(KEY_THEME_MODE, ThemeMode.System.name) ?: ThemeMode.System.name
        return try {
            ThemeMode.valueOf(raw)
        } catch (_: Exception) {
            ThemeMode.System
        }
    }

    fun saveThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeModeFlow.value = mode
    }

    // 读取/保存用户选择的默认图片比例
    fun loadDefaultAspectRatio(context: Context): String {
        return prefs(context).getString(KEY_DEFAULT_ASPECT_RATIO, "SQUARE") ?: "SQUARE"
    }

    fun saveDefaultAspectRatio(context: Context, ratioName: String) {
        prefs(context).edit().putString(KEY_DEFAULT_ASPECT_RATIO, ratioName).apply()
    }

    // 初始化流的值，避免第一次 collectAsState 为空
    fun ensureInitialized(context: Context) {
        if (_themeModeFlow.value == null) {
            _themeModeFlow.value = loadThemeMode(context)
        }
    }
}