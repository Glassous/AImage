package com.glassous.aimage.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.glassous.aimage.data.PromptParamsStorage
import com.glassous.aimage.PromptParamsActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.glassous.aimage.api.PolishAIClient
import org.json.JSONObject

// AI 配置数据与存储（顶层定义，避免局部 object 报错）
data class AIConfig(val baseUrl: String, val apiKey: String, val model: String)
object PromptAIConfigStorage {
    private const val PREF_NAME = "PromptAIConfig"
    private const val KEY_BASE = "ai_base_url"
    private const val KEY_KEY = "ai_api_key"
    private const val KEY_MODEL = "ai_model"
    fun load(ctx: android.content.Context): AIConfig? {
        val p = ctx.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        val base = p.getString(KEY_BASE, "").orEmpty()
        val key = p.getString(KEY_KEY, "").orEmpty()
        val model = p.getString(KEY_MODEL, "").orEmpty()
        return if (base.isNotBlank() && key.isNotBlank() && model.isNotBlank()) AIConfig(base, key, model) else null
    }
    fun save(ctx: android.content.Context, cfg: AIConfig) {
        ctx.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE, cfg.baseUrl)
            .putString(KEY_KEY, cfg.apiKey)
            .putString(KEY_MODEL, cfg.model)
            .apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptAssistantScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inspiration by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabledCount by remember { mutableStateOf(PromptParamsStorage.enabledCount(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabledCount = PromptParamsStorage.enabledCount(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }


    var cfgState by remember { mutableStateOf<AIConfig?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    var currentHandle by remember { mutableStateOf<PolishAIClient.Handle?>(null) }

    LaunchedEffect(Unit) {
        cfgState = PromptAIConfigStorage.load(context)
    }

    fun startStreaming(source: String, mode: PolishAIClient.Mode) {
        val cfg = cfgState ?: return
        val (flow, handle) = PolishAIClient.streamRefine(
            PolishAIClient.Config(cfg.baseUrl, cfg.apiKey, cfg.model),
            source,
            mode
        )
        currentHandle = handle
        streaming = true
        resultText = ""
        scope.launch {
            flow.collect { event ->
                when (event) {
                    is PolishAIClient.Event.Chunk -> resultText += event.text
                    is PolishAIClient.Event.Error -> {
                        streaming = false
                        scope.launch { snackbarHostState.showSnackbar(event.message) }
                    }
                    is PolishAIClient.Event.Completed -> streaming = false
                }
            }
        }
    }

    data class ParamDef(
        val name: String,
        val suggestions: List<String> = emptyList()
    )
    var paramDefs by remember { mutableStateOf<List<ParamDef>>(emptyList()) }
    LaunchedEffect(Unit) {
        val stored = PromptParamsStorage.loadParamDefs(context)
        paramDefs = stored.map { ParamDef(it.name, it.suggestions) }
    }

    // 参数设置已改为独立Activity并使用持久化存储

    fun buildFinalPrompt(): String {
        val sb = StringBuilder()
        if (inspiration.isNotBlank()) sb.append(inspiration.trim())
        paramDefs.forEach { def ->
            val enabled = PromptParamsStorage.loadEnabled(context, def.name)
            val v = PromptParamsStorage.loadValue(context, def.name).trim()
            if (enabled && v.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append(" | ")
                sb.append(def.name).append(": ").append(v)
            }
        }
        return sb.toString()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "提示词助写",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(imageVector = Icons.Filled.Tune, contentDescription = "AI配置")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 灵感区域
            Text(
                text = "灵感",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = inspiration,
                onValueChange = { inspiration = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("自由书写你的创意、主题或画面描述…") },
                singleLine = false,
                maxLines = 6
            )

            // 参数设置（模块入口，点击跳转至独立Activity）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "参数设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "已启用: $enabledCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilledTonalButton(onClick = {
                        val intent = android.content.Intent(context, PromptParamsActivity::class.java)
                        context.startActivity(intent)
                    }) { Text("打开") }
                }
            }

            // 最终区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "最终提示词",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // 小型复制按钮
                IconButton(
                    onClick = {
                        val text = buildFinalPrompt()
                        clipboard.setText(AnnotatedString(text))
                        scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                    )
                }
            }

            val finalSurfaceModifier = Modifier
                .fillMaxWidth()
                .weight(1f)

            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = finalSurfaceModifier
                    .defaultMinSize(minHeight = 120.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = buildFinalPrompt(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val canShowFab = cfgState != null && !streaming
                    if (canShowFab) {
                        SmallFloatingActionButton(
                            onClick = {
                                showResultDialog = true
                                startStreaming(buildFinalPrompt(), PolishAIClient.Mode.Refine)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.AutoFixHigh, contentDescription = "AI润色")
                        }
                    }
                }
            }
        }
    }

    // AI 配置弹窗
    if (showConfigDialog) {
        var baseUrl by remember { mutableStateOf(cfgState?.baseUrl.orEmpty()) }
        var apiKey by remember { mutableStateOf(cfgState?.apiKey.orEmpty()) }
        var model by remember { mutableStateOf(cfgState?.model.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("AI配置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL") })
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") })
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cfg = AIConfig(baseUrl.trim(), apiKey.trim(), model.trim())
                    PromptAIConfigStorage.save(context, cfg)
                    cfgState = cfg
                    showConfigDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) { Text("取消") }
            }
        )
    }

    // 参数设置改为独立 Activity，移除原弹窗

    // 润色结果弹窗（支持流式输出、复制、重新润色、翻译英文、取消）
    if (showResultDialog) {
        Dialog(onDismissRequest = {
            currentHandle?.cancel(); streaming = false; showResultDialog = false
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
                        text = "AI润色结果",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (streaming) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = resultText,
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
                                clipboard.setText(AnnotatedString(resultText));
                                scope.launch { snackbarHostState.showSnackbar("结果已复制") }
                            }) { Text("复制") }
                            TextButton(onClick = {
                                currentHandle?.cancel();
                                startStreaming(buildFinalPrompt(), PolishAIClient.Mode.Refine)
                            }) { Text("重新润色") }
                            TextButton(onClick = {
                                currentHandle?.cancel();
                                startStreaming(buildFinalPrompt(), PolishAIClient.Mode.TranslateEnglish)
                            }) { Text("翻译至英文") }
                        }
                        TextButton(onClick = {
                            currentHandle?.cancel();
                            streaming = false; showResultDialog = false
                        }) { Text("取消") }
                    }
                }
            }
        }
    }
}

// 参数卡片与FlowRow已迁移至 PromptParamsScreen