package com.glassous.aimage.oss

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class OssConfig(
    val regionId: String,
    val endpoint: String,
    val bucket: String,
    val accessKeyId: String,
    val accessKeySecret: String
)

object OssConfigStorage {
    private const val PREF_NAME = "oss_config_prefs"
    private const val KEY_REGION = "region_id"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_BUCKET = "bucket"
    private const val KEY_AK = "access_key_id"
    private const val KEY_SK = "access_key_secret"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // OSS 配置流：保存/清空时更新，界面可实时响应
    private val _configFlow: MutableStateFlow<OssConfig?> = MutableStateFlow(null)
    val configFlow: StateFlow<OssConfig?> = _configFlow

    fun save(context: Context, cfg: OssConfig) {
        prefs(context).edit()
            .putString(KEY_REGION, cfg.regionId)
            .putString(KEY_ENDPOINT, cfg.endpoint)
            .putString(KEY_BUCKET, cfg.bucket)
            .putString(KEY_AK, cfg.accessKeyId)
            .putString(KEY_SK, cfg.accessKeySecret)
            .apply()
        _configFlow.value = cfg
    }

    fun load(context: Context): OssConfig? {
        val p = prefs(context)
        val region = p.getString(KEY_REGION, null) ?: return null
        val endpoint = p.getString(KEY_ENDPOINT, null) ?: return null
        val bucket = p.getString(KEY_BUCKET, null) ?: return null
        val ak = p.getString(KEY_AK, null) ?: return null
        val sk = p.getString(KEY_SK, null) ?: return null
        return OssConfig(region, endpoint, bucket, ak, sk)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        _configFlow.value = null
    }

    fun isConfigured(context: Context): Boolean = load(context) != null

    // 初始化流的值，避免第一次收集为 null 与实际不同步
    fun ensureInitialized(context: Context) {
        if (_configFlow.value == null) {
            _configFlow.value = load(context)
        }
    }
}