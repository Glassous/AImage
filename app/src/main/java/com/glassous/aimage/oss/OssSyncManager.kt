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

        // Parse remote IDs
        val remoteIds = mutableSetOf<String>()
        if (idsRemote != null) {
            try {
                val arr = JSONArray(idsRemote)
                for (i in 0 until arr.length()) {
                    remoteIds.add(arr.getString(i))
                }
            } catch (_: Exception) { }
        }

        // Determine uploads and downloads
        val toUpload = localIds.minus(remoteIds)
        val toDownload = remoteIds.minus(localIds)

        // Upload missing on remote
        localHistory.filter { toUpload.contains(it.id) }.forEach { item ->
            uploadHistoryItem(context, c, b, item)
        }

        // Download missing locally
        toDownload.forEach { id ->
            val item = downloadHistoryItem(c, b, id)
            if (item != null) {
                val merged = localHistory.toMutableList().apply { add(item) }
                ChatHistoryStorage.saveAll(context, merged)
            }
        }

        // Update ID table on remote to reflect local union
        val unionIds = (localIds + remoteIds).toList()
        val idArr = JSONArray()
        unionIds.forEach { idArr.put(it) }
        val idBytes = idArr.toString().toByteArray()
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, idTableKey, idBytes))
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
        val remoteIds = mutableSetOf<String>()
        if (idsRemote != null) {
            try {
                val arr = JSONArray(idsRemote)
                for (i in 0 until arr.length()) remoteIds.add(arr.getString(i))
            } catch (_: Exception) {}
        }
        val localHistory = ChatHistoryStorage.loadAll(context)
        val localIds = localHistory.map { it.id }.toSet()
        val toUpload = localIds.minus(remoteIds)
        localHistory.filter { toUpload.contains(it.id) }.forEach { item ->
            uploadHistoryItem(context, c, b, item)
        }
        val unionIds = (localIds + remoteIds).toList()
        val idArr = JSONArray()
        unionIds.forEach { idArr.put(it) }
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, idTableKey, idArr.toString().toByteArray()))
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
        val remoteIds = mutableSetOf<String>()
        if (idsRemote != null) {
            try {
                val arr = JSONArray(idsRemote)
                for (i in 0 until arr.length()) remoteIds.add(arr.getString(i))
            } catch (_: Exception) {}
        }
        val localHistory = ChatHistoryStorage.loadAll(context)
        val localIds = localHistory.map { it.id }.toSet()
        val toDownload = remoteIds.minus(localIds)
        var changed = false
        toDownload.forEach { id ->
            val item = downloadHistoryItem(c, b, id)
            if (item != null) {
                val merged = localHistory.toMutableList().apply { add(0, item) }
                ChatHistoryStorage.saveAll(context, merged)
                changed = true
            }
        }
        if (changed) {
            // 刷新远端ID表为 union（不强制、保持远端表）
            val unionIds = (localIds + remoteIds).toList()
            val idArr = JSONArray()
            unionIds.forEach { idArr.put(it) }
            c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, idTableKey, idArr.toString().toByteArray()))
        }
    }

    fun onHistoryAdded(context: Context, item: HistoryItem) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        uploadHistoryItem(context, c, b, item)
        // Update ID table
        val localIds = ChatHistoryStorage.loadAll(context).map { it.id }
        val arr = JSONArray()
        localIds.forEach { arr.put(it) }
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, "history_ids.json", arr.toString().toByteArray()))
    }

    fun onHistoryDeleted(context: Context, deletedId: String) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        // Regenerate ID table from local
        val localIds = ChatHistoryStorage.loadAll(context).map { it.id }
        val arr = JSONArray()
        localIds.forEach { arr.put(it) }
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, "history_ids.json", arr.toString().toByteArray()))
        // 删除对应的远端独立记录文件
        try {
            c.deleteObject(com.alibaba.sdk.android.oss.model.DeleteObjectRequest(b, "history/$deletedId.json"))
        } catch (_: Exception) { /* ignore */ }
    }

    fun onHistoryUpdated(context: Context, item: HistoryItem) {
        val c = client(context) ?: return
        val b = bucket(context) ?: return
        uploadHistoryItem(context, c, b, item)
        // 刷新ID表
        val localIds = ChatHistoryStorage.loadAll(context).map { it.id }
        val arr = JSONArray()
        localIds.forEach { arr.put(it) }
        c.putObject(com.alibaba.sdk.android.oss.model.PutObjectRequest(b, "history_ids.json", arr.toString().toByteArray()))
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

    private fun downloadHistoryItem(c: OSSClient, b: String, id: String): HistoryItem? {
        return try {
            val get = com.alibaba.sdk.android.oss.model.GetObjectRequest(b, "history/$id.json")
            val result = c.getObject(get)
            val text = result.objectContent.bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            val imgBase64 = o.optString("imageBase64", "")
            var imageUrl: String? = null
            if (imgBase64.isNotBlank()) {
                // In a real app we would persist to local cache dir and reference path
                imageUrl = null
            }
            val providerStr = o.optString("providerGroup", "")
            val provider = try {
                com.glassous.aimage.ui.screens.ModelGroupType.valueOf(providerStr)
            } catch (_: Exception) {
                com.glassous.aimage.ui.screens.ModelGroupType.Google
            }
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
}