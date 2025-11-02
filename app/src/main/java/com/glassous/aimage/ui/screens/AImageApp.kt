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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest

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
    var newConversationToken by remember { mutableStateOf(0L) }
    // 独立图片预览改为 Activity 承载，不再使用内部状态切换

    LaunchedEffect(Unit) {
        val loaded = ChatHistoryStorage.loadAll(context)
        historyItems.clear()
        historyItems.addAll(loaded)
    }

    // 订阅历史记录流，确保云端下载或其他来源变更后侧边栏自动刷新
    val historyFromFlow by ChatHistoryStorage.historyFlow.collectAsState(initial = emptyList())
    LaunchedEffect(historyFromFlow) {
        historyItems.clear()
        historyItems.addAll(historyFromFlow)
    }

    // 处理返回键逻辑
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // 图片预览已独立为 Activity，无需处理其返回键

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
                    // 触发主页面清空
                    newConversationToken++
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
                    // 云端同步：删除后更新远端ID表并移除对应文件（后台线程防卡顿）
                    scope.launch(Dispatchers.IO) {
                        com.glassous.aimage.oss.OssSyncManager.onHistoryDeleted(context, historyItem.id)
                    }
                },
                activeHistoryIds = activeHistoryIds,
                historyItems = historyItems
            )
        },
        modifier = modifier
    ) {
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
                    // 云端同步：新增条目后上传并更新ID表（后台线程防卡顿）
                    scope.launch(Dispatchers.IO) {
                        com.glassous.aimage.oss.OssSyncManager.onHistoryAdded(context, item)
                    }
                },
                onImageClick = { url ->
                    // 跳转至独立的图片预览 Activity
                    val intent = android.content.Intent(context, com.glassous.aimage.ImagePreviewActivity::class.java)
                    intent.putExtra("imageUrl", url)
                    context.startActivity(intent)
                },
                historyItemToLoad = historyItemToLoad,
                newConversationToken = newConversationToken,
                modifier = Modifier.fillMaxSize()
            )
    }
}