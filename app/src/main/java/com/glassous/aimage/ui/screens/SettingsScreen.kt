package com.glassous.aimage.ui.screens

import android.content.Intent
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
import com.glassous.aimage.ModelConfigActivity
import com.glassous.aimage.R
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