package com.glassous.aimage.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.glassous.aimage.ui.navigation.AppNavigationDrawer
import com.glassous.aimage.SettingsActivity
import kotlinx.coroutines.launch
import com.glassous.aimage.data.ChatHistoryStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import com.glassous.aimage.oss.OssConfigStorage
import com.glassous.aimage.oss.AutoSyncNotifier
import com.glassous.aimage.oss.AutoSyncEvent
import com.glassous.aimage.oss.AutoSyncType
import okhttp3.OkHttpClient
import okhttp3.Request

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

    // 自动同步状态：订阅 OSS 配置与自动开关
    OssConfigStorage.ensureInitialized(context)
    val ossConfigured by OssConfigStorage.configFlow.collectAsState(initial = OssConfigStorage.load(context))
    val autoSyncEnabled by OssConfigStorage.autoSyncFlow.collectAsState(initial = OssConfigStorage.isAutoSyncEnabled(context))
    val configVersion by com.glassous.aimage.data.ModelConfigStorage.versionFlow.collectAsState(initial = 0L)
    var lastConfigVersionAfterStartup by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        val loaded = ChatHistoryStorage.loadAll(context)
        historyItems.clear()
        historyItems.addAll(loaded)
    }

    // 进入应用时自动从云端下载（一次），需已配置且开关开启
    LaunchedEffect(ossConfigured, autoSyncEnabled) {
        if (ossConfigured != null && autoSyncEnabled) {
            // 优先使用本地：此处不阻塞UI，只启动后台任务
            lastConfigVersionAfterStartup = configVersion
            // 异步下载模型配置（仅模型配置）
            scope.launch(Dispatchers.IO) {
                try {
                    com.glassous.aimage.oss.OssSyncManager.downloadModelConfigAsync(context)
                    // 模型配置下载成功后可选择上报事件，这里保持静默以减少多次提示
                } catch (_: Exception) { /* ignore */ }
            }
            // 异步查询ID并增量补齐缺失历史记录（避免一次性阻塞）
            scope.launch(Dispatchers.IO) {
                try {
                    com.glassous.aimage.oss.OssSyncManager.downloadMissingHistoryIncremental(context)
                    AutoSyncNotifier.report(AutoSyncEvent(AutoSyncType.DownloadOnStart, true))
                } catch (e: Exception) {
                    AutoSyncNotifier.report(AutoSyncEvent(AutoSyncType.DownloadOnStart, false))
                }
            }
        }
    }

    // 模型配置或默认模型变更时，自动上传（仅模型配置文件）
    LaunchedEffect(configVersion, ossConfigured, autoSyncEnabled) {
        if (ossConfigured != null && autoSyncEnabled) {
            val skipDueToStartup = lastConfigVersionAfterStartup != null && configVersion <= lastConfigVersionAfterStartup!!
            if (!skipDueToStartup) {
                try {
                    com.glassous.aimage.oss.OssSyncManager.syncModelConfig(context)
                    AutoSyncNotifier.report(AutoSyncEvent(AutoSyncType.UploadModelConfig, true))
                } catch (e: Exception) {
                    AutoSyncNotifier.report(AutoSyncEvent(AutoSyncType.UploadModelConfig, false))
                }
            }
        }
    }

    // 订阅历史记录流，确保云端下载或其他来源变更后侧边栏自动刷新
    val historyFromFlow by ChatHistoryStorage.historyFlow.collectAsState(initial = emptyList())
    LaunchedEffect(historyFromFlow) {
        historyItems.clear()
        historyItems.addAll(historyFromFlow)
    }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestTag by remember { mutableStateOf<String?>(null) }
    var latestBody by remember { mutableStateOf("") }
    var latestApkUrl by remember { mutableStateOf<String?>(null) }
    var currentVersionName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val pm = context.packageManager
            val flags = android.content.pm.PackageManager.PackageInfoFlags.of(0)
            val pkg = pm.getPackageInfo(context.packageName, flags)
            currentVersionName = pkg.versionName ?: ""
        } catch (_: Exception) { currentVersionName = "" }
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.github.com/repos/Glassous/AImage/releases/latest")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .addHeader("User-Agent", "AImage-Android")
                .build()
            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val json = org.json.JSONObject(bodyStr)
                    val tag = json.optString("tag_name").ifBlank { json.optString("name") }
                    latestTag = tag.takeIf { it.isNotBlank() }
                    latestBody = json.optString("body")
                    latestApkUrl = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.optJSONObject(i)
                            val name = a?.optString("name") ?: ""
                            val url = a?.optString("browser_download_url") ?: ""
                            if (name.endsWith(".apk") && url.isNotBlank()) {
                                latestApkUrl = url
                                break
                            }
                        }
                    }
                    val latest = latestTag ?: ""
                    fun parseVersion(s: String): List<Int> {
                        val t = s.trim().removePrefix("v").removePrefix("V")
                        return t.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
                    }
                    fun isNewer(a: String, b: String): Boolean {
                        val va = parseVersion(a)
                        val vb = parseVersion(b)
                        val n = kotlin.math.max(va.size, vb.size)
                        for (i in 0 until n) {
                            val ai = va.getOrElse(i) { 0 }
                            val bi = vb.getOrElse(i) { 0 }
                            if (ai != bi) return ai > bi
                        }
                        return false
                    }
                    if (latest.isNotBlank() && currentVersionName.isNotBlank() && isNewer(latest, currentVersionName)) {
                        showUpdateDialog = true
                    }
                }
            }
        } catch (_: Exception) { }
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
                onPromptAssistant = {
                    context.startActivity(Intent(context, com.glassous.aimage.PromptAssistantActivity::class.java))
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
                    // 云端自动同步开启时：删除后更新远端ID表并移除对应文件（后台线程防卡顿）
                    if (ossConfigured != null && autoSyncEnabled) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                com.glassous.aimage.oss.OssSyncManager.onHistoryDeleted(context, historyItem.id)
                                AutoSyncNotifier.report(AutoSyncEvent(AutoSyncType.UploadHistoryDelete, true))
                            } catch (e: Exception) {
                                AutoSyncNotifier.report(AutoSyncEvent(AutoSyncType.UploadHistoryDelete, false))
                            }
                        }
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
                    // 云端自动同步开启时：新增条目后上传并更新ID表（后台线程防卡顿）
                    if (ossConfigured != null && autoSyncEnabled) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                com.glassous.aimage.oss.OssSyncManager.onHistoryAdded(context, item)
                                AutoSyncNotifier.report(AutoSyncEvent(AutoSyncType.UploadHistoryAdd, true))
                            } catch (e: Exception) {
                                AutoSyncNotifier.report(AutoSyncEvent(AutoSyncType.UploadHistoryAdd, false))
                            }
                        }
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
        if (showUpdateDialog) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = { Text("发现新版本") },
                text = {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        val latest = latestTag ?: ""
                        Text("当前版本：${currentVersionName.ifBlank { "未知" }}", style = MaterialTheme.typography.bodyMedium)
                        Text("最新版本：${latest.ifBlank { "未知" }}", style = MaterialTheme.typography.bodyMedium)
                        if (latestBody.isNotBlank()) {
                            Text("更新内容：", style = MaterialTheme.typography.bodyMedium)
                            Text(latestBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val url = latestApkUrl ?: "https://github.com/Glassous/AImage/releases/latest"
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                        showUpdateDialog = false
                    }) { Text("下载更新") }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDialog = false }) { Text("稍后再说") }
                }
            )
        }
    }
}