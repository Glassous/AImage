package com.glassous.aimage.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import com.glassous.aimage.ModelConfigActivity
import com.glassous.aimage.OssConfigActivity
import com.glassous.aimage.R
import com.glassous.aimage.data.BackupManager
import com.glassous.aimage.data.ModelConfigStorage
import com.glassous.aimage.data.ThemeMode
import com.glassous.aimage.data.ThemeStorage

data class SettingsItem(
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit
)

// 云端同步操作类型（上传/下载）
private enum class CloudAction { Upload, Download }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 状态：各分组模型列表与当前默认选择
    val groupModels = remember { mutableStateMapOf<ModelGroupType, List<UserModel>>() }
    var defaultRef by remember { mutableStateOf<ModelConfigStorage.DefaultModelRef?>(null) }
    var showSelector by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ModelGroupType.values().forEach { group ->
            groupModels[group] = ModelConfigStorage.loadModels(context, group)
        }
        defaultRef = ModelConfigStorage.loadDefaultModel(context)
    }

    // 订阅模型配置版本流：任意模型配置/API Key/默认模型变动后，设置页自动刷新显示
    val configVersion by ModelConfigStorage.versionFlow.collectAsState(initial = 0L)
    LaunchedEffect(configVersion) {
        ModelGroupType.values().forEach { group ->
            groupModels[group] = ModelConfigStorage.loadModels(context, group)
        }
        defaultRef = ModelConfigStorage.loadDefaultModel(context)
    }

    fun currentDefaultLabel(): String {
        val ref = defaultRef ?: return "未选择"
        val list = groupModels[ref.group].orEmpty()
        val m = list.find { it.name == ref.modelName }
        val modelLabel = (m?.displayName?.takeIf { it.isNotBlank() } ?: ref.modelName).ifBlank { "(未命名)" }
        return "${ref.group.displayName} / $modelLabel"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontWeight = FontWeight.Bold
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
                        Icon(
                            imageVector = if (lastSuccess) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = if (lastSuccess) "自动同步成功" else "自动同步失败"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val noBottomPadding = androidx.compose.foundation.layout.PaddingValues(
            start = paddingValues.calculateStartPadding(layoutDirection),
            top = paddingValues.calculateTopPadding(),
            end = paddingValues.calculateEndPadding(layoutDirection),
            bottom = 0.dp
        )
        val navBarInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(noBottomPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp + navBarInset
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 主题设置：跟随系统 / 浅色 / 深色
            item {
                ThemeSettingCard()
            }
            item {
                SettingsItemCard(
                    item = SettingsItem(
                        title = "模型配置",
                        subtitle = "配置AI模型参数和选项",
                        onClick = {
                            context.startActivity(Intent(context, ModelConfigActivity::class.java))
                        }
                    ),
                    onClick = {
                        context.startActivity(Intent(context, ModelConfigActivity::class.java))
                    }
                )
            }

            item {
                SettingsItemCard(
                    item = SettingsItem(
                        title = "默认模型",
                        subtitle = currentDefaultLabel(),
                        onClick = { showSelector = true }
                    ),
                    onClick = { showSelector = true }
                )
            }

            // 数据备份模块
            item {
                BackupSettingCard()
            }
            // 云端同步模块（OSS）
            item {
                CloudSyncSettingCard()
            }
        }
    }

    if (showSelector) {
        // 每次打开弹窗时重新读取数据
        LaunchedEffect(showSelector) {
            if (showSelector) {
                ModelGroupType.values().forEach { group ->
                    groupModels[group] = ModelConfigStorage.loadModels(context, group)
                }
                defaultRef = ModelConfigStorage.loadDefaultModel(context)
            }
        }
        
        AlertDialog(
            onDismissRequest = { showSelector = false },
            title = { Text("选择默认模型") },
            text = {
                val availableGroups = ModelGroupType.values().filter { (groupModels[it] ?: emptyList()).isNotEmpty() }
                if (availableGroups.isEmpty()) {
                    Text("暂无可选模型，请先在‘模型配置’中添加模型。")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(availableGroups) { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = group.logoRes()),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(group.displayName, style = MaterialTheme.typography.titleSmall)
                                }
                                val list = groupModels[group].orEmpty()
                                list.forEach { m ->
                                    val isSelected = defaultRef?.group == group && defaultRef?.modelName == m.name
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                ModelConfigStorage.saveDefaultModel(context, group, m.name)
                                                defaultRef = ModelConfigStorage.DefaultModelRef(group, m.name)
                                                showSelector = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
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
            },
            confirmButton = {
                TextButton(onClick = { showSelector = false }) { Text("关闭") }
            }
        )
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

@Composable
private fun ThemeSettingCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    ThemeStorage.ensureInitialized(context)
    val currentMode by ThemeStorage.themeModeFlow.collectAsState(initial = ThemeStorage.loadThemeMode(context))
    val showTranslateFab by ThemeStorage.showTranslateFabFlow.collectAsState(initial = ThemeStorage.loadShowTranslateFab(context))

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "主题设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemeOption(
                    label = "跟随系统",
                    selected = currentMode == ThemeMode.System,
                    onClick = { ThemeStorage.saveThemeMode(context, ThemeMode.System) }
                )
                ThemeOption(
                    label = "浅色",
                    selected = currentMode == ThemeMode.Light,
                    onClick = { ThemeStorage.saveThemeMode(context, ThemeMode.Light) }
                )
                ThemeOption(
                    label = "深色",
                    selected = currentMode == ThemeMode.Dark,
                    onClick = { ThemeStorage.saveThemeMode(context, ThemeMode.Dark) }
                )
            }

            // 仅当 AI 助写的 AI 配置完成时，显示“主页翻译按钮”开关
            val aiCfgReady = PromptAIConfigStorage.load(context) != null
            if (aiCfgReady) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "显示主页翻译按钮",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = showTranslateFab == true,
                        onCheckedChange = { ThemeStorage.saveShowTranslateFab(context, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedContainer = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
    val unselectedContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) selectedContainer else unselectedContainer
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsItemCard(
    item: SettingsItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = item.title,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "进入",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupSettingCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var importSummary by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            exporting = true
            scope.launch {
                try {
                    val json = BackupManager.createBackup(context)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    }
                } catch (_: Exception) { /* ignore */ }
                exporting = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8)
                    if (json != null) {
                        val result = BackupManager.restoreBackup(context, json)
                        importSummary = "已恢复 模型:${result.modelsRestored} 项，历史记录:${result.historyRestored} 条"
                    }
                } catch (_: Exception) {
                    importSummary = "导入失败，请检查文件"
                }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "本地数据备份",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "在本地导入/导出用户模型、API Key、默认模型与历史记录（图片转Base64并随JSON保存）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { exportLauncher.launch("aimage-backup.json") },
                    enabled = !exporting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (exporting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在导出…")
                    } else {
                        Text("导出备份")
                    }
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导入备份")
                }
            }

            if (importSummary != null) {
                Text(
                    text = importSummary!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun CloudSyncSettingCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showProgress by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("准备中…") }
    var showConfirm by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var resultTitle by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<CloudAction?>(null) }
    // 订阅 OSS 配置流，按钮实时启用/禁用
    com.glassous.aimage.oss.OssConfigStorage.ensureInitialized(context)
    val ossCfgOrNull by com.glassous.aimage.oss.OssConfigStorage.configFlow.collectAsState(initial = com.glassous.aimage.oss.OssConfigStorage.load(context))
    val configured = ossCfgOrNull != null

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "云端同步（OSS）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "配置阿里云 OSS 后，可执行全面上传或下载并覆盖云端/本地数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            // 第一行：配置按钮独占一行
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { context.startActivity(Intent(context, com.glassous.aimage.OssConfigActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("配置OSS")
                }
            }

            // 中间行：自动同步开关（居中显示）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 订阅自动同步开关
                com.glassous.aimage.oss.OssConfigStorage.ensureInitialized(context)
                val autoSyncEnabled by com.glassous.aimage.oss.OssConfigStorage.autoSyncFlow.collectAsState(
                    initial = com.glassous.aimage.oss.OssConfigStorage.isAutoSyncEnabled(context)
                )
                Text("自动同步")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = autoSyncEnabled,
                    onCheckedChange = { enabled ->
                        com.glassous.aimage.oss.OssConfigStorage.setAutoSyncEnabled(context, enabled)
                    }
                )
            }

            // 第二行：上传/下载各占一半
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = {
                        pendingAction = CloudAction.Upload
                        showConfirm = true
                    },
                    enabled = configured,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("全面上传并覆盖")
                }

                FilledTonalButton(
                    onClick = {
                        pendingAction = CloudAction.Download
                        showConfirm = true
                    },
                    enabled = configured,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("全面下载并覆盖")
                }
            }

            if (!configured) {
                Text(
                    text = "请先完成OSS配置后再进行云端同步。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showProgress) {
        AlertDialog(
            onDismissRequest = { /* 同步过程中不可关闭 */ },
            confirmButton = {},
            title = { Text("正在同步") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(progressText)
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(if (pendingAction == CloudAction.Upload) "确认全面上传并覆盖云端" else "确认全面下载并覆盖本地") },
            text = {
                Text(if (pendingAction == CloudAction.Upload)
                    "将上传本地模型配置与历史记录，并覆盖云端同名数据。是否继续？"
                    else "将从云端下载模型配置与历史记录，并覆盖本地数据。是否继续？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    showProgress = true
                    progressText = if (pendingAction == CloudAction.Upload) "正在覆盖上传…" else "正在覆盖下载…"
                    scope.launch {
                        try {
                            if (pendingAction == CloudAction.Upload) {
                                com.glassous.aimage.oss.OssSyncManager.uploadOverwriteAll(context) { step ->
                                    progressText = step
                                }
                                resultTitle = "上传完成"
                                resultMessage = "已成功覆盖上传到云端。"
                                isError = false
                            } else {
                                com.glassous.aimage.oss.OssSyncManager.downloadOverwriteAll(context) { step ->
                                    progressText = step
                                }
                                resultTitle = "下载完成"
                                resultMessage = "已成功覆盖下载并替换本地数据。"
                                isError = false
                            }
                        } catch (e: Exception) {
                            resultTitle = if (pendingAction == CloudAction.Upload) "上传失败" else "下载失败"
                            resultMessage = e.message ?: "发生未知错误"
                            isError = true
                        } finally {
                            showProgress = false
                            showResult = true
                        }
                    }
                }) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showResult) {
        AlertDialog(
            onDismissRequest = { showResult = false },
            title = { Text(resultTitle) },
            text = { Text(resultMessage) },
            confirmButton = {
                TextButton(onClick = { showResult = false }) { Text("好的") }
            }
        )
    }
}