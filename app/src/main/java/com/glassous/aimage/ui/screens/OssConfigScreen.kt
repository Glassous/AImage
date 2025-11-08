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
// 立即同步按钮已移除，进度提示不再在配置页使用
import com.glassous.aimage.oss.OssConfig
import com.glassous.aimage.oss.OssConfigStorage
import com.glassous.aimage.oss.OssRegion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssConfigScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
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

                    // “立即同步”按钮已移除
                }
            }
        }
    }
}