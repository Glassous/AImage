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
            onStep("正在同步历史记录…")
            syncHistory(context)
            onStep("同步完成")
        }
    }

    // 兼容解析远端历史索引（允许旧版仅ID数组或新版对象数组{id, prompt}）
    private fun parseRemoteHistoryIndex(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val arr = JSONArray(raw)
            val map = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    val id = obj.optString("id", "")
                    val prompt = obj.optString("prompt", "")
                    if (id.isNotBlank()) map[id] = prompt
                } else {
                    val idStr = arr.optString(i, "")
                    if (idStr.isNotBlank()) map[idStr] = "" // 旧格式下无提示词
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // 将索引写为{id, prompt}对象数组
    private fun buildHistoryIndexBytes(index: Map<String, String>): ByteArray {
        val arr = JSONArray()
        index.forEach { (id, prompt) ->
            val o = JSONObject()
            o.put("id", id)
            o.put("prompt", prompt)
            arr.put(o)
        }
        return arr.toString().toByteArray()
    }

    // 合并索引：以远端为基础，用本地提示词覆盖同ID项，并补充本地新增ID
    private fun unionIndex(local: List<com.glassous.aimage.ui.screens.HistoryItem>, remote: Map<String, String>): Map<String, String> {
        val result = remote.toMutableMap()
        local.forEach { h -> result[h.id] = h.prompt }
        return result
    }

    suspend fun uploadToCloud(context: Context, onStep: (String) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            onStep("正在上传模型配置…")
            syncModelConfig(context)
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
            val remoteIndex = parseRemoteHistoryIndex(idsRemote)
            val remoteIds = remoteIndex.keys.toSet()
            val localHistory = ChatHistoryStorage.loadAll(context)
            val localIds = localHistory.map { it.id }.toSet()
            val toDownload = remoteIds.minus(localIds)
            if (toDownload.isNotEmpty()) {
                toDownload.forEach { id ->
                    val item = downloadHistoryItem(context, c, b, id)
                    if (item != null) {
                        try {
                            val current = ChatHistoryStorage.loadAll(context)
                            current.add(0, item)
                            ChatHistoryStorage.saveAll(context, current)
                        } catch (_: Exception) { /* ignore individual failures */ }
                    }
                }
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

        // Parse remote index (id -> prompt)
        val remoteIndex = parseRemoteHistoryIndex(idsRemote)
        val remoteIds = remoteIndex.keys.toSet()

        // Determine uploads and downloads
        val toUpload = localIds.minus(remoteIds)
        val toDownload = remoteIds.minus(localIds)

        // Upload missing on remote
        localHistory.filter { toUpload.contains(it.id) }.forEach { item ->
            uploadHistoryItem(context, c, b, item)
        }

        // Download missing locally（一次性合并保存，避免被覆盖只保留一条）
        if (toDownload.isNotEmpty()) {
            val merged = localHistory.toMutableList()
            toDownload.forEach { id ->
                val item = downloadHistoryItem(context, c, b, id)
                if (item != null) {
                    merged.add(item)
                }
            }
            ChatHistoryStorage.saveAll(context, merged)
        }

        // Update index table on remote to reflect local union (id+prompt)
        val union = unionIndex(localHistory, remoteIndex)
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, idTableKey, buildHistoryIndexBytes(union)))
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
        val remoteIndex = parseRemoteHistoryIndex(idsRemote)
        val remoteIds = remoteIndex.keys.toSet()
        val localHistory = ChatHistoryStorage.loadAll(context)
        val localIds = localHistory.map { it.id }.toSet()
        val toUpload = localIds.minus(remoteIds)
        localHistory.filter { toUpload.contains(it.id) }.forEach { item ->
            uploadHistoryItem(context, c, b, item)
        }
        val union = unionIndex(localHistory, remoteIndex)
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, idTableKey, buildHistoryIndexBytes(union)))
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
        val remoteIndex = parseRemoteHistoryIndex(idsRemote)
        val remoteIds = remoteIndex.keys.toSet()
        val localHistory = ChatHistoryStorage.loadAll(context)
        val localIds = localHistory.map { it.id }.toSet()
        val toDownload = remoteIds.minus(localIds)
        if (toDownload.isNotEmpty()) {
            val merged = localHistory.toMutableList()
            toDownload.forEach { id ->
                val item = downloadHistoryItem(context, c, b, id)
                if (item != null) {
                    merged.add(0, item)
                }
            }
            ChatHistoryStorage.saveAll(context, merged)
            // 刷新远端索引为 union（id+prompt），不强制覆盖
            val union = unionIndex(merged, remoteIndex)
            c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, idTableKey, buildHistoryIndexBytes(union)))
        }
    }

    fun onHistoryAdded(context: Context, item: HistoryItem) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        uploadHistoryItem(context, c, b, item)
        // Update index table (id+prompt)
        val local = ChatHistoryStorage.loadAll(context)
        val index = local.associate { it.id to it.prompt }
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, "history_ids.json", buildHistoryIndexBytes(index)))
    }

    fun onHistoryDeleted(context: Context, deletedId: String) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        // Regenerate index table from local (id+prompt)
        val local = ChatHistoryStorage.loadAll(context)
        val index = local.associate { it.id to it.prompt }
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, "history_ids.json", buildHistoryIndexBytes(index)))
        // 删除对应的远端独立记录文件
        try {
            c.deleteObject(com.alibaba.sdk.android.oss.model.DeleteObjectRequest(b, "history/$deletedId.json"))
        } catch (_: Exception) { /* ignore */ }
    }

    fun onHistoryUpdated(context: Context, item: HistoryItem) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        uploadHistoryItem(context, c, b, item)
        // 刷新索引表（id+prompt）
        val local = ChatHistoryStorage.loadAll(context)
        val index = local.associate { it.id to it.prompt }
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, "history_ids.json", buildHistoryIndexBytes(index)))
    }

    private fun uploadHistoryItem(context: Context, c: OSSClient, b: String, item: HistoryItem) {
        val objKey = "history/${item.id}.json"
        val o = JSONObject()
        o.put("id", item.id)
        o.put("prompt", item.prompt)
        o.put("imageUrl", item.imageUrl ?: JSONObject.NULL)
        o.put("timestamp", item.timestamp)
        o.put("model", item.model)
        o.put("providerGroup", item.provider.name)

        // If there is an image file path, try to encode and upload alongside
        if (!item.imageUrl.isNullOrBlank()) {
            try {
                val bitmap = BitmapFactory.decodeFile(item.imageUrl)
                if (bitmap != null) {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    val base64Img = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    o.put("imageBase64", base64Img)
                }
            } catch (_: Exception) { }
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
                // Fallback: try raw imageUrl if present
                val rawUrl = o.optString("imageUrl", "")
                imageUrl = if (rawUrl.isBlank() || rawUrl.equals("null", true)) null else rawUrl
            }

            // Robust provider parsing compatible with local storage/backup
            val providerStr = o.optString("providerGroup", "")
            val provider = parseProviderCompat(providerStr)
            HistoryItem(
                id = o.optString("id", id),
                prompt = o.optString("prompt", ""),
                imageUrl = imageUrl,
                timestamp = o.optString("timestamp", ""),
                model = o.optString("model", ""),
                provider = provider
            )
        } catch (_: Exception) {
            null
        }
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