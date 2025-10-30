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
    var showParamsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current


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

    val paramDefs = remember {
        listOf(
            ParamDef("光圈", listOf("f/1.4", "f/2.8", "f/5.6", "f/8")),
            ParamDef("焦段", listOf("24mm", "35mm", "50mm", "85mm")),
            ParamDef("ISO", listOf("100", "200", "400", "800")),
            ParamDef("快门速度", listOf("1/60", "1/125", "1/250", "1/1000")),
            ParamDef("相机型号", listOf("Canon R5", "Nikon Z7", "Sony A7R V")),
            ParamDef("镜头", listOf("50mm f/1.4", "24-70mm f/2.8", "85mm f/1.8")),
            ParamDef("纵横比", listOf("1:1", "3:2", "4:3", "16:9")),
            ParamDef("分辨率", listOf("1024x1024", "2048x2048", "4k")),
            ParamDef("光线", listOf("柔光", "逆光", "侧光", "黄金时刻")),
            ParamDef("色彩风格", listOf("冷色调", "暖色调", "高饱和", "复古色")),
            ParamDef("情绪", listOf("宁静", "神秘", "活力", "忧郁")),
            ParamDef("构图", listOf("三分法", "对称", "居中", "留白")),
            ParamDef("对焦", listOf("主体对焦", "前景虚化", "背景虚化")),
            ParamDef("景深", listOf("浅景深", "中景深", "深景深")),
            ParamDef("透视", listOf("仰拍", "俯拍", "平视")),
            ParamDef("胶片颗粒", listOf("轻微", "中等", "强烈")),
            ParamDef("HDR", listOf("开启", "关闭")),
            ParamDef("白平衡", listOf("日光", "阴影", "钨丝灯", "自定义")),
            ParamDef("曝光补偿", listOf("-1EV", "0EV", "+1EV")),
            ParamDef("虚化强度", listOf("低", "中", "高")),
            ParamDef("运动模糊", listOf("关闭", "轻微", "明显")),
            ParamDef("负面提示", listOf("噪点", "低清晰度", "过曝", "欠曝")),
            ParamDef("风格", listOf("写实", "赛博朋克", "油画", "水彩")),
            ParamDef("风格强度", listOf("0.3", "0.5", "0.8")),
            ParamDef("采样器", listOf("Euler", "DPM++", "DDIM")),
            ParamDef("采样步数", listOf("20", "30", "50")),
            ParamDef("CFG Scale", listOf("5", "7", "10")),
            ParamDef("画质", listOf("标准", "高", "超高")),
            ParamDef("细节增强", listOf("关闭", "轻度", "强力")),
            ParamDef("材质", listOf("金属", "木质", "玻璃", "织物")),
            ParamDef("环境", listOf("室内", "室外", "城市", "自然")),
            ParamDef("背景", listOf("纯色", "虚化", "城市夜景")),
            ParamDef("前景", listOf("花朵", "树叶", "雨滴")),
            ParamDef("天气", listOf("晴", "阴", "雨", "雪", "雾")),
            ParamDef("时间", listOf("白天", "黄昏", "夜晚", "黎明")),
            ParamDef("服装", listOf("休闲", "正装", "复古")),
            ParamDef("表情", listOf("微笑", "冷峻", "愉悦", "沉思")),
            ParamDef("动作", listOf("站立", "行走", "奔跑", "回眸")),
            ParamDef("视角", listOf("广角", "标准", "长焦")),
            ParamDef("相机位置", listOf("近景", "中景", "远景")),
            ParamDef("噪点", listOf("低", "中", "高")),
            ParamDef("锐化", listOf("低", "中", "高")),
            ParamDef("色温", listOf("冷色", "中性", "暖色"))
        )
    }

    val paramValues = remember { mutableStateMapOf<String, String>() }
    val paramEnabled = remember { mutableStateMapOf<String, Boolean>() }

    fun buildFinalPrompt(): String {
        val sb = StringBuilder()
        if (inspiration.isNotBlank()) sb.append(inspiration.trim())
        paramDefs.forEach { def ->
            if (paramEnabled[def.name] == true) {
                val v = paramValues[def.name]?.trim().orEmpty()
                if (v.isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append(" | ")
                    sb.append(def.name).append(": ").append(v)
                }
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

            // 参数设置（模块入口，点击打开弹窗）
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
                val enabledCount = remember(paramEnabled, paramValues) {
                    paramEnabled.count { (k, v) ->
                        v == true && (paramValues[k]?.isNotBlank() == true)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "已启用: $enabledCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilledTonalButton(onClick = { showParamsDialog = true }) { Text("打开") }
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

    // 参数设置弹窗
    if (showParamsDialog) {
        Dialog(onDismissRequest = { showParamsDialog = false }) {
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
                        text = "参数设置",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                        .defaultMinSize(minHeight = 320.dp)) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(paramDefs) { def ->
                                ParameterCard(
                                    name = def.name,
                                    suggestions = def.suggestions,
                                    value = paramValues[def.name].orEmpty(),
                                    enabled = paramEnabled[def.name] == true,
                                    onToggle = { on -> paramEnabled[def.name] = on },
                                    onValueChange = { v ->
                                        paramValues[def.name] = v
                                        if (v.isNotBlank()) paramEnabled[def.name] = true
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showParamsDialog = false }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { showParamsDialog = false }) { Text("完成") }
                    }
                }
            }
        }
    }

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

@Composable
private fun ParameterCard(
    name: String,
    suggestions: List<String>,
    value: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            if (suggestions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { s ->
                        AssistChip(
                            onClick = {
                                onValueChange(s)
                                onToggle(true)
                            },
                            label = { Text(s) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("填写$\u007Bname\u007D参数，例如：${suggestions.firstOrNull() ?: ""}") },
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}