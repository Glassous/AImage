package com.glassous.aimage.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.glassous.aimage.ui.navigation.AppNavigationDrawer
import com.glassous.aimage.SettingsActivity
import kotlinx.coroutines.launch

enum class Screen {
    Main,
    History
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AImageApp(
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.Main) }
    val context = LocalContext.current

    // 处理返回键逻辑
    BackHandler(enabled = drawerState.isOpen || currentScreen != Screen.Main) {
        when {
            drawerState.isOpen -> {
                scope.launch {
                    drawerState.close()
                }
            }
            currentScreen != Screen.Main -> {
                currentScreen = Screen.Main
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppNavigationDrawer(
                onNewConversation = {
                    scope.launch {
                        drawerState.close()
                    }
                    currentScreen = Screen.Main
                },
                onHistory = {
                    scope.launch {
                        drawerState.close()
                    }
                    currentScreen = Screen.History
                },
                onSettings = {
                    scope.launch { drawerState.close() }
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            )
        },
        modifier = modifier
    ) {
        when (currentScreen) {
            Screen.Main -> {
                MainScreen(
                    onMenuClick = {
                        scope.launch {
                            if (drawerState.isClosed) {
                                drawerState.open()
                            } else {
                                drawerState.close()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Screen.History -> {
                HistoryScreen(
                    onBackClick = {
                        currentScreen = Screen.Main
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}