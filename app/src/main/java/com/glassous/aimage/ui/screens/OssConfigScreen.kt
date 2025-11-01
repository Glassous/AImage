package com.glassous.aimage.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import com.glassous.aimage.oss.OssConfig
import com.glassous.aimage.oss.OssConfigStorage
import com.glassous.aimage.oss.OssRegion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OssConfigScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("准备中…") }
    val context = LocalContext.current
    var selectedRegion by remember { mutableStateOf<OssRegion?>(null) }
    var endpoint by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var accessKeyId by remember { mutableStateOf("") }
    var accessKeySecret by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val cfg = OssConfigStorage.load(context)
        if (cfg != null) {
            selectedRegion = OssRegion.fromRegionId(cfg.regionId)
            endpoint = cfg.endpoint
            bucket = cfg.bucket
            accessKeyId = cfg.accessKeyId
            accessKeySecret = cfg.accessKeySecret
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "OSS 云端同步配置", fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "选择地域（自动填充Endpoint）",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = selectedRegion?.displayName ?: "请选择地域",
                        onValueChange = {},
                        label = { Text("地域") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        OssRegion.values().forEach { region ->
                            DropdownMenuItem(
                                text = { Text("${region.displayName} (${region.regionId})") },
                                onClick = {
                                    selectedRegion = region
                                    endpoint = region.endpoint
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Endpoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = bucket,
                    onValueChange = { bucket = it },
                    label = { Text("Bucket 名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = accessKeyId,
                    onValueChange = { accessKeyId = it },
                    label = { Text("AccessKey ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = accessKeySecret,
                    onValueChange = { accessKeySecret = it },
                    label = { Text("AccessKey Secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = {
                        val regionId = selectedRegion?.regionId ?: ""
                        if (regionId.isNotBlank() && endpoint.isNotBlank() && bucket.isNotBlank() && accessKeyId.isNotBlank() && accessKeySecret.isNotBlank()) {
                            OssConfigStorage.save(
                                context,
                                OssConfig(
                                    regionId = regionId,
                                    endpoint = endpoint,
                                    bucket = bucket,
                                    accessKeyId = accessKeyId,
                                    accessKeySecret = accessKeySecret
                                )
                            )
                        }
                    }) {
                        Text("保存配置")
                    }

                    OutlinedButton(onClick = {
                        showDialog = true
                        progressText = "正在同步…"
                        scope.launch {
                            com.glassous.aimage.oss.OssSyncManager.syncAll(context) { step ->
                                progressText = step
                            }
                            showDialog = false
                        }
                    }) {
                        Text("立即同步")
                    }
                }
            }
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { /* 同步过程中不可关闭 */ },
            confirmButton = {},
            title = { Text("正在同步") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(progressText)
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        )
    }
}