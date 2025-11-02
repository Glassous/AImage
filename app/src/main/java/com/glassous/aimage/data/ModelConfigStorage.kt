package com.glassous.aimage.data

import android.content.Context
import com.glassous.aimage.ui.screens.ModelGroupType
import com.glassous.aimage.ui.screens.UserModel
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ModelConfigStorage {
    private const val PREF_NAME = "model_config_prefs"
    private const val KEY_DEFAULT_GROUP = "default_group"
    private const val KEY_DEFAULT_MODEL = "default_model"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // 全局版本流：每次配置变更自增，供界面订阅后重新读取本地数据
    private val _versionFlow: MutableStateFlow<Long> = MutableStateFlow(0L)
    val versionFlow: StateFlow<Long> = _versionFlow

    private fun keyApi(group: ModelGroupType) = "api_" + group.name
    private fun keyModels(group: ModelGroupType) = "models_" + group.name

    fun loadApiKey(context: Context, group: ModelGroupType): String {
        return prefs(context).getString(keyApi(group), "") ?: ""
    }

    fun saveApiKey(context: Context, group: ModelGroupType, apiKey: String) {
        prefs(context).edit().putString(keyApi(group), apiKey).apply()
        _versionFlow.value = _versionFlow.value + 1
    }

    fun loadModels(context: Context, group: ModelGroupType): MutableList<UserModel> {
        val raw = prefs(context).getString(keyModels(group), null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<UserModel>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    UserModel(
                        name = obj.optString("name", ""),
                        displayName = obj.optString("displayName", ""),
                        note = obj.optString("note", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveModels(context: Context, group: ModelGroupType, models: List<UserModel>) {
        val arr = JSONArray()
        models.forEach { m ->
            val obj = JSONObject()
            obj.put("name", m.name)
            obj.put("displayName", m.displayName)
            obj.put("note", m.note)
            arr.put(obj)
        }
        prefs(context).edit().putString(keyModels(group), arr.toString()).apply()
        _versionFlow.value = _versionFlow.value + 1
    }

    data class DefaultModelRef(val group: ModelGroupType, val modelName: String)

    fun saveDefaultModel(context: Context, group: ModelGroupType, modelName: String) {
        prefs(context).edit()
            .putString(KEY_DEFAULT_GROUP, group.name)
            .putString(KEY_DEFAULT_MODEL, modelName)
            .apply()
        _versionFlow.value = _versionFlow.value + 1
    }

    fun loadDefaultModel(context: Context): DefaultModelRef? {
        val groupStr = prefs(context).getString(KEY_DEFAULT_GROUP, null) ?: return null
        val modelName = prefs(context).getString(KEY_DEFAULT_MODEL, null) ?: return null
        val group = try {
            ModelGroupType.valueOf(groupStr)
        } catch (_: Exception) {
            return null
        }
        return DefaultModelRef(group, modelName)
    }

    fun clearDefaultModel(context: Context) {
        prefs(context).edit()
            .remove(KEY_DEFAULT_GROUP)
            .remove(KEY_DEFAULT_MODEL)
            .apply()
        _versionFlow.value = _versionFlow.value + 1
    }
}