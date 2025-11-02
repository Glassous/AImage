package com.glassous.aimage.data

import android.content.Context
import com.glassous.aimage.ui.screens.HistoryItem
import com.glassous.aimage.ui.screens.ModelGroupType
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ChatHistoryStorage {
    private const val PREF_NAME = "chat_history_prefs"
    private const val KEY_HISTORY = "history_list"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun JSONObject.getStringOrNull(key: String): String? {
        return if (this.has(key) && !this.isNull(key)) {
            val v = this.optString(key, "")
            if (v.isBlank() || v.equals("null", ignoreCase = true)) null else v
        } else null
    }

    // 实时数据流，供界面订阅以自动刷新
    private val _historyFlow = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyFlow: StateFlow<List<HistoryItem>> = _historyFlow

    private fun parseTimestampToMillis(ts: String): Long {
        return try {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
            fmt.parse(ts)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun loadAll(context: Context): MutableList<HistoryItem> {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<HistoryItem>()
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val provider = parseProvider(obj.optString("providerGroup", ""))
                    val imageUrl = obj.getStringOrNull("imageUrl")
                    list.add(
                        HistoryItem(
                            id = obj.optString("id", ""),
                            prompt = obj.optString("prompt", ""),
                            imageUrl = imageUrl,
                            timestamp = obj.optString("timestamp", ""),
                            model = obj.optString("model", ""),
                            provider = provider
                        )
                    )
                } catch (_: Exception) {
                    // 跳过有问题的条目，继续解析
                }
            }
            // 统一按时间降序（最新在上），时间缺失时按 id 降序兜底
            val sorted = list.sortedWith(compareByDescending<HistoryItem> {
                parseTimestampToMillis(it.timestamp)
            }.thenByDescending {
                it.id.toLongOrNull() ?: 0L
            })
            // 更新流以通知订阅者
            _historyFlow.value = sorted
            sorted.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveAll(context: Context, items: List<HistoryItem>) {
        try {
            // 保存前统一排序为时间降序，保证界面与云端合并后的顺序一致
            val sorted = items.sortedWith(compareByDescending<HistoryItem> {
                parseTimestampToMillis(it.timestamp)
            }.thenByDescending {
                it.id.toLongOrNull() ?: 0L
            })
            val arr = JSONArray()
            sorted.forEach { h ->
                val obj = JSONObject()
                obj.put("id", h.id)
                obj.put("prompt", h.prompt)
                // 允许 imageUrl 为 null，写入 JSONObject.NULL，以兼容读取逻辑
                if (h.imageUrl == null) obj.put("imageUrl", JSONObject.NULL) else obj.put("imageUrl", h.imageUrl)
                obj.put("timestamp", h.timestamp)
                obj.put("model", h.model)
                obj.put("providerGroup", h.provider.name)
                arr.put(obj)
            }
            prefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
            // 更新流以通知订阅者
            _historyFlow.value = sorted
        } catch (_: Exception) {
            // 忽略持久化异常，防止影响运行
        }
    }
}
    private fun parseProvider(value: String?): ModelGroupType {
        if (value.isNullOrBlank()) return ModelGroupType.Google
        // 1) Try enum name exact
        try {
            return ModelGroupType.valueOf(value)
        } catch (_: Exception) { /* ignore */ }
        // 2) Try displayName or common aliases
        val normalized = value.lowercase()
        return when {
            normalized.contains("gemini") || normalized.contains("google") -> ModelGroupType.Google
            normalized.contains("doubao") || normalized.contains("豆包") || normalized.contains("volc") -> ModelGroupType.Doubao
            normalized.contains("qwen") || normalized.contains("阿里") || normalized.contains("ali") -> ModelGroupType.Qwen
            else -> ModelGroupType.Google
        }
    }