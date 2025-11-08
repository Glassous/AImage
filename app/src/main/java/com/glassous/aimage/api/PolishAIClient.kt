package com.glassous.aimage.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject

object PolishAIClient {
    data class Config(
        val baseUrl: String,
        val apiKey: String,
        val model: String
    )

    enum class Mode { Refine, TranslateEnglish }

    sealed class Event {
        data class Chunk(val text: String) : Event()
        data class Error(val message: String) : Event()
        object Completed : Event()
    }

    data class Handle(val cancel: () -> Unit)

    private fun buildUrl(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (trimmed.contains("chat/completions")) trimmed else "$trimmed/v1/chat/completions"
    }

    private fun buildBody(model: String, source: String, mode: Mode): String {
        val system = """
            你是资深提示词工程师，专注为文生图/摄影风格生成高质量提示词。
            任务：在保持原始语义与关键要素的前提下进行重写与优化，使表达更简洁有力、层次清晰，便于模型执行。
            写作原则：
            - 只输出提示词文本，不附加说明或前后缀。
            - 保留并突出主体、场景、动作、材质、色彩、光线、镜头参数等信息。
            - 允许重组语序、替换近义词、加强风格与光线描述，使措辞更鲜明。
            - 不更改或新增分辨率、尺寸或纵横比（例如 1024x1024、4k、16:9、4:3）。
            - 若原文包含品牌、分辨率或比例，保持原样。
            - 不凭空加入具体相机品牌或型号；仅在用户已明确给出时保持。
            - 中文使用自然、现代的表达；英文使用清晰、自然的表达。
        """.trimIndent()
        val user = when (mode) {
            Mode.Refine -> """
                请重写并优化以下提示词：去除冗余、合并重复，使用并列短语与逗号分隔；
                适度替换近义词并强化风格/光线/质感描述，但不要更改或新增分辨率、尺寸或纵横比；
                只输出优化后的提示词文本。
                内容：
                $source
            """.trimIndent()
            Mode.TranslateEnglish -> """
                请将以下提示词重写优化并翻译为英文：保持语义与关键要素，
                使用自然清晰的英文表达；不要更改或新增分辨率、尺寸或纵横比；只输出优化后的英文提示词文本。
                内容：
                $source
            """.trimIndent()
        }
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", system) })
            put(JSONObject().apply { put("role", "user"); put("content", user) })
        }
        return JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("stream", true)
        }.toString()
    }

    fun streamRefine(cfg: Config, source: String, mode: Mode): Pair<Flow<Event>, Handle> {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val url = buildUrl(cfg.baseUrl)
        val body = buildBody(cfg.model, source, mode)
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${cfg.apiKey}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)

        val flow = callbackFlow<Event> {
            val job = launch(Dispatchers.IO) {
                try {
                    val response = call.execute()
                    if (!response.isSuccessful) {
                        val msg = "HTTP ${response.code}: ${response.message}" +
                                (response.body?.string()?.let { "\n$it" } ?: "")
                        trySend(Event.Error(msg))
                        return@launch
                    }
                    val bodyResp = response.body ?: run {
                        trySend(Event.Error("空响应体"))
                        return@launch
                    }
                    val sourceBuf: BufferedSource = bodyResp.source()
                    while (!sourceBuf.exhausted()) {
                        val line = sourceBuf.readUtf8Line() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        if (trimmed.startsWith("data:")) {
                            val data = trimmed.removePrefix("data:").trim()
                            if (data == "[DONE]") {
                                trySend(Event.Completed)
                                break
                            }
                            try {
                                val json = JSONObject(data)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val c0 = choices.getJSONObject(0)
                                    val delta = c0.optJSONObject("delta")
                                    val message = c0.optJSONObject("message")
                                    val content = when {
                                        delta != null -> delta.optString("content")
                                        message != null -> message.optString("content")
                                        else -> c0.optString("content")
                                    }
                                    if (!content.isNullOrBlank()) {
                                        trySend(Event.Chunk(content))
                                    }
                                }
                            } catch (e: Exception) {
                                trySend(Event.Error("解析流事件失败：${e.message}"))
                            }
                        }
                    }
                } catch (e: Exception) {
                    trySend(Event.Error("网络异常：${e.message}"))
                }
            }

            awaitClose {
                try {
                    call.cancel()
                } catch (_: Exception) { }
                job.cancel()
            }
        }

        return flow to Handle { try { call.cancel() } catch (_: Exception) { } }
    }
}