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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
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
    var showDialog by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("准备中…") }
    val configured = com.glassous.aimage.oss.OssConfigStorage.isConfigured(context)

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
                text = "配置阿里云 OSS 后，可将模型配置与历史记录上传到云端或从云端下载到本地。",
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

            // 第二行：上传/下载各占一半
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = {
                        showDialog = true
                        progressText = "正在上传…"
                        scope.launch {
                            com.glassous.aimage.oss.OssSyncManager.uploadToCloud(context) { step ->
                                progressText = step
                            }
                            showDialog = false
                        }
                    },
                    enabled = configured,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("上传到云端")
                }

                FilledTonalButton(
                    onClick = {
                        showDialog = true
                        progressText = "正在下载…"
                        scope.launch {
                            com.glassous.aimage.oss.OssSyncManager.downloadFromCloud(context) { step ->
                                progressText = step
                            }
                            showDialog = false
                        }
                    },
                    enabled = configured,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("从云端下载")
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

    if (showDialog) {
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
}