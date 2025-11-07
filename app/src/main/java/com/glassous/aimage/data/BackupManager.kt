package com.glassous.aimage.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.glassous.aimage.ui.screens.HistoryItem
import com.glassous.aimage.ui.screens.ModelGroupType
import com.glassous.aimage.ui.screens.UserModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.URLConnection

/**
 * 数据备份/恢复管理器：导出/导入模型配置、API Key、默认模型与历史记录（含图片Base64）。
 */
object BackupManager {
    data class RestoreResult(
        val modelsRestored: Int,
        val historyRestored: Int
    )

    suspend fun createBackup(context: Context): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)

        // 模型配置与API Key
        val apiKeys = JSONObject()
        val models = JSONObject()
        ModelGroupType.values().forEach { group ->
            apiKeys.put(group.name, ModelConfigStorage.loadApiKey(context, group))
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
        val defaultRef = ModelConfigStorage.loadDefaultModel(context)
        val defaultObj = if (defaultRef != null) JSONObject().apply {
            put("group", defaultRef.group.name)
            put("modelName", defaultRef.modelName)
        } else JSONObject.NULL

        val modelConfigs = JSONObject()
        modelConfigs.put("apiKeys", apiKeys)
        modelConfigs.put("models", models)
        modelConfigs.put("default", defaultObj)
        root.put("modelConfigs", modelConfigs)

        // 历史记录（包含图片Base64）
        val historyArr = JSONArray()
        ChatHistoryStorage.loadAll(context).forEach { h ->
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("prompt", h.prompt)
            obj.put("timestamp", h.timestamp)
            obj.put("model", h.model)
            obj.put("providerGroup", h.provider.name)
            if (h.imageUrl != null) obj.put("imageUrl", h.imageUrl) else obj.put("imageUrl", JSONObject.NULL)

            // 尝试读取图片并转为Base64
            val imageData = safeReadImageBytes(context, h.imageUrl)
            if (imageData != null) {
                val (bytes, mime) = imageData
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                obj.put("imageBase64", b64)
                if (mime != null) obj.put("imageMime", mime)
            }
            historyArr.put(obj)
        }
        root.put("history", historyArr)

        root.toString()
    }

    suspend fun restoreBackup(context: Context, json: String): RestoreResult = withContext(Dispatchers.IO) {
        val root = JSONObject(json)

        // 恢复模型配置与API Key
        val modelConfigs = root.optJSONObject("modelConfigs") ?: JSONObject()
        val apiKeys = modelConfigs.optJSONObject("apiKeys") ?: JSONObject()
        val models = modelConfigs.optJSONObject("models") ?: JSONObject()
        var modelsRestored = 0
        ModelGroupType.values().forEach { group ->
            val key = apiKeys.optString(group.name, "")
            ModelConfigStorage.saveApiKey(context, group, key)

            val arr = models.optJSONArray(group.name) ?: JSONArray()
            val list = mutableListOf<UserModel>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(UserModel(o.optString("name", ""), o.optString("displayName", ""), o.optString("note", "")))
            }
            ModelConfigStorage.saveModels(context, group, list)
            modelsRestored += list.size
        }
        val defaultObj = modelConfigs.optJSONObject("default")
        if (defaultObj != null) {
            val groupStr = defaultObj.optString("group", "")
            val modelName = defaultObj.optString("modelName", "")
            try {
                val group = ModelGroupType.valueOf(groupStr)
                if (modelName.isNotBlank()) {
                    ModelConfigStorage.saveDefaultModel(context, group, modelName)
                }
            } catch (_: Exception) { /* ignore invalid group */ }
        }

        // 恢复历史记录：若存在Base64则写入本地文件并用file路径作为imageUrl
        val historyArr = root.optJSONArray("history") ?: JSONArray()
        val restored = mutableListOf<HistoryItem>()
        val imagesDir = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }
        for (i in 0 until historyArr.length()) {
            try {
                val obj = historyArr.getJSONObject(i)
                val id = obj.optString("id", System.currentTimeMillis().toString())
                val prompt = obj.optString("prompt", "")
                val timestamp = obj.optString("timestamp", "")
                val model = obj.optString("model", "")
                val providerGroup = obj.optString("providerGroup", "Google")
                val imageUrlRaw = obj.optString("imageUrl", null)
                var finalImageUrl: String? = imageUrlRaw

                val b64 = obj.optString("imageBase64", "")
                if (b64.isNotBlank()) {
                    val mime = obj.optString("imageMime", "image/png")
                    val ext = when {
                        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                        mime.contains("webp") -> "webp"
                        mime.contains("gif") -> "gif"
                        else -> "png"
                    }
                    val name = if (id.isNotBlank()) id else "img_${System.currentTimeMillis()}_$i"
                    val f = File(imagesDir, "$name.$ext")
                    try {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        f.writeBytes(bytes)
                        finalImageUrl = f.absolutePath // Coil 支持直接加载文件路径字符串
                    } catch (_: Exception) { /* ignore */ }
                }

                val provider = parseProviderCompat(providerGroup)
                restored.add(
                    HistoryItem(
                        id = id,
                        prompt = prompt,
                        imageUrl = finalImageUrl,
                        timestamp = timestamp,
                        model = model,
                        provider = provider
                    )
                )
            } catch (_: Exception) { /* ignore bad entry */ }
        }
        ChatHistoryStorage.saveAll(context, restored)
        RestoreResult(modelsRestored = modelsRestored, historyRestored = restored.size)
    }

    // 读取图片为字节数组与MIME类型（支持 http/https、content://、file 路径）
    private fun safeReadImageBytes(context: Context, url: String?): Pair<ByteArray, String?>? {
        if (url.isNullOrBlank()) return null
        return try {
            when {
                url.startsWith("http://") || url.startsWith("https://") -> {
                    URL(url).openStream().use { ins ->
                        val bytes = ins.readAllBytesCompat()
                        val mime = try { URLConnection.guessContentTypeFromStream(bytes.inputStreamCompat()) } catch (_: Exception) { null }
                        bytes to mime
                    }
                }
                url.startsWith("content://") -> {
                    val uri = Uri.parse(url)
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        val bytes = ins.readAllBytesCompat()
                        val mime = context.contentResolver.getType(uri)
                        bytes to mime
                    }
                }
                else -> {
                    // 允许直接文件路径或 file://
                    val path = if (url.startsWith("file://")) url.removePrefix("file://") else url
                    val f = File(path)
                    if (!f.exists()) return null
                    val bytes = f.readBytes()
                    val mime = when {
                        path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
                        path.endsWith(".png", true) -> "image/png"
                        path.endsWith(".webp", true) -> "image/webp"
                        path.endsWith(".gif", true) -> "image/gif"
                        else -> null
                    }
                    bytes to mime
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // 与 ChatHistoryStorage 的 provider 解析保持兼容
    private fun parseProviderCompat(value: String?): ModelGroupType {
        if (value.isNullOrBlank()) return ModelGroupType.Google
        try { return ModelGroupType.valueOf(value) } catch (_: Exception) { }
        val normalized = value.lowercase()
        return when {
            normalized.contains("gemini") || normalized.contains("google") -> ModelGroupType.Google
            normalized.contains("doubao") || normalized.contains("豆包") || normalized.contains("volc") -> ModelGroupType.Doubao
            normalized.contains("qwen") || normalized.contains("阿里") || normalized.contains("ali") -> ModelGroupType.Qwen
            normalized.contains("minimax") -> ModelGroupType.MiniMax
            else -> ModelGroupType.Google
        }
    }
}

// 兼容性：JDK 8 无 readAllBytes，补充扩展
private fun InputStream.readAllBytesCompat(): ByteArray {
    return try {
        this.readBytes()
    } catch (_: Throwable) {
        val buffer = ByteArray(8 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var n: Int
        while (true) {
            n = this.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    }
}

private fun ByteArray.inputStreamCompat(): InputStream = java.io.ByteArrayInputStream(this)