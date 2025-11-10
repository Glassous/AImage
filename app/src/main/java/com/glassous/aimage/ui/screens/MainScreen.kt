@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.glassous.aimage.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import com.glassous.aimage.R
import com.glassous.aimage.data.ModelConfigStorage
import com.glassous.aimage.data.ThemeStorage
import com.glassous.aimage.api.PolishAIClient
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.animation.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import android.widget.Toast

// 聊天窗口数据结构
data class ChatWindow(
    val id: String = UUID.randomUUID().toString(),
    val modelRef: ModelConfigStorage.DefaultModelRef?,
    var inputText: String = "",
    var responseText: String = "",
    var imageUrl: String? = null,
    var isLoading: Boolean = false,
    var lastGeneratedTimestamp: String? = null
)

// 图片比例选项
enum class AspectRatio(val displayName: String, val ratio: Float) {
    SQUARE("1:1", 1f),
    PORTRAIT_3_4("3:4", 3f/4f),
    LANDSCAPE_4_3("4:3", 4f/3f),
    PORTRAIT_9_16("9:16", 9f/16f)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    onMenuClick: () -> Unit,
    onAddHistory: (HistoryItem) -> Unit,
    onImageClick: (String) -> Unit,
    historyItemToLoad: HistoryItem? = null,
    newConversationToken: Long = 0L,
    modifier: Modifier = Modifier
) {
    // 多窗口状态管理
    var chatWindows by remember { mutableStateOf(listOf<ChatWindow>()) }
    val pagerState = rememberPagerState(pageCount = { chatWindows.size })
    val scope = rememberCoroutineScope()
    
    // 原有状态变量（用于兼容性）
    var inputText by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showModelRequiredDialog by remember { mutableStateOf(false) }
    
    // 新增状态
    var showNewChatDialog by remember { mutableStateOf(false) }
    var selectedAspectRatio by remember { mutableStateOf(AspectRatio.SQUARE) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    
    var showSheet by remember { mutableStateOf(false) }
    // 右侧快捷栏显隐（提升到页面级，供顶部栏按钮调用）
    var showRightQuickBar by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val groupModels = remember { mutableStateMapOf<ModelGroupType, List<UserModel>>() }
    var defaultRef by remember { mutableStateOf<ModelConfigStorage.DefaultModelRef?>(null) }
    var currentRef by remember { mutableStateOf<ModelConfigStorage.DefaultModelRef?>(null) }

    // 翻译相关状态（复用提示词助写页的模型配置与流式输出）
    var showTranslateDialog by remember { mutableStateOf(false) }
    var translateStreaming by remember { mutableStateOf(false) }
    var translateResultText by remember { mutableStateOf("") }
    var translateHandle by remember { mutableStateOf<PolishAIClient.Handle?>(null) }
    var translateCfg by remember { mutableStateOf(PromptAIConfigStorage.load(context)) }
    ThemeStorage.ensureInitialized(context)
    val showTranslateFab by ThemeStorage.showTranslateFabFlow.collectAsState(initial = ThemeStorage.loadShowTranslateFab(context))

    fun startTranslateStreaming(source: String) {
        val cfg = translateCfg
        if (cfg == null) {
            Toast.makeText(context, "请先在提示词助写页配置AI模型", Toast.LENGTH_SHORT).show()
            return
        }
        val (flow, handle) = PolishAIClient.streamRefine(
            PolishAIClient.Config(cfg.baseUrl, cfg.apiKey, cfg.model),
            source,
            PolishAIClient.Mode.TranslateEnglish
        )
        translateHandle = handle
        translateStreaming = true
        translateResultText = ""
        scope.launch {
            flow.collect { event ->
                when (event) {
                    is PolishAIClient.Event.Chunk -> translateResultText += event.text
                    is PolishAIClient.Event.Error -> {
                        translateStreaming = false
                        Toast.makeText(context, "翻译失败：${event.message}", Toast.LENGTH_SHORT).show()
                    }
                    is PolishAIClient.Event.Completed -> translateStreaming = false
                }
            }
        }
    }
    fun cancelTranslateStreaming() {
        try { translateHandle?.cancel() } catch (_: Exception) {}
        translateStreaming = false
    }

    // 检查是否有可用模型
    val hasAvailableModel = (currentRef ?: defaultRef) != null || 
                           ModelGroupType.values().any { group -> 
                               val models = groupModels[group] ?: emptyList<UserModel>()
                               models.isNotEmpty()
                           }

    val keyboardController = LocalSoftwareKeyboardController.current

    fun labelFor(ref: ModelConfigStorage.DefaultModelRef?): String {
        if (ref == null) return if (groupModels.values.any { it.isNotEmpty() }) "点击选择模型" else "无可用模型"
        val list = groupModels[ref.group].orEmpty()
        val m = list.find { it.name == ref.modelName }
        val modelLabel = (m?.displayName?.takeIf { it.isNotBlank() } ?: ref.modelName).ifBlank { "(未命名)" }
        return "${ref.group.displayName} / $modelLabel"
    }

    // 获取已使用的模型
    fun getUsedModels(): Set<ModelConfigStorage.DefaultModelRef> {
        return chatWindows.mapNotNull { it.modelRef }.toSet()
    }

    // 创建新聊天窗口
    fun createNewChatWindow(modelRef: ModelConfigStorage.DefaultModelRef) {
        val newWindow = ChatWindow(modelRef = modelRef)
        chatWindows = chatWindows + newWindow
        scope.launch {
            pagerState.animateScrollToPage(chatWindows.size - 1)
        }
    }

    // 获取当前窗口
    fun getCurrentWindow(): ChatWindow? {
        return if (chatWindows.isNotEmpty() && pagerState.currentPage < chatWindows.size) {
            chatWindows[pagerState.currentPage]
        } else null
    }

    // 更新当前窗口
    fun updateCurrentWindow(update: (ChatWindow) -> ChatWindow) {
        val currentIndex = pagerState.currentPage
        if (currentIndex < chatWindows.size) {
            chatWindows = chatWindows.toMutableList().apply {
                this[currentIndex] = update(this[currentIndex])
            }
        }
    }

    // 根据窗口ID更新特定窗口
    fun updateWindowById(windowId: String, update: (ChatWindow) -> ChatWindow) {
        val windowIndex = chatWindows.indexOfFirst { it.id == windowId }
        if (windowIndex >= 0) {
            chatWindows = chatWindows.toMutableList().apply {
                this[windowIndex] = update(this[windowIndex])
            }
        }
    }

    // 关闭并移除指定窗口
    fun removeWindowById(windowId: String) {
        val removeIndex = chatWindows.indexOfFirst { it.id == windowId }
        if (removeIndex >= 0) {
            val newList = chatWindows.toMutableList().apply { removeAt(removeIndex) }
            chatWindows = newList
            scope.launch {
                if (newList.isNotEmpty()) {
                    val targetIndex = kotlin.math.min(removeIndex, newList.size - 1)
                    pagerState.animateScrollToPage(targetIndex)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        ModelGroupType.values().forEach { group ->
            groupModels[group] = ModelConfigStorage.loadModels(context, group)
        }
        defaultRef = ModelConfigStorage.loadDefaultModel(context)
        modelName = labelFor(currentRef ?: defaultRef)
        
        // 自动创建一个初始窗口，使用默认模型
        val initialModelRef = currentRef ?: defaultRef
        if (initialModelRef != null && chatWindows.isEmpty()) {
            createNewChatWindow(initialModelRef)
        }
    }

    // 订阅模型配置版本流：任意配置变化后主界面可用模型与默认模型自动刷新
    val configVersion by ModelConfigStorage.versionFlow.collectAsState(initial = 0L)
    LaunchedEffect(configVersion) {
        ModelGroupType.values().forEach { group ->
            groupModels[group] = ModelConfigStorage.loadModels(context, group)
        }
        defaultRef = ModelConfigStorage.loadDefaultModel(context)
        modelName = labelFor(currentRef ?: defaultRef)
    }

    // 加载历史记录项
    LaunchedEffect(historyItemToLoad) {
        historyItemToLoad?.let { item ->
            // 加载前先清空所有窗口，避免多个窗口加载同一条历史
            chatWindows = emptyList()

            // 根据历史记录设置模型引用
            val targetGroup = item.provider
            val models = groupModels[targetGroup] ?: emptyList()
            // 兼容历史记录中 model 字段为“<provider> / <displayName>”的情况
            val rawLabel = item.model
            val namePart = rawLabel.substringAfterLast('/') .trim()
            val candidates = listOf(rawLabel, namePart)
            val targetModel = models.find { model ->
                candidates.any { c ->
                    c.equals(model.displayName, ignoreCase = true) ||
                    c.equals(model.name, ignoreCase = true)
                }
            } ?: models.find { model ->
                val lc = rawLabel.lowercase()
                lc.contains(model.displayName.lowercase()) || lc.contains(model.name.lowercase())
            } ?: models.firstOrNull()
            
            // 无论是否找到匹配模型，都创建窗口显示历史内容
            val targetModelRef = targetModel?.let { ModelConfigStorage.DefaultModelRef(targetGroup, it.name) }
            val newWindow = ChatWindow(
                modelRef = targetModelRef,
                inputText = item.prompt,
                responseText = "",
                imageUrl = item.imageUrl,
                lastGeneratedTimestamp = item.timestamp
            )
            chatWindows = listOf(newWindow)
            scope.launch { pagerState.animateScrollToPage(0) }

            // 更新全局状态以保持兼容性
            currentRef = targetModelRef
            modelName = labelFor(currentRef)
            inputText = item.prompt
            responseText = ""
            imageUrl = item.imageUrl
        }
    }

    // 处理“新建对话”触发：清空主页面内容
    LaunchedEffect(newConversationToken) {
        if (newConversationToken != 0L) {
            // 清空所有聊天窗口，展示空状态页
            chatWindows = emptyList()
            // 重置兼容性状态，避免残留内容
            inputText = ""
            responseText = ""
            imageUrl = null
            isLoading = false
            showModelRequiredDialog = false
            // 其他 UI 辅助状态复位
            selectedAspectRatio = AspectRatio.SQUARE
            showAspectRatioMenu = false
            showSheet = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val currentWindow = getCurrentWindow()
                        val titleText = if (currentWindow == null) {
                            "AImage"
                        } else {
                            val ref = currentWindow.modelRef
                            val m = ref?.let { groupModels[it.group]?.find { um -> um.name == it.modelName } }
                            m?.displayName?.takeIf { it.isNotBlank() } ?: m?.name ?: "未选择模型"
                        }
                        TextButton(onClick = { if (getCurrentWindow() != null) showSheet = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        if (currentWindow != null) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { currentWindow.let { removeWindowById(it.id) } },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "关闭窗口",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        // 自动同步状态提示图标（仅自动触发时显示，成功为对勾，失败为叉号）
                        val event by com.glassous.aimage.oss.AutoSyncNotifier.events.collectAsState(initial = null)
                        var showIndicator by remember { mutableStateOf(false) }
                        var lastSuccess by remember { mutableStateOf(true) }
                        LaunchedEffect(event) {
                            if (event != null) {
                                lastSuccess = event!!.success
                                showIndicator = true
                                kotlinx.coroutines.delay(3000)
                                showIndicator = false
                            }
                        }
                        if (showIndicator) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (lastSuccess) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = if (lastSuccess) "自动同步成功" else "自动同步失败"
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "菜单"
                        )
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // 顶部栏不再显示快捷窗口图标，改为右侧快捷栏
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showNewChatDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "新建聊天窗口")
                        }
                        // 提示词助写入口按钮
                        val context = LocalContext.current
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    context.startActivity(android.content.Intent(context, com.glassous.aimage.PromptAssistantActivity::class.java))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "提示词助写")
                        }
                        // 呼出/关闭右侧快捷栏按钮（靠右，位于 + 号右侧）
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showRightQuickBar = !showRightQuickBar },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "切换快捷窗口栏")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        // 悬浮按钮改由内容层覆盖实现，以支持居左显示
        bottomBar = {
            // 当没有窗口时，隐藏底部输入区域
            if (chatWindows.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val navBarInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp + navBarInset),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = getCurrentWindow()?.inputText ?: "",
                            onValueChange = { newText ->
                                updateCurrentWindow { window ->
                                    window.copy(inputText = newText)
                                }
                            },
                            placeholder = { Text("描述您想要生成的图片...") },
                            modifier = Modifier
                                .weight(1f)
                                .animateContentSize(
                                    animationSpec = tween(
                                        durationMillis = 220,
                                        easing = FastOutSlowInEasing
                                    )
                                ),
                            enabled = getCurrentWindow()?.isLoading?.not() ?: true,
                            maxLines = 6,
                            minLines = 1,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            leadingIcon = {
                                // 图片比例选择按钮（内嵌在输入框内）
                                Surface(
                                    onClick = { showAspectRatioMenu = !showAspectRatioMenu },
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .wrapContentSize(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    Text(
                                        text = when (selectedAspectRatio) {
                                            AspectRatio.SQUARE -> "1:1"
                                            AspectRatio.PORTRAIT_3_4 -> "3:4"
                                            AspectRatio.LANDSCAPE_4_3 -> "4:3"
                                            AspectRatio.PORTRAIT_9_16 -> "9:16"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            },
                        trailingIcon = {
                            if (chatWindows.size >= 2) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 上一窗口（向上）
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(enabled = pagerState.currentPage > 0) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowUp,
                                            contentDescription = "上一窗口",
                                            tint = if (pagerState.currentPage > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    // 下一窗口（向下）
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(enabled = pagerState.currentPage < chatWindows.size - 1) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "下一窗口",
                                            tint = if (pagerState.currentPage < chatWindows.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                    // 当图片正在生成时，隐藏发送按钮；仅在当前窗口非加载且输入不为空时显示
                    val showSendButton = (getCurrentWindow()?.isLoading != true) && (getCurrentWindow()?.inputText?.isNotBlank() == true)
                    AnimatedVisibility(
                        visible = showSendButton,
                        enter =
                            fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                            scaleIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing), initialScale = 0.9f) +
                            slideInHorizontally(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing), initialOffsetX = { it / 4 }),
                        exit =
                            fadeOut(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                            scaleOut(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing), targetScale = 0.9f) +
                            slideOutHorizontally(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing), targetOffsetX = { it / 4 })
                    ) {
                        FloatingActionButton(
                        onClick = {
                            // 没有聊天窗口则提示创建
                            if (chatWindows.isEmpty()) {
                                showNewChatDialog = true
                                return@FloatingActionButton
                            }

                            val currentWindow = getCurrentWindow()
                            val broadcastInput = currentWindow?.inputText?.trim() ?: ""

                            // 只有当有输入时才广播发送
                            if (broadcastInput.isBlank()) {
                                return@FloatingActionButton
                            }

                            // 快照当前窗口列表，避免遍历时状态突变
                            val windowsSnapshot = chatWindows.toList()
                            val windowsWithModel = windowsSnapshot.filter { it.modelRef != null }
                            val windowsWithoutModel = windowsSnapshot.filter { it.modelRef == null }

                            // 如果全部窗口都未设置模型，提示先选择模型
                            if (windowsWithModel.isEmpty()) {
                                showSheet = true
                                return@FloatingActionButton
                            }

                            try {
                                // 设置所有已配置模型的窗口进入加载状态，并写入广播输入
                                windowsWithModel.forEach { w ->
                                    updateWindowById(w.id) { window ->
                                        window.copy(
                                            inputText = broadcastInput,
                                            responseText = "",
                                            imageUrl = null,
                                            isLoading = true
                                        )
                                    }
                                }

                                // 隐藏键盘
                                keyboardController?.hide()

                                // 并发调用各窗口对应模型的API
                                windowsWithModel.forEach { w ->
                                    val targetWindowId = w.id
                                    val targetModelRef = w.modelRef!!
                                    scope.launch {
                                        val modelLabel = labelFor(targetModelRef)
                                        val apiResponse = com.glassous.aimage.api.ApiService.generateImage(
                                            provider = targetModelRef.group,
                                            modelName = targetModelRef.modelName,
                                            prompt = broadcastInput,
                                            aspectRatio = selectedAspectRatio.displayName,
                                            context = context
                                        )

                                        if (apiResponse.success) {
                                            val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())
                                            updateWindowById(targetWindowId) { window ->
                                                window.copy(
                                                    imageUrl = apiResponse.imageUrl,
                                                    responseText = apiResponse.responseText,
                                                    isLoading = false,
                                                    lastGeneratedTimestamp = ts
                                                )
                                            }

                                            // 分别记录每个窗口的历史
                                            val provider = targetModelRef.group
                                            val item = HistoryItem(
                                                id = System.currentTimeMillis().toString(),
                                                prompt = broadcastInput,
                                                imageUrl = apiResponse.imageUrl,
                                                timestamp = ts,
                                                model = modelLabel,
                                                provider = provider
                                            )
                                            onAddHistory(item)
                                        } else {
                                            updateWindowById(targetWindowId) { window ->
                                                window.copy(
                                                    responseText = "发送失败：" + (apiResponse.errorMessage ?: "未知错误"),
                                                    isLoading = false
                                                )
                                            }
                                        }
                                    }
                                }

                                // 对未设置模型的窗口进行提示（不阻断已发送的窗口）
                                if (windowsWithoutModel.isNotEmpty()) {
                                    showModelRequiredDialog = true
                                }
                            } catch (e: Exception) {
                                // 广播发送的异常兜底：反馈到当前窗口
                                if (currentWindow != null) {
                                    updateWindowById(currentWindow.id) { window ->
                                        window.copy(
                                            responseText = "发送失败：" + (e.message ?: "未知错误"),
                                            isLoading = false
                                        )
                                    }
                                }
                            }
                        },
                        containerColor = if (chatWindows.isNotEmpty() && chatWindows.any { it.modelRef != null }) 
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        contentColor = if (chatWindows.isNotEmpty() && chatWindows.any { it.modelRef != null }) 
                            MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp
                        ),
                        modifier = Modifier.size(56.dp)
                        ) {
                        val currentWindow = getCurrentWindow()
                        if (currentWindow?.isLoading == true) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送"
                            )
                        }
                        }
                        }
                    }
                }
            }
            }
        }
    ) { paddingValues ->
        // 将内容区域包裹在窗口级 Box 中，方便在其上层叠右侧固定快捷栏
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (chatWindows.isEmpty()) {
                // 空状态 - 显示欢迎界面
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "欢迎使用 AImage",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "点击右上角的 + 按钮创建新的聊天窗口",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            } else {
                // 多窗口内容区域（改为上下滑动切换窗口）
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                ) { pageIndex ->
                val window = chatWindows[pageIndex]
                // 在可组合作用域中获取密度并转换阈值，避免在pointerInput中访问LocalDensity.current
                val thresholdPx = with(LocalDensity.current) { 32.dp.toPx() }
                val horizontalThresholdPx = with(LocalDensity.current) { 28.dp.toPx() }
                val rightEdgePx = with(LocalDensity.current) { 72.dp.toPx() }
                Box(modifier = Modifier.fillMaxSize()) {
                    ChatWindowContent(
                        window = window,
                        onModelClick = { showSheet = true },
                        onImageClick = onImageClick,
                        onCloseClick = { removeWindowById(window.id) },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 右侧窄手势区：仅在右侧栏展开时启用，收缩时不拦截手势
                    if (showRightQuickBar) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(64.dp)
                                .align(Alignment.CenterEnd)
                                .pointerInput(pagerState.currentPage, chatWindows.size, thresholdPx) {
                                    var dyAcc = 0f
                                    detectDragGestures(
                                        onDragStart = { dyAcc = 0f },
                                        onDrag = { change, dragAmount ->
                                            dyAcc += dragAmount.y
                                            if (dyAcc <= -thresholdPx) {
                                                if (pagerState.currentPage < chatWindows.size - 1) {
                                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                                }
                                                dyAcc = 0f
                                                change.consume()
                                            } else if (dyAcc >= thresholdPx) {
                                                if (pagerState.currentPage > 0) {
                                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                                }
                                                dyAcc = 0f
                                                change.consume()
                                            }
                                        }
                                    )
                                }
                        )
                    }

                    // 已移除左滑呼出：仅保留顶部按钮打开，以及点击非侧栏区域关闭
                }
            }
            }

            // 将右侧快捷切换栏外移到窗口级覆盖层，避免随窗口内容切换移动
            AnimatedVisibility(
                visible = showRightQuickBar,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 })
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 点击非侧边栏区域关闭
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { showRightQuickBar = false }
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(72.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                        tonalElevation = 2.dp
                    ) {
                        // 图标列表
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            chatWindows.forEachIndexed { idx, w ->
                                val logo = w.modelRef?.group?.logoRes()
                                val selected = idx == pagerState.currentPage
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            scope.launch { pagerState.animateScrollToPage(idx) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // 生成完成标识：在logo后方叠加绿色背景
                                    if (!w.isLoading && w.imageUrl != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF4CAF50).copy(alpha = 0.22f))
                                        )
                                    }
                                    // 在图标背面显示生成动画（仅当前窗口正在生成时）
                                    if (w.isLoading) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(36.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            strokeWidth = 3.dp
                                        )
                                    }
                                    if (logo != null) {
                                        Image(
                                            painter = painterResource(id = logo),
                                            contentDescription = "跳转窗口${idx + 1}",
                                            modifier = Modifier.size(28.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "未设置模型",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            // 新建窗口按钮（点击呼出，再次点击顶部按钮可关闭）
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .clickable { showNewChatDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "新建聊天窗口",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 页面左下角的悬浮英译按钮（不与输入框同容器，背后透明），受设置页开关控制 + 输入为空隐藏
            val showTranslateButton = showTranslateFab == true && getCurrentWindow()?.inputText?.isNotBlank() == true
            if (showTranslateButton) {
                SmallFloatingActionButton(
                    onClick = {
                        val source = getCurrentWindow()?.inputText?.trim().orEmpty()
                        if (source.isBlank()) {
                            Toast.makeText(context, "请输入内容后再翻译", Toast.LENGTH_SHORT).show()
                        } else {
                            showTranslateDialog = true
                            startTranslateStreaming(source)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    if (translateStreaming) {
                        LoadingIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
                        )
                    } else {
                        Icon(imageVector = Icons.Filled.Language, contentDescription = "一键英译")
                    }
                }
            }
        }
    }

    // 英译结果弹窗（支持流式输出、取消、重新翻译、插入）
    if (showTranslateDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {
            cancelTranslateStreaming(); showTranslateDialog = false
        }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "英文翻译结果",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (translateStreaming) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = translateResultText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                cancelTranslateStreaming()
                                val source = getCurrentWindow()?.inputText?.trim().orEmpty()
                                if (source.isNotBlank()) startTranslateStreaming(source)
                            }) { Text("重新翻译") }
                            TextButton(onClick = {
                                updateCurrentWindow { w -> w.copy(inputText = translateResultText) }
                                cancelTranslateStreaming(); showTranslateDialog = false
                            }) { Text("插入") }
                        }
                        TextButton(onClick = {
                            cancelTranslateStreaming(); showTranslateDialog = false
                        }) { Text("取消") }
                    }
                }
            }
        }
    }

    if (showSheet) {
        // 每次打开弹窗时重新读取数据
        LaunchedEffect(showSheet) {
            if (showSheet) {
                ModelGroupType.values().forEach { group ->
                    groupModels[group] = ModelConfigStorage.loadModels(context, group)
                }
                defaultRef = ModelConfigStorage.loadDefaultModel(context)
                modelName = labelFor(currentRef ?: defaultRef)
            }
        }
        
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            val availableGroups = ModelGroupType.values().filter { (groupModels[it] ?: emptyList()).isNotEmpty() }
            if (availableGroups.isEmpty()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("暂无可选模型，请前往‘设置 > 模型配置’添加。")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(availableGroups.size) { idx ->
                        val group = availableGroups[idx]
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = group.logoRes()),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp) // 限制logo大小
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(group.displayName, style = MaterialTheme.typography.titleSmall)
                            }
                            val list = groupModels[group].orEmpty()
                            list.forEach { m ->
                                val currentWindow = getCurrentWindow()
                                val isSelected = currentWindow?.modelRef?.let { 
                                    it.group == group && it.modelName == m.name 
                                } ?: false
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = isSelected, onClick = {
                                        val newModelRef = ModelConfigStorage.DefaultModelRef(group, m.name)
                                        updateCurrentWindow { window ->
                                            window.copy(modelRef = newModelRef)
                                        }
                                        showSheet = false
                                    })
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        val title = if (m.displayName.isNotBlank()) m.displayName else m.name
                                        Text(title, style = MaterialTheme.typography.bodyMedium)
                                        if (m.displayName.isNotBlank() && m.name.isNotBlank()) {
                                            Text("模型名称：${m.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // 新建聊天窗口的模型选择对话框
    if (showNewChatDialog) {
        LaunchedEffect(showNewChatDialog) {
            if (showNewChatDialog) {
                ModelGroupType.values().forEach { group ->
                    groupModels[group] = ModelConfigStorage.loadModels(context, group)
                }
            }
        }
        
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text("选择模型") },
            text = {
                val usedModels = getUsedModels()
                val availableGroups = ModelGroupType.values().filter { 
                    val models = groupModels[it] ?: emptyList()
                    models.any { model -> 
                        !usedModels.contains(ModelConfigStorage.DefaultModelRef(it, model.name))
                    }
                }
                
                if (availableGroups.isEmpty()) {
                    Text("暂无可用模型，所有模型都已在使用中或未配置。")
                } else {
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableGroups.size) { idx ->
                            val group = availableGroups[idx]
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = group.logoRes()),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(group.displayName, style = MaterialTheme.typography.titleSmall)
                                }
                                val list = groupModels[group].orEmpty()
                                list.forEach { m ->
                                    val modelRef = ModelConfigStorage.DefaultModelRef(group, m.name)
                                    if (!usedModels.contains(modelRef)) {
                                        TextButton(
                                            onClick = {
                                                createNewChatWindow(modelRef)
                                                showNewChatDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Start
                                            ) {
                                                val title = if (m.displayName.isNotBlank()) m.displayName else m.name
                                                Text(title, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 模型选择提示对话框
    if (showModelRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showModelRequiredDialog = false },
            title = { Text("请先选择模型") },
            text = { Text("请先选择一个模型后再发送消息。") },
            confirmButton = {
                TextButton(onClick = { showModelRequiredDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
    
    // 图片比例选择菜单
    if (showAspectRatioMenu) {
        AlertDialog(
            onDismissRequest = { showAspectRatioMenu = false },
            title = { Text("选择图片比例") },
            text = {
                Column {
                    AspectRatio.values().forEach { ratio ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAspectRatio = ratio
                                    showAspectRatioMenu = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAspectRatio == ratio,
                                onClick = {
                                    selectedAspectRatio = ratio
                                    showAspectRatioMenu = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (ratio) {
                                    AspectRatio.SQUARE -> "1:1 (正方形)"
                                    AspectRatio.PORTRAIT_3_4 -> "3:4 (竖屏)"
                                    AspectRatio.LANDSCAPE_4_3 -> "4:3 (横屏)"
                                    AspectRatio.PORTRAIT_9_16 -> "9:16 (手机竖屏)"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAspectRatioMenu = false }) {
                    Text("确定")
                }
            }
        )
    }
    
    // 添加上下滑动手势处理
    LaunchedEffect(pagerState.currentPage) {
        // 当页面改变时的处理逻辑
    }
}

@Composable
fun ChatWindowContent(
    window: ChatWindow,
    onModelClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            // 模型选择卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onModelClick
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val logo = window.modelRef?.group?.logoRes()
                    if (logo != null) {
                        Image(
                            painter = painterResource(id = logo),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "当前模型",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = window.modelRef?.let { ref ->
                                "${ref.group.displayName} / ${ref.modelName}"
                            } ?: "未选择模型",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = onCloseClick) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭窗口"
                        )
                    }
                }
            }
        }
        
        // 显示生成的图片（移动到文字回复之前）
        window.imageUrl?.let { url ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 根据图片实际比例动态调整显示比例
                        var imageAspect by remember(url) { mutableStateOf<Float?>(null) }
                        
                        AsyncImage(
                            model = url,
                            contentDescription = "生成的图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { base -> if (imageAspect != null) base.aspectRatio(imageAspect!!) else base }
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onImageClick(url) },
                            contentScale = ContentScale.Fit,
                            onSuccess = { success ->
                                val d = success.result.drawable
                                val w = d.intrinsicWidth
                                val h = d.intrinsicHeight
                                if (w > 0 && h > 0) {
                                    imageAspect = w.toFloat() / h.toFloat()
                                }
                            }
                        )

                        // 模型与时间信息
                        val modelText = window.modelRef?.let { ref ->
                            "模型：${ref.group.displayName} / ${ref.modelName}"
                        } ?: "模型：未选择"
                        val timeText = window.lastGeneratedTimestamp?.let { "时间：$it" } ?: ""
                        if (timeText.isNotEmpty()) {
                            Text(
                                text = "$modelText  |  $timeText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Text(
                                text = modelText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        // 按需求移除图片框中的“生成说明”文本
                    }
                }
            }
        }

        // 文本回复（AI文字部分）移到图片框下方
        if (window.responseText.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val clipboardManager = LocalClipboardManager.current
                        val context = LocalContext.current
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(window.responseText))
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "复制文本"
                                )
                            }
                        }
                        Text(
                            text = window.responseText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        
        // 加载状态
        if (window.isLoading) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LoadingIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
                            )
                            Text(
                                text = "正在生成图片...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
    }
    // 空状态覆盖层（全屏居中显示）
    if (!window.isLoading && window.imageUrl == null && window.inputText.isBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "开始创作",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "在下方输入框中描述您想要生成的图片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
    }
}

// 仅用于本文件的图标映射
private fun ModelGroupType.logoRes(): Int = when (this) {
    ModelGroupType.Google -> R.drawable.gemini
    ModelGroupType.Doubao -> R.drawable.doubao
    ModelGroupType.Qwen -> R.drawable.qwen
    ModelGroupType.MiniMax -> R.drawable.minimax
    ModelGroupType.OpenRouter -> R.drawable.openrouter
}