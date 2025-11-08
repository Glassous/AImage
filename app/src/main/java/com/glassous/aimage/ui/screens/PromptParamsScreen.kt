package com.glassous.aimage.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassous.aimage.data.PromptParamsStorage

data class ParamDef(val name: String, val suggestions: List<String> = emptyList())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptParamsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(PromptParamsStorage.loadFavorites(context)) }
    var paramDefs by remember { mutableStateOf<List<ParamDef>>(emptyList()) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editOriginalName by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editSuggestionsText by remember { mutableStateOf("") }

    // 加载参数定义（首次为空则写入内置预设），并初始化预设收藏
    LaunchedEffect(Unit) {
        val stored = PromptParamsStorage.loadParamDefs(context)
        if (stored.isEmpty()) {
            val builtin = listOf(
                ParamDef("光圈", listOf("f/1.4", "f/2.8", "f/5.6", "f/8")),
                ParamDef("焦段", listOf("24mm", "35mm", "50mm", "85mm")),
                ParamDef("ISO", listOf("100", "200", "400", "800")),
                ParamDef("快门速度", listOf("1/60", "1/125", "1/250", "1/1000")),
                ParamDef("相机型号", listOf("Canon R5", "Nikon Z7", "Sony A7R V")),
                ParamDef("镜头", listOf("50mm f/1.4", "24-70mm f/2.8", "85mm f/1.8")),
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
            val records = builtin.map { PromptParamsStorage.ParamDefRecord(it.name, it.suggestions, builtin = true) }
            PromptParamsStorage.saveParamDefs(context, records)
            paramDefs = builtin
        } else {
            paramDefs = stored.map { ParamDef(it.name, it.suggestions) }
        }

        // 初始化预设收藏（仅首次）
        if (favorites.isEmpty()) {
            val preset = setOf("光圈", "焦段", "ISO", "快门速度", "光线", "色彩风格", "构图", "情绪")
                .filter { n -> paramDefs.any { it.name == n } }
                .toSet()
            favorites = preset
            PromptParamsStorage.saveFavorites(context, favorites)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "参数设置",
                        maxLines = 1
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
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = "完成")
                    }
                }
            )
        }
    ) { innerPadding ->
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val noBottomPadding = androidx.compose.foundation.layout.PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = 0.dp
        )
        val navBarInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(noBottomPadding)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索参数或建议词…") },
                    singleLine = true
                )
                FilledTonalButton(onClick = {
                    editOriginalName = null
                    editName = ""
                    editSuggestionsText = ""
                    showEditDialog = true
                }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "新增参数")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新增")
                }
            }

            val base = remember(query, paramDefs) {
                val q = query.trim()
                if (q.isEmpty()) paramDefs
                else paramDefs.filter { def ->
                    def.name.contains(q, ignoreCase = true) ||
                            def.suggestions.any { it.contains(q, ignoreCase = true) }
                }
            }
            val filtered = remember(base, favorites) {
                val favs = base.filter { favorites.contains(it.name) }
                val others = base.filter { !favorites.contains(it.name) }
                favs + others
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 0.dp)
            ) {
                items(filtered) { def ->
                    ParameterCard(
                        name = def.name,
                        suggestions = def.suggestions,
                        isFavorite = favorites.contains(def.name),
                        onToggleFavorite = { favored ->
                            favorites = if (favored) favorites + def.name else favorites - def.name
                            PromptParamsStorage.setFavorite(context, def.name, favored)
                        },
                        onEdit = {
                            editOriginalName = def.name
                            editName = def.name
                            editSuggestionsText = def.suggestions.joinToString("\n")
                            showEditDialog = true
                        },
                        onDelete = {
                            PromptParamsStorage.deleteParamDef(context, def.name)
                            paramDefs = PromptParamsStorage.loadParamDefs(context).map { ParamDef(it.name, it.suggestions) }
                            favorites = PromptParamsStorage.loadFavorites(context)
                        }
                    )
                }
            }

            if (showEditDialog) {
                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    title = { Text(if (editOriginalName == null) "新增参数" else "编辑参数") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("参数名称") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editSuggestionsText,
                                onValueChange = { editSuggestionsText = it },
                                label = { Text("快捷建议词（每行一个）") },
                                singleLine = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val name = editName.trim()
                            val suggestions = editSuggestionsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            if (name.isNotEmpty()) {
                                if (editOriginalName == null) {
                                    PromptParamsStorage.addParamDef(context, PromptParamsStorage.ParamDefRecord(name, suggestions, builtin = false))
                                } else {
                                    PromptParamsStorage.updateParamDef(context, editOriginalName!!, PromptParamsStorage.ParamDefRecord(name, suggestions, builtin = false))
                                }
                                paramDefs = PromptParamsStorage.loadParamDefs(context).map { ParamDef(it.name, it.suggestions) }
                                favorites = PromptParamsStorage.loadFavorites(context)
                                showEditDialog = false
                            }
                        }) { Text("保存") }
                    },
                    dismissButton = {
                        if (editOriginalName != null) {
                            TextButton(onClick = {
                                PromptParamsStorage.deleteParamDef(context, editOriginalName!!)
                                paramDefs = PromptParamsStorage.loadParamDefs(context).map { ParamDef(it.name, it.suggestions) }
                                favorites = PromptParamsStorage.loadFavorites(context)
                                showEditDialog = false
                            }) { Text("删除") }
                        } else {
                            TextButton(onClick = { showEditDialog = false }) { Text("取消") }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ParameterCard(
    name: String,
    suggestions: List<String>,
    isFavorite: Boolean,
    onToggleFavorite: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(PromptParamsStorage.loadEnabled(context, name)) }
    var value by remember { mutableStateOf(PromptParamsStorage.loadValue(context, name)) }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onToggleFavorite(!isFavorite) }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isFavorite) "取消收藏" else "收藏"
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "删除")
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            PromptParamsStorage.saveEnabled(context, name, it)
                        }
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { s ->
                        AssistChip(
                            onClick = {
                                value = s
                                enabled = true
                                PromptParamsStorage.saveValue(context, name, s)
                                PromptParamsStorage.saveEnabled(context, name, true)
                            },
                            label = { Text(s) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    PromptParamsStorage.saveValue(context, name, it)
                    if (it.isNotBlank()) {
                        enabled = true
                        PromptParamsStorage.saveEnabled(context, name, true)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("填写${name}参数，例如：${suggestions.firstOrNull() ?: ""}") },
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