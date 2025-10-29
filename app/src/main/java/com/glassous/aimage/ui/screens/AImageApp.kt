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
import com.glassous.aimage.data.ChatHistoryStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AImageApp(
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val historyItems = remember { mutableStateListOf<HistoryItem>() }
    val activeHistoryIds = remember { mutableStateListOf<String>() }
    var historyItemToLoad by remember { mutableStateOf<HistoryItem?>(null) }
    var imagePreviewUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val loaded = ChatHistoryStorage.loadAll(context)
        historyItems.clear()
        historyItems.addAll(loaded)
    }

    // 处理返回键逻辑
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // 在图片预览页按返回键时关闭预览页
    BackHandler(enabled = imagePreviewUrl != null) {
        imagePreviewUrl = null
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppNavigationDrawer(
                onNewConversation = {
                    scope.launch {
                        drawerState.close()
                    }
                    historyItemToLoad = null
                    // 清空侧边栏高亮，避免残留背景色
                    activeHistoryIds.clear()
                },
                onSettings = {
                    // 进入设置页时不关闭侧边栏，确保动画连贯
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                onHistoryItemClick = { historyItem ->
                    scope.launch {
                        drawerState.close()
                    }
                    // 加载历史记录到主界面
                    historyItemToLoad = historyItem
                    // 仅高亮当前浏览的记录
                    activeHistoryIds.clear()
                    activeHistoryIds.add(historyItem.id)
                },
                onDeleteHistoryItem = { historyItem ->
                    historyItems.removeAll { it.id == historyItem.id }
                    activeHistoryIds.remove(historyItem.id)
                    ChatHistoryStorage.saveAll(context, historyItems.toList())
                },
                activeHistoryIds = activeHistoryIds,
                historyItems = historyItems
            )
        },
        modifier = modifier
    ) {
        if (imagePreviewUrl == null) {
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
                onAddHistory = { item ->
                    historyItems.add(0, item)
                    // 使用快照副本避免潜在的并发修改问题
                    ChatHistoryStorage.saveAll(context, historyItems.toList())
                },
                onImageClick = { url ->
                    imagePreviewUrl = url
                },
                historyItemToLoad = historyItemToLoad,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ImagePreviewScreen(
                imageUrl = imagePreviewUrl!!,
                onBackClick = { imagePreviewUrl = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}