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
            目标：在不改变语义的前提下，对用户提示进行整合与润色，使内容明确、结构清晰、可执行。
            输出要求：
            - 只输出提示词文本本身，不要附加说明或前后缀。
            - 保持参数明确（如光圈、焦段、ISO、光线、风格等）。
            - 不要添加或更改分辨率、尺寸或纵横比（例如 1024x1024、4k、16:9、4:3）。
            - 若用户已包含分辨率或比例，保持原样但不要扩展或新增此类参数。
            - 不要凭空引入具体相机品牌或型号；仅在用户已明确给出时保持。
            - 中文润色用简洁、自然的现代中文；英文模式请输出自然、清晰的英文。
        """.trimIndent()
        val user = when (mode) {
            Mode.Refine -> """
                请整合并润色以下提示词与参数：
                - 禁止生成或扩展分辨率、尺寸或纵横比相关内容；仅在用户已有此项时保持原样。
                - 只输出润色后的提示词文本。
                内容：
                $source
            """.trimIndent()
            Mode.TranslateEnglish -> """
                请将以下提示词润色并翻译为英文：
                - 禁止生成或扩展分辨率、尺寸或纵横比相关内容；仅在用户已有此项时保持原样。
                - 只输出润色后的提示词文本（英文）。
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