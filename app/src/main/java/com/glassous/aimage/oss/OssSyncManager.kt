package com.glassous.aimage.oss

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.glassous.aimage.ui.screens.HistoryItem
import com.glassous.aimage.data.ChatHistoryStorage
import com.glassous.aimage.data.ModelConfigStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

object OssSyncManager {
    private fun client(context: Context): OSSClient? {
        val cfg = OssConfigStorage.load(context) ?: return null
        val credentialProvider = OSSPlainTextAKSKCredentialProvider(cfg.accessKeyId, cfg.accessKeySecret)
        return OSSClient(context, cfg.endpoint, credentialProvider)
    }

    private fun bucket(context: Context): String? = OssConfigStorage.load(context)?.bucket

    suspend fun syncAll(context: Context, onStep: (String) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            onStep("正在同步模型配置…")
            syncModelConfig(context)
            onStep("正在同步提示词助写配置…")
            syncPromptAssistantConfig(context)
            onStep("正在同步历史记录…")
            syncHistory(context)
            onStep("同步完成")
        }
    }

    // 解析新版远端历史ID表：对象数组，包含除图片外的完整信息
    // 兼容旧版：字符串ID或{id, prompt}对象将转换为最小HistoryItem
    private fun parseRemoteHistoryTable(raw: String?): List<com.glassous.aimage.ui.screens.HistoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<com.glassous.aimage.ui.screens.HistoryItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    val id = obj.optString("id", "")
                    if (id.isBlank()) continue
                    val providerStr = obj.optString("providerGroup", "")
                    val provider = parseProviderCompat(providerStr)
                    val imageUrlRaw = obj.optString("imageUrl", "")
                    val imageUrl = if (imageUrlRaw.isBlank() || imageUrlRaw.equals("null", true)) null else imageUrlRaw
                    list.add(
                        com.glassous.aimage.ui.screens.HistoryItem(
                            id = id,
                            prompt = obj.optString("prompt", ""),
                            imageUrl = imageUrl,
                            timestamp = obj.optString("timestamp", ""),
                            model = obj.optString("model", ""),
                            provider = provider
                        )
                    )
                } else {
                    // 旧格式：纯ID字符串
                    val idStr = arr.optString(i, "")
                    if (idStr.isNotBlank()) {
                        list.add(
                            com.glassous.aimage.ui.screens.HistoryItem(
                                id = idStr,
                                prompt = "",
                                imageUrl = null,
                                timestamp = "",
                                model = "",
                                provider = com.glassous.aimage.ui.screens.ModelGroupType.Google
                            )
                        )
                    }
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    // 将历史ID表写为对象数组，包含除图片外的完整信息
    private fun buildHistoryTableBytes(items: List<com.glassous.aimage.ui.screens.HistoryItem>): ByteArray {
        val arr = JSONArray()
        items.forEach { h ->
            val o = JSONObject()
            o.put("id", h.id)
            o.put("prompt", h.prompt)
            // 允许 imageUrl 为 null
            if (h.imageUrl == null) o.put("imageUrl", JSONObject.NULL) else o.put("imageUrl", h.imageUrl)
            o.put("timestamp", h.timestamp)
            o.put("model", h.model)
            o.put("providerGroup", h.provider.name)
            arr.put(o)
        }
        return arr.toString().toByteArray()
    }

    // 合并历史记录元数据：同ID以本地为准，补充远端新增
    private fun unionHistory(local: List<com.glassous.aimage.ui.screens.HistoryItem>, remote: List<com.glassous.aimage.ui.screens.HistoryItem>): List<com.glassous.aimage.ui.screens.HistoryItem> {
        val byId = mutableMapOf<String, com.glassous.aimage.ui.screens.HistoryItem>()
        remote.forEach { byId[it.id] = it }
        local.forEach { byId[it.id] = it } // 本地覆盖远端
        // 排序遵循 ChatHistoryStorage（按时间降序，其次按id降序）
        fun parseTs(ts: String): Long = try { val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm"); fmt.parse(ts)?.time ?: 0L } catch (_: Exception) { 0L }
        return byId.values.sortedWith(compareByDescending<com.glassous.aimage.ui.screens.HistoryItem> { parseTs(it.timestamp) }
            .thenByDescending { it.id.toLongOrNull() ?: 0L })
    }

    suspend fun uploadToCloud(context: Context, onStep: (String) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            onStep("正在上传模型配置…")
            syncModelConfig(context)
            onStep("正在上传提示词助写配置…")
            syncPromptAssistantConfig(context)
            onStep("正在上传本地缺失到云端…")
            uploadMissingToRemote(context)
            onStep("上传完成")
        }
    }

    suspend fun downloadFromCloud(context: Context, onStep: (String) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            // 先同步模型配置（API Key、模型列表、默认模型）
            onStep("正在同步模型配置…")
            downloadModelConfig(context)
            onStep("正在同步提示词助写配置…")
            downloadPromptAssistantConfig(context)

            onStep("正在下载云端缺失到本地…")
            downloadMissingToLocal(context)
            onStep("下载完成")
        }
    }

    fun syncModelConfig(context: Context) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return

        val modelConfigs = JSONObject()

        // apiKeys
        val apiKeys = JSONObject()
        com.glassous.aimage.ui.screens.ModelGroupType.values().forEach { group ->
            apiKeys.put(group.name, ModelConfigStorage.loadApiKey(context, group))
        }
        modelConfigs.put("apiKeys", apiKeys)

        // models per group
        val models = JSONObject()
        com.glassous.aimage.ui.screens.ModelGroupType.values().forEach { group ->
            val arr = JSONArray()
            ModelConfigStorage.loadModels(context, group).forEach { m ->
                val obj = JSONObject()
                obj.put("name", m.name)
                obj.put("displayName", m.displayName)
                obj.put("note", m.note)
                arr.put(obj)
            }
            models.put(group.name, arr)
        }
        modelConfigs.put("models", models)

        // default model
        val defaultRef = ModelConfigStorage.loadDefaultModel(context)
        val defaultObj = if (defaultRef != null) JSONObject().apply {
            put("group", defaultRef.group.name)
            put("modelName", defaultRef.modelName)
        } else JSONObject.NULL
        modelConfigs.put("default", defaultObj)

        val bytes = modelConfigs.toString().toByteArray()
        val put = com.alibaba.sdk.android.oss.model.PutObjectRequest(b, "model_configs.json", bytes)
        c.putObject(put)
    }

    private fun downloadModelConfig(context: Context) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        try {
            val get = com.alibaba.sdk.android.oss.model.GetObjectRequest(b, "model_configs.json")
            val result = c.getObject(get)
            val text = result.objectContent.bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(text)
            val apiKeys = root.optJSONObject("apiKeys") ?: org.json.JSONObject()
            val models = root.optJSONObject("models") ?: org.json.JSONObject()
            // 保存 API Key 与模型列表
            com.glassous.aimage.ui.screens.ModelGroupType.values().forEach { group ->
                val key = apiKeys.optString(group.name, "")
                com.glassous.aimage.data.ModelConfigStorage.saveApiKey(context, group, key)

                val arr = models.optJSONArray(group.name) ?: org.json.JSONArray()
                val list = mutableListOf<com.glassous.aimage.ui.screens.UserModel>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        com.glassous.aimage.ui.screens.UserModel(
                            o.optString("name", ""),
                            o.optString("displayName", ""),
                            o.optString("note", "")
                        )
                    )
                }
                com.glassous.aimage.data.ModelConfigStorage.saveModels(context, group, list)
            }
            // 默认模型
            val def = root.optJSONObject("default")
            if (def != null) {
                val g = def.optString("group", "")
                val m = def.optString("modelName", "")
                try {
                    val group = com.glassous.aimage.ui.screens.ModelGroupType.valueOf(g)
                    if (m.isNotBlank()) {
                        com.glassous.aimage.data.ModelConfigStorage.saveDefaultModel(context, group, m)
                    }
                } catch (_: Exception) { /* ignore invalid group */ }
            }
        } catch (_: Exception) {
            // 下载/解析失败时忽略，不影响历史下载
        }
    }

    // 提供仅下载模型配置的异步入口（不阻塞UI）
    suspend fun downloadModelConfigAsync(context: Context) {
        withContext(Dispatchers.IO) {
            downloadModelConfig(context)
            downloadPromptAssistantConfig(context)
        }
    }

    // 增量下载缺失的历史记录（不更新远端 union 索引，避免启动时阻塞与不必要写入）
    suspend fun downloadMissingHistoryIncremental(context: Context) {
        withContext(Dispatchers.IO) {
            val c = client(context) ?: return@withContext
            val b = bucket(context) ?: return@withContext
            val idTableKey = "history_ids.json"
            val idsRemote = try {
                val get = com.alibaba.sdk.android.oss.model.GetObjectRequest(b, idTableKey)
                val result = c.getObject(get)
                result.objectContent.bufferedReader().use { it.readText() }
            } catch (e: Exception) { null }
            val remoteItems = parseRemoteHistoryTable(idsRemote)
            if (remoteItems.isNotEmpty()) {
                val localHistory = ChatHistoryStorage.loadAll(context)
                val merged = unionHistory(localHistory, remoteItems).toMutableList()
                ChatHistoryStorage.saveAll(context, merged)
            }
        }
    }

    fun syncHistory(context: Context) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return

        // Step 1: Download ID table first
        val idTableKey = "history_ids.json"
        val idsRemote = try {
            val get = com.alibaba.sdk.android.oss.model.GetObjectRequest(b, idTableKey)
            val result = c.getObject(get)
            result.objectContent.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }

        // Build local list
        val localHistory = ChatHistoryStorage.loadAll(context)
        val localIds = localHistory.map { it.id }.toSet()

        // Parse remote table (full metadata except image)
        val remoteItems = parseRemoteHistoryTable(idsRemote)
        val remoteIds = remoteItems.map { it.id }.toSet()

        // Determine uploads and downloads
        val toUpload = localIds.minus(remoteIds)
        val toDownload = remoteIds.minus(localIds)

        // Upload missing on remote
        localHistory.filter { toUpload.contains(it.id) }.forEach { item ->
            uploadHistoryItem(context, c, b, item)
        }

        // 合并保存（仅元数据，不下载图片）
        val merged = unionHistory(localHistory, remoteItems)
        ChatHistoryStorage.saveAll(context, merged)

        // 更新远端ID表为合并后的完整元数据
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, idTableKey, buildHistoryTableBytes(merged)))
    }

    private fun uploadMissingToRemote(context: Context) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        val idTableKey = "history_ids.json"
        val idsRemote = try {
            val get = com.alibaba.sdk.android.oss.model.GetObjectRequest(b, idTableKey)
            val result = c.getObject(get)
            result.objectContent.bufferedReader().use { it.readText() }
        } catch (e: Exception) { null }
        val remoteItems = parseRemoteHistoryTable(idsRemote)
        val remoteIds = remoteItems.map { it.id }.toSet()
        val localHistory = ChatHistoryStorage.loadAll(context)
        val localIds = localHistory.map { it.id }.toSet()
        val toUpload = localIds.minus(remoteIds)
        localHistory.filter { toUpload.contains(it.id) }.forEach { item ->
            uploadHistoryItem(context, c, b, item)
        }
        val union = unionHistory(localHistory, remoteItems)
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, idTableKey, buildHistoryTableBytes(union)))
    }

    private fun downloadMissingToLocal(context: Context) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        val idTableKey = "history_ids.json"
        val idsRemote = try {
            val get = com.alibaba.sdk.android.oss.model.GetObjectRequest(b, idTableKey)
            val result = c.getObject(get)
            result.objectContent.bufferedReader().use { it.readText() }
        } catch (e: Exception) { null }
        val remoteItems = parseRemoteHistoryTable(idsRemote)
        val remoteIds = remoteItems.map { it.id }.toSet()
        val localHistory = ChatHistoryStorage.loadAll(context)
        val localIds = localHistory.map { it.id }.toSet()
        val toDownload = remoteIds.minus(localIds)
        // 合并保存（不下载图片），同时刷新远端ID表
        val merged = unionHistory(localHistory, remoteItems)
        ChatHistoryStorage.saveAll(context, merged)
        val union = merged // 已按union合并
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, idTableKey, buildHistoryTableBytes(union)))
    }

    fun onHistoryAdded(context: Context, item: HistoryItem) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        uploadHistoryItem(context, c, b, item)
        // 更新ID表（完整元数据）
        val local = ChatHistoryStorage.loadAll(context)
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, "history_ids.json", buildHistoryTableBytes(local)))
    }

    fun onHistoryDeleted(context: Context, deletedId: String) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        // 重新生成ID表（完整元数据）
        val local = ChatHistoryStorage.loadAll(context)
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, "history_ids.json", buildHistoryTableBytes(local)))
        // 删除对应的远端独立记录文件
        try {
            c.deleteObject(com.alibaba.sdk.android.oss.model.DeleteObjectRequest(b, "history/$deletedId.json"))
        } catch (_: Exception) { /* ignore */ }
    }

    fun onHistoryUpdated(context: Context, item: HistoryItem) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        uploadHistoryItem(context, c, b, item)
        // 刷新ID表（完整元数据）
        val local = ChatHistoryStorage.loadAll(context)
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, "history_ids.json", buildHistoryTableBytes(local)))
    }

    private fun uploadHistoryItem(context: Context, c: OSSClient, b: String, item: HistoryItem) {
        // 独立记录文件仅保存图片Base64
        val objKey = "history/${item.id}.json"
        val o = JSONObject()
        // 编码本地图片为Base64（若存在）
        if (!item.imageUrl.isNullOrBlank()) {
            try {
                val bitmap = BitmapFactory.decodeFile(item.imageUrl)
                if (bitmap != null) {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    val base64Img = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    o.put("imageBase64", base64Img)
                }
            } catch (_: Exception) { /* ignore */ }
        }
        val bytes = o.toString().toByteArray()
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, objKey, bytes))
    }

    private fun downloadHistoryItem(context: Context, c: OSSClient, b: String, id: String): HistoryItem? {
        return try {
            val get = com.alibaba.sdk.android.oss.model.GetObjectRequest(b, "history/$id.json")
            val result = c.getObject(get)
            val text = result.objectContent.bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            val imgBase64 = o.optString("imageBase64", "")
            var imageUrl: String? = null
            if (imgBase64.isNotBlank()) {
                try {
                    // Decode and persist to local app files dir
                    val imagesDir = File(context.filesDir, "images")
                    if (!imagesDir.exists()) imagesDir.mkdirs()
                    val imgBytes = Base64.decode(imgBase64, Base64.DEFAULT)
                    val f = File(imagesDir, "$id.png")
                    f.writeBytes(imgBytes)
                    imageUrl = f.absolutePath
                } catch (_: Exception) { /* ignore and keep imageUrl null */ }
            } else {
                // 新版记录文件不包含元数据，这里保持 imageUrl 为 null
                imageUrl = null
            }

            // Robust provider parsing compatible with local storage/backup
            val provider = com.glassous.aimage.ui.screens.ModelGroupType.Google
            HistoryItem(
                id = id,
                prompt = "",
                imageUrl = imageUrl,
                timestamp = "",
                model = "",
                provider = provider
            )
        } catch (_: Exception) {
            null
        }
    }

    // —— 提示词助写 AI 配置 ——
    private fun syncPromptAssistantConfig(context: Context) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        try {
            val cfg = com.glassous.aimage.ui.screens.PromptAIConfigStorage.load(context)
            val root = JSONObject()
            if (cfg != null) {
                root.put("baseUrl", cfg.baseUrl)
                root.put("apiKey", cfg.apiKey)
                root.put("model", cfg.model)
            } else {
                root.put("baseUrl", JSONObject.NULL)
                root.put("apiKey", JSONObject.NULL)
                root.put("model", JSONObject.NULL)
            }
            val bytes = root.toString().toByteArray()
            c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, "prompt_ai_config.json", bytes))
        } catch (_: Exception) {
            // ignore upload errors
        }
    }

    private fun downloadPromptAssistantConfig(context: Context) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        try {
            val get = com.alibaba.sdk.android.oss.model.GetObjectRequest(b, "prompt_ai_config.json")
            val result = c.getObject(get)
            val text = result.objectContent.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val baseUrl = root.optString("baseUrl", "")
            val apiKey = root.optString("apiKey", "")
            val model = root.optString("model", "")
            if (baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()) {
                com.glassous.aimage.ui.screens.PromptAIConfigStorage.save(context, com.glassous.aimage.ui.screens.AIConfig(baseUrl, apiKey, model))
            }
        } catch (_: Exception) { /* ignore */ }
    }

    // 与本地解析保持一致的 provider 兼容逻辑
    private fun parseProviderCompat(value: String?): com.glassous.aimage.ui.screens.ModelGroupType {
        if (value.isNullOrBlank()) return com.glassous.aimage.ui.screens.ModelGroupType.Google
        try { return com.glassous.aimage.ui.screens.ModelGroupType.valueOf(value) } catch (_: Exception) { }
        val normalized = value.lowercase()
        return when {
            normalized.contains("gemini") || normalized.contains("google") -> com.glassous.aimage.ui.screens.ModelGroupType.Google
            normalized.contains("doubao") || normalized.contains("豆包") || normalized.contains("volc") -> com.glassous.aimage.ui.screens.ModelGroupType.Doubao
            normalized.contains("qwen") || normalized.contains("阿里") || normalized.contains("ali") -> com.glassous.aimage.ui.screens.ModelGroupType.Qwen
            normalized.contains("minimax") -> com.glassous.aimage.ui.screens.ModelGroupType.MiniMax
            else -> com.glassous.aimage.ui.screens.ModelGroupType.Google
        }
    }
}