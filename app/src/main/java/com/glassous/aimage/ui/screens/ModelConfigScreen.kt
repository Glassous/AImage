package com.glassous.aimage.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class UserModel(
    var name: String,
    var note: String
)

enum class ModelGroupType(val displayName: String, val website: String) {
    Google("Google", "https://aistudio.google.com/api-keys"),
    Doubao("豆包", "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey"),
    Qwen("Qwen", "https://bailian.console.aliyun.com/?tab=app#/api-key")
}

data class ModelGroupConfig(
    var apiKey: String,
    val models: MutableList<UserModel>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groupConfigs = remember {
        mutableStateMapOf(
            ModelGroupType.Google to ModelGroupConfig(apiKey = "", models = mutableStateListOf()),
            ModelGroupType.Doubao to ModelGroupConfig(apiKey = "", models = mutableStateListOf()),
            ModelGroupType.Qwen to ModelGroupConfig(apiKey = "", models = mutableStateListOf())
        )
    }

    var showModelDialog by remember { mutableStateOf(false) }
    var activeGroup by remember { mutableStateOf<ModelGroupType?>(null) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var modelName by remember { mutableStateOf("") }
    var modelNote by remember { mutableStateOf("") }

    val context = LocalContext.current

    fun openWebsite(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    fun startAddModel(group: ModelGroupType) {
        activeGroup = group
        editIndex = null
        modelName = ""
        modelNote = ""
        showModelDialog = true
    }

    fun startEditModel(group: ModelGroupType, index: Int, existing: UserModel) {
        activeGroup = group
        editIndex = index
        modelName = existing.name
        modelNote = existing.note
        showModelDialog = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "模型配置",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(ModelGroupType.values()) { group ->
                val cfg = groupConfigs.getValue(group)
                GroupConfigCard(
                    group = group,
                    config = cfg,
                    onApiKeyChange = { cfg.apiKey = it },
                    onOpenWebsite = { openWebsite(group.website) },
                    onAddModel = { startAddModel(group) },
                    onEditModel = { index, model -> startEditModel(group, index, model) },
                    onDeleteModel = { index -> cfg.models.removeAt(index) }
                )
            }
        }
    }

    if (showModelDialog && activeGroup != null) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text(if (editIndex == null) "新增模型" else "编辑模型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("模型名称") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = modelNote,
                        onValueChange = { modelNote = it },
                        label = { Text("备注") },
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val group = activeGroup!!
                    val models = groupConfigs.getValue(group).models
                    if (modelName.isNotBlank()) {
                        if (editIndex == null) {
                            models.add(UserModel(modelName, modelNote))
                        } else {
                            models[editIndex!!] = UserModel(modelName, modelNote)
                        }
                        showModelDialog = false
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupConfigCard(
    group: ModelGroupType,
    config: ModelGroupConfig,
    onApiKeyChange: (String) -> Unit,
    onOpenWebsite: () -> Unit,
    onAddModel: () -> Unit,
    onEditModel: (Int, UserModel) -> Unit,
    onDeleteModel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = group.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenWebsite) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "官网"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("官网")
                }
            }

            OutlinedTextField(
                value = config.apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "模型列表",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onAddModel) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("新增")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                config.models.forEachIndexed { index, model ->
                    ModelItemRow(
                        model = model,
                        onEdit = { onEditModel(index, model) },
                        onDelete = { onDeleteModel(index) }
                    )
                }
                if (config.models.isEmpty()) {
                    Text(
                        text = "暂无模型，点击右侧新增",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelItemRow(
    model: UserModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (model.note.isNotBlank()) {
                Text(
                    text = model.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(imageVector = Icons.Filled.Edit, contentDescription = "编辑")
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Filled.Delete, contentDescription = "删除")
        }
    }
}