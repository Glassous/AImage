package com.glassous.aimage.api

import android.content.Context
import com.glassous.aimage.data.ModelConfigStorage
import com.glassous.aimage.ui.screens.ModelGroupType
import kotlinx.coroutines.delay
import kotlin.random.Random

data class ApiResponse(
    val imageUrl: String,
    val responseText: String,
    val success: Boolean,
    val errorMessage: String? = null
)

object ApiService {
    
    // 不同厂商的模拟图片池
    private val mockImages = mapOf(
        ModelGroupType.Google to listOf(
            "https://img-s.msn.cn/tenant/amp/entityid/AA1Po9Ti.img?w=599&h=337&m=6",
            "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=600&h=400",
            "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=600&h=400"
        ),
        ModelGroupType.Doubao to listOf(
            "https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=600&h=400",
            "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=600&h=400",
            "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=600&h=400"
        ),
        ModelGroupType.Qwen to listOf(
            "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=600&h=400",
            "https://images.unsplash.com/photo-1501436513145-30f24e19fcc4?w=600&h=400",
            "https://images.unsplash.com/photo-1472214103451-9374bd1c798e?w=600&h=400"
        ),
        ModelGroupType.MiniMax to listOf(
            "https://images.unsplash.com/photo-1529243856184-fd1e3c2e0c08?w=600&h=400",
            "https://images.unsplash.com/photo-1496317556649-f930d733eea0?w=600&h=400",
            "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=600&h=400"
        ),
        ModelGroupType.OpenRouter to listOf(
            "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=600&h=400",
            "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600&h=400",
            "https://images.unsplash.com/photo-1526318472351-c75fcf070305?w=600&h=400"
        )
    )
    
    // 不同厂商的模拟延迟时间（毫秒）
    private val apiDelays = mapOf(
        ModelGroupType.Google to 1500L..2500L,
        ModelGroupType.Doubao to 2000L..3000L,
        ModelGroupType.Qwen to 1800L..2800L,
        ModelGroupType.MiniMax to 1600L..2600L,
        ModelGroupType.OpenRouter to 1700L..2700L
    )
    
    // 不同厂商的成功率（模拟网络不稳定）
    private val successRates = mapOf(
        ModelGroupType.Google to 0.95,
        ModelGroupType.Doubao to 0.90,
        ModelGroupType.Qwen to 0.92,
        ModelGroupType.MiniMax to 0.93,
        ModelGroupType.OpenRouter to 0.94
    )
    
    /**
     * 根据厂商类型调用对应的API
     */
    suspend fun generateImage(
        provider: ModelGroupType,
        modelName: String,
        prompt: String,
        aspectRatio: String = "1:1",
        context: Context? = null
    ): ApiResponse {
        return when (provider) {
            ModelGroupType.Google -> {
                if (context != null) {
                    generateImageWithGemini(context, modelName, prompt, aspectRatio)
                } else {
                    generateImageMock(provider, modelName, prompt)
                }
            }
            ModelGroupType.Doubao -> {
                if (context != null) {
                    generateImageWithDoubao(context, modelName, prompt, aspectRatio)
                } else {
                    generateImageMock(provider, modelName, prompt)
                }
            }
            ModelGroupType.Qwen -> {
                if (context != null) {
                    generateImageWithQwen(context, modelName, prompt, aspectRatio)
                } else {
                    generateImageMock(provider, modelName, prompt)
                }
            }
            ModelGroupType.MiniMax -> {
                if (context != null) {
                    generateImageWithMiniMax(context, modelName, prompt, aspectRatio)
                } else {
                    generateImageMock(provider, modelName, prompt)
                }
            }
            ModelGroupType.OpenRouter -> {
                if (context != null) {
                    generateImageWithOpenRouter(context, modelName, prompt, aspectRatio)
                } else {
                    generateImageMock(provider, modelName, prompt)
                }
            }
        }
    }

    // -------- OpenRouter 文生图（Chat Completions 图片输出） --------
    private suspend fun generateImageWithOpenRouter(
        context: Context,
        modelName: String,
        prompt: String,
        aspectRatio: String
    ): ApiResponse {
        return try {
            val cleanedPrompt = prompt.trim()
            if (cleanedPrompt.isEmpty()) {
                return ApiResponse(
                    imageUrl = "",
                    responseText = "提示词为空，请输入描述后重试",
                    success = false,
                    errorMessage = "提示词为空，请输入描述后重试"
                )
            }
            var apiKey = ModelConfigStorage.loadApiKey(context, ModelGroupType.OpenRouter).trim()
            // 清洗潜在的重复 Bearer 前缀与引号
            apiKey = apiKey.trim('"').trim('\'')
            apiKey = apiKey.replace(Regex("(?i)^Bearer\\s+"), "").trim()
            if (apiKey.isEmpty()) {
                return ApiResponse(
                    imageUrl = "",
                    responseText = "",
                    success = false,
                    errorMessage = "请先在设置中配置 OpenRouter API 密钥"
                )
            }

            val request = OpenRouterChatRequest(
                model = modelName,
                messages = listOf(
                    OpenRouterMessage(
                        role = "user",
                        content = listOf(
                            // OpenRouter chat/completions 支持在 content 数组中使用 type="text" 传递输入文本
                            OpenRouterContentPart(type = "text", text = cleanedPrompt)
                        )
                    )
                ),
                modalities = listOf("text", "image"),
                image_config = OpenRouterImageConfig(aspect_ratio = aspectRatio)
            )

            val response = RetrofitClient.openRouterApiService.createCompletion(
                authorization = "Bearer ${apiKey}",
                request = request
            )

            if (response.isSuccessful) {
                val body = response.body()
                val choice = body?.choices?.firstOrNull()
                // 优先从 images 数组中解析图片（符合 OpenRouter 文生图返回格式）
                val images = choice?.message?.images.orEmpty()
                val imageUrlFromImages = images.firstOrNull { it.image_url?.url?.isNotBlank() == true }?.image_url?.url
                val contentText = choice?.message?.content?.trim()

                // 若 images 存在，直接使用
                if (!imageUrlFromImages.isNullOrBlank()) {
                    return ApiResponse(
                        imageUrl = imageUrlFromImages,
                        responseText = generateResponseText(ModelGroupType.OpenRouter, modelName, prompt),
                        success = true
                    )
                }

                // 其次尝试从文本中提取图片URL或data URI
                val extractedImageUrl = contentText?.let { extractImageUrlFromText(it) }

                if (!extractedImageUrl.isNullOrBlank()) {
                    return ApiResponse(
                        imageUrl = extractedImageUrl,
                        responseText = generateResponseText(ModelGroupType.OpenRouter, modelName, prompt),
                        success = true
                    )
                }

                if (!contentText.isNullOrBlank()) {
                    return ApiResponse(
                        imageUrl = "",
                        responseText = contentText,
                        success = false,
                        errorMessage = "API返回了文字回复但没有图片数据"
                    )
                }

                ApiResponse(
                    imageUrl = "",
                    responseText = "",
                    success = false,
                    errorMessage = "API返回了空的响应数据"
                )
            } else {
                val detail = buildHttpErrorDetail(response)
                ApiResponse(
                    imageUrl = "",
                    responseText = detail,
                    success = false,
                    errorMessage = detail
                )
            }
        } catch (e: Exception) {
            ApiResponse(
                imageUrl = "",
                responseText = "",
                success = false,
                errorMessage = "网络请求异常：${e.message}"
            )
        }
    }

    // 提取内容字符串中的图片URL或data URI
    private fun extractImageUrlFromText(content: String): String? {
        // Data URI（base64）
        val dataUriRegex = Regex("data:image/(png|jpeg|jpg|webp|gif);base64,[A-Za-z0-9+/=]+", RegexOption.IGNORE_CASE)
        dataUriRegex.find(content)?.let { return it.value }

        // JSON 字符串中包含 image_url/url 字段
        val trimmed = content.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = com.google.gson.JsonParser.parseString(trimmed).asJsonObject
                val direct = json.get("image_url")?.asString
                if (!direct.isNullOrBlank()) return direct
                val url = json.get("url")?.asString
                if (!url.isNullOrBlank()) return url
                val image = json.get("image")?.asString
                if (!image.isNullOrBlank()) return image
            } catch (_: Exception) { /* 非严格JSON，忽略 */ }
        }

        // 普通 http(s) URL（优先识别常见图片扩展名）
        val urlRegex = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
        val url = urlRegex.findAll(content).map { it.value.trimEnd('.', ',', ';') }.firstOrNull {
            it.contains(".png") || it.contains(".jpg") || it.contains(".jpeg") || it.contains(".webp") || it.contains(".gif")
        }
        return url
    }

    // 真正的Gemini API调用
    private suspend fun generateImageWithGemini(
        context: Context,
        modelName: String,
        prompt: String,
        aspectRatio: String
    ): ApiResponse {
        return try {
            val apiKey = ModelConfigStorage.loadApiKey(context, ModelGroupType.Google)
            if (apiKey.isEmpty()) {
                return ApiResponse(
                    imageUrl = "",
                    responseText = "",
                    success = false,
                    errorMessage = "请先在设置中配置Gemini API密钥"
                )
            }

            // 根据模型名称选择正确的端点：
            // - gemini-2.5-flash-image -> generateContent
            // - imagen-4.0-* -> predict
            val useGenerateContent = modelName.startsWith("gemini-")

            if (useGenerateContent) {
                val gcRequest = GeminiGenerateContentRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = prompt)
                            ),
                            role = "user"
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(
                        responseModalities = listOf("IMAGE"),
                        imageConfig = GeminiImageConfig(
                            aspectRatio = aspectRatio
                        )
                    )
                )

                val response = RetrofitClient.geminiApiService.generateContent(
                    model = modelName,
                    apiKey = apiKey,
                    request = gcRequest
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    val candidate = body?.candidates?.firstOrNull()
                    val parts = candidate?.content?.parts.orEmpty()
                    // 优先查找图片
                    val imagePart = parts.firstOrNull { it.inlineData?.data?.isNotEmpty() == true }
                    if (imagePart?.inlineData?.data != null) {
                        val mimeType = imagePart.inlineData.mimeType ?: "image/png"
                        val imageUrl = "data:${mimeType};base64,${imagePart.inlineData.data}"
                        val textPart = parts.firstOrNull { !it.text.isNullOrEmpty() }
                        val responseText = textPart?.text
                            ?: generateResponseText(ModelGroupType.Google, modelName, prompt)
                        return ApiResponse(
                            imageUrl = imageUrl,
                            responseText = responseText,
                            success = true
                        )
                    }

                    // 如果没有图片，但可能有文本
                    val textOnly = parts.firstOrNull { !it.text.isNullOrEmpty() }?.text
                    if (!textOnly.isNullOrEmpty()) {
                        return ApiResponse(
                            imageUrl = "",
                            responseText = textOnly,
                            success = false,
                            errorMessage = "API返回了文字回复但没有图片数据"
                        )
                    }

                    ApiResponse(
                        imageUrl = "",
                        responseText = "",
                        success = false,
                        errorMessage = "API返回了空的响应数据"
                    )
                } else {
                    val detail = buildHttpErrorDetail(response)
                    ApiResponse(
                        imageUrl = "",
                        responseText = detail,
                        success = false,
                        errorMessage = detail
                    )
                }
            } else {
                // Imagen predict 路径
                val request = GeminiRequest(
                    instances = listOf(GeminiInstance(prompt = prompt)),
                    parameters = GeminiParameters(
                        sampleCount = 1,
                        aspectRatio = aspectRatio,
                        personGeneration = "allow_all"
                    )
                )

                val response = RetrofitClient.geminiApiService.generateImage(
                    model = modelName,
                    apiKey = apiKey,
                    request = request
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.predictions?.isNotEmpty() == true) {
                        val prediction = body.predictions[0]
                        val base64 = prediction.bytesBase64Encoded
                        if (!base64.isNullOrEmpty()) {
                            val mimeType = prediction.mimeType ?: "image/png"
                            val imageUrl = "data:${mimeType};base64,${base64}"
                            val responseText = prediction.generatedText
                                ?: generateResponseText(ModelGroupType.Google, modelName, prompt)
                            return ApiResponse(
                                imageUrl = imageUrl,
                                responseText = responseText,
                                success = true
                            )
                        } else {
                            val responseText = prediction.generatedText
                            if (!responseText.isNullOrEmpty()) {
                                return ApiResponse(
                                    imageUrl = "",
                                    responseText = responseText,
                                    success = false,
                                    errorMessage = "API返回了文字回复但没有图片数据"
                                )
                            }
                        }
                    }

                    val errorMessage = body?.error?.message ?: "API返回了空的响应数据"
                    ApiResponse(
                        imageUrl = "",
                        responseText = "",
                        success = false,
                        errorMessage = errorMessage
                    )
                } else {
                    val detail = buildHttpErrorDetail(response)
                    ApiResponse(
                        imageUrl = "",
                        responseText = detail,
                        success = false,
                        errorMessage = detail
                    )
                }
            }
        } catch (e: Exception) {
            ApiResponse(
                imageUrl = "",
                responseText = "",
                success = false,
                errorMessage = "网络请求异常：${e.message}"
            )
        }
    }

    private fun buildHttpErrorDetail(response: retrofit2.Response<*>): String {
        val code = response.code()
        val msg = response.message()
        val raw = try {
            response.errorBody()?.string()
        } catch (_: Exception) { null }
        if (raw.isNullOrBlank()) return "HTTP ${code}: ${msg}"
        // 尝试解析标准 {"error": { code, status, message, details }}
        return try {
            val json = com.google.gson.JsonParser.parseString(raw).asJsonObject
            val err = json.getAsJsonObject("error")
            val eCode = err?.get("code")?.asString ?: code.toString()
            val eStatus = err?.get("status")?.asString
            val eMsg = err?.get("message")?.asString
            val details = err?.get("details")?.toString()
            buildString {
                append("HTTP ")
                append(code)
                append(": ")
                append(msg)
                if (!eCode.isNullOrBlank() || !eStatus.isNullOrBlank() || !eMsg.isNullOrBlank()) {
                    append("\nAPI错误：")
                    if (!eCode.isNullOrBlank()) append("code=").append(eCode).append(" ")
                    if (!eStatus.isNullOrBlank()) append("status=").append(eStatus).append(" ")
                    if (!eMsg.isNullOrBlank()) append("message=").append(eMsg)
                }
                if (!details.isNullOrBlank()) {
                    append("\n详情：").append(details)
                }
            }
        } catch (_: Exception) {
            // 兜底返回原始错误体
            "HTTP ${code}: ${msg}\n${raw}"
        }
    }

    // -------- MiniMax 文生图 --------
    private suspend fun generateImageWithMiniMax(
        context: Context,
        modelName: String,
        prompt: String,
        aspectRatio: String
    ): ApiResponse {
        return try {
            var apiKey = ModelConfigStorage.loadApiKey(context, ModelGroupType.MiniMax).trim()
            // 清洗：去左右引号与重复的 "Bearer " 前缀
            apiKey = apiKey.trim('"').trim('\'')
            apiKey = apiKey.replace(Regex("(?i)^Bearer\\s+"), "").trim()
            if (apiKey.isEmpty()) {
                return ApiResponse(
                    imageUrl = "",
                    responseText = "",
                    success = false,
                    errorMessage = "请先在设置中配置 MiniMax API 密钥"
                )
            }

            val request = MiniMaxT2IRequest(
                model = modelName,
                prompt = prompt,
                aspect_ratio = aspectRatio,
                response_format = "base64",
                n = 1,
                prompt_optimizer = false,
                aigc_watermark = false
            )

            // 首先尝试国内主机（api.minimax.chat）；如读超时则回退到全球主机
            val responseCn: retrofit2.Response<com.google.gson.JsonObject>? = try {
                RetrofitClient.minimaxApiServiceCn.generateImage(
                    authorization = "Bearer ${apiKey}",
                    request = request
                )
            } catch (toe: java.net.SocketTimeoutException) {
                null
            }

            if (responseCn != null && responseCn.isSuccessful) {
                val body = responseCn.body()
                // 依据官方文档解析 data.image_urls（response_format=url）
                // 参考: https://platform.minimax.io/docs/api-reference/image-generation-i2i
                val dataObj = try { body?.getAsJsonObject("data") } catch (_: Exception) { null }

                val urlFromData = try {
                    val arr = dataObj?.getAsJsonArray("image_urls")
                    if (arr != null && arr.size() > 0) arr.get(0).asString else dataObj?.get("image_url")?.asString
                } catch (_: Exception) { null }

                val urlFromTopLevel = try {
                    val arr = body?.getAsJsonArray("image_urls") ?: body?.getAsJsonArray("images")
                    if (arr != null && arr.size() > 0) arr.get(0).asString else null
                } catch (_: Exception) { null }

                val finalUrl = urlFromData ?: urlFromTopLevel

                if (!finalUrl.isNullOrBlank()) {
                    val saved = saveImageToAppDataFromUrl(context, finalUrl, suggestedExt = "jpg")
                    return ApiResponse(
                        imageUrl = saved,
                        responseText = generateResponseText(ModelGroupType.MiniMax, modelName, prompt),
                        success = true
                    )
                }

                // 尝试 Base64（response_format=base64）
                val base64FromData = try {
                    val arr = dataObj?.getAsJsonArray("image_base64") ?: dataObj?.getAsJsonArray("images_base64")
                    if (arr != null && arr.size() > 0) arr.get(0).asString else null
                } catch (_: Exception) { null }

                val base64Other = try {
                    val dataArr = body?.getAsJsonArray("data")
                    val firstData = if (dataArr != null && dataArr.size() > 0) dataArr.get(0).asJsonObject else null
                    firstData?.get("b64_json")?.asString ?: firstData?.get("b64_img")?.asString
                } catch (_: Exception) { null }

                val base64 = base64FromData ?: base64Other

                if (!base64.isNullOrBlank()) {
                    return try {
                        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                        val saved = saveImageToAppDataFromBytes(context, bytes, suggestedExt = "png")
                        ApiResponse(
                            imageUrl = saved,
                            responseText = generateResponseText(ModelGroupType.MiniMax, modelName, prompt),
                            success = true
                        )
                    } catch (e: Exception) {
                        ApiResponse(
                            imageUrl = "",
                            responseText = "",
                            success = false,
                            errorMessage = "解析图片数据失败：${e.message}"
                        )
                    }
                }

                // 错误详情
                val statusCode = try { body?.getAsJsonObject("base_resp")?.get("status_code")?.asInt } catch (_: Exception) { null }
                val statusMsg = try { body?.getAsJsonObject("base_resp")?.get("status_msg")?.asString } catch (_: Exception) { null }
                val errCn = if (statusCode != null && statusCode != 0) {
                    // 附加授权信息（脱敏）以便排查
                    val masked = try {
                        val shownPrefix = apiKey.take(6)
                        val shownSuffix = apiKey.takeLast(4)
                        "${shownPrefix}...${shownSuffix}"
                    } catch (_: Exception) { "(mask failed)" }
                    val extra = if ((statusMsg ?: "").contains("invalid api key", ignoreCase = true)) {
                        "；Authorization 已发送：Bearer ${masked}（长度=${apiKey.length}）"
                    } else ""
                    "MiniMax 返回错误：status_code=${statusCode}, status_msg=${statusMsg ?: ""}${extra}"
                } else {
                    "API返回了空的响应数据"
                }
                // 如果是鉴权错误（2049/invalid api key），尝试全球主机
                val shouldFallback = (statusCode == 2049) || ((statusMsg ?: "").contains("invalid api key", ignoreCase = true))
                if (!shouldFallback) {
                    return ApiResponse(
                        imageUrl = "",
                        responseText = "",
                        success = false,
                        errorMessage = errCn
                    )
                }
            } else if (responseCn != null) {
                // HTTP 失败：401/403 也尝试主机回退
                val httpCode = responseCn.code()
                val detail = buildHttpErrorDetail(responseCn)
                val shouldFallback = httpCode == 401 || httpCode == 403
                if (!shouldFallback) {
                    return ApiResponse(
                        imageUrl = "",
                        responseText = detail,
                        success = false,
                        errorMessage = detail
                    )
                }
            } // else: 国内主机直接读超时，走全球主机

            // 回退到全球主机（api.minimaxi.com）；如果国内直接超时，无需等待鉴权判断
            val responseGlobal: retrofit2.Response<com.google.gson.JsonObject> = try {
                RetrofitClient.minimaxApiServiceGlobal.generateImage(
                    authorization = "Bearer ${apiKey}",
                    request = request
                )
            } catch (toe: java.net.SocketTimeoutException) {
                // 全球也超时，返回统一错误
                return ApiResponse(
                    imageUrl = "",
                    responseText = "",
                    success = false,
                    errorMessage = "网络请求异常：timeout（国内与全球主机均超时）"
                )
            }

            if (responseGlobal.isSuccessful) {
                val body = responseGlobal.body()
                val dataObj = try { body?.getAsJsonObject("data") } catch (_: Exception) { null }

                val urlFromData = try {
                    val arr = dataObj?.getAsJsonArray("image_urls")
                    if (arr != null && arr.size() > 0) arr.get(0).asString else dataObj?.get("image_url")?.asString
                } catch (_: Exception) { null }

                val urlFromTopLevel = try {
                    val arr = body?.getAsJsonArray("image_urls") ?: body?.getAsJsonArray("images")
                    if (arr != null && arr.size() > 0) arr.get(0).asString else null
                } catch (_: Exception) { null }

                val finalUrl = urlFromData ?: urlFromTopLevel

                if (!finalUrl.isNullOrBlank()) {
                    val saved = saveImageToAppDataFromUrl(context, finalUrl, suggestedExt = "jpg")
                    return ApiResponse(
                        imageUrl = saved,
                        responseText = generateResponseText(ModelGroupType.MiniMax, modelName, prompt),
                        success = true
                    )
                }

                val base64FromData = try {
                    val arr = dataObj?.getAsJsonArray("image_base64") ?: dataObj?.getAsJsonArray("images_base64")
                    if (arr != null && arr.size() > 0) arr.get(0).asString else null
                } catch (_: Exception) { null }

                val base64FromTopLevel = try {
                    val arr = body?.getAsJsonArray("images_base64") ?: body?.getAsJsonArray("image_base64")
                    if (arr != null && arr.size() > 0) arr.get(0).asString else null
                } catch (_: Exception) { null }

                val b64 = base64FromData ?: base64FromTopLevel
                if (!b64.isNullOrBlank()) {
                    return try {
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        val saved = saveImageToAppDataFromBytes(context, bytes, suggestedExt = "png")
                        ApiResponse(
                            imageUrl = saved,
                            responseText = generateResponseText(ModelGroupType.MiniMax, modelName, prompt),
                            success = true
                        )
                    } catch (e: Exception) {
                        ApiResponse(
                            imageUrl = "",
                            responseText = "",
                            success = false,
                            errorMessage = "解析图片数据失败：${e.message}"
                        )
                    }
                }

                val statusCode = try { body?.getAsJsonObject("base_resp")?.get("status_code")?.asInt } catch (_: Exception) { null }
                val statusMsg = try { body?.getAsJsonObject("base_resp")?.get("status_msg")?.asString } catch (_: Exception) { null }
                val err = if (statusCode != null && statusCode != 0) {
                    "MiniMax 返回错误（全球主机）：status_code=${statusCode}, status_msg=${statusMsg ?: ""}"
                } else {
                    "API返回了空的响应数据"
                }

                return ApiResponse(
                    imageUrl = "",
                    responseText = "",
                    success = false,
                    errorMessage = err
                )
            } else {
                val detail = buildHttpErrorDetail(responseGlobal)
                return ApiResponse(
                    imageUrl = "",
                    responseText = detail,
                    success = false,
                    errorMessage = detail
                )
            }
        } catch (e: Exception) {
            ApiResponse(
                imageUrl = "",
                responseText = "",
                success = false,
                errorMessage = "网络请求异常：${e.message}"
            )
        }
    }

    // -------- 豆包 Doubao ARK 文生图 --------
    private suspend fun generateImageWithDoubao(
        context: Context,
        modelName: String,
        prompt: String,
        aspectRatio: String
    ): ApiResponse {
        return try {
            val apiKey = ModelConfigStorage.loadApiKey(context, ModelGroupType.Doubao)
            if (apiKey.isEmpty()) {
                return ApiResponse(
                    imageUrl = "",
                    responseText = "",
                    success = false,
                    errorMessage = "请先在设置中配置豆包 API 密钥"
                )
            }

            val size = mapDoubaoSize(aspectRatio)
            val request = DoubaoImageRequest(
                model = modelName,
                prompt = prompt,
                size = size,
                responseFormat = "url",
                watermark = false
            )

            val response = RetrofitClient.doubaoApiService.generateImage(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val body = response.body()
                val first = body?.data?.firstOrNull()
                val url = first?.url
                val b64 = first?.b64Json
                when {
                    !url.isNullOrBlank() -> {
                        ApiResponse(
                            imageUrl = url,
                            responseText = generateResponseText(ModelGroupType.Doubao, modelName, prompt),
                            success = true
                        )
                    }
                    !b64.isNullOrBlank() -> {
                        val dataUrl = "data:image/png;base64,$b64"
                        ApiResponse(
                            imageUrl = dataUrl,
                            responseText = generateResponseText(ModelGroupType.Doubao, modelName, prompt),
                            success = true
                        )
                    }
                    else -> {
                        ApiResponse(
                            imageUrl = "",
                            responseText = "",
                            success = false,
                            errorMessage = "API返回为空或未包含图片数据"
                        )
                    }
                }
            } else {
                val detail = buildHttpErrorDetail(response)
                ApiResponse(
                    imageUrl = "",
                    responseText = detail,
                    success = false,
                    errorMessage = detail
                )
            }
        } catch (e: Exception) {
            ApiResponse(
                imageUrl = "",
                responseText = e.message ?: "未知错误",
                success = false,
                errorMessage = e.message
            )
        }
    }

    private fun mapDoubaoSize(aspectRatio: String): String {
        return when (aspectRatio) {
            "1:1" -> "1024x1024"
            "3:4" -> "768x1024"
            "4:3" -> "1024x768"
            "9:16" -> "1024x1792"
            else -> "1024x1024"
        }
    }

    // -------- Qwen (DashScope) 文生图：异步任务 + 轮询 + 本地落盘 --------
    private suspend fun generateImageWithQwen(
        context: Context,
        modelName: String,
        prompt: String,
        aspectRatio: String
    ): ApiResponse {
        return try {
            // 若选择的是通义千问图像模型（qwen-image / qwen-image-plus），直接走千问同步生成通道
            if (modelName.startsWith("qwen-image")) {
                return generateImageWithQwenImageSync(context, modelName, prompt, aspectRatio)
            }

            val apiKey = ModelConfigStorage.loadApiKey(context, ModelGroupType.Qwen)
            if (apiKey.isEmpty()) {
                return ApiResponse(
                    imageUrl = "",
                    responseText = "",
                    success = false,
                    errorMessage = "请先在设置中配置Qwen API密钥"
                )
            }

            val size = mapQwenSize(aspectRatio)
            val request = QwenImageSynthesisRequest(
                model = modelName,
                input = QwenInput(prompt = prompt),
                parameters = QwenParameters(
                    size = size,
                    n = 1,
                    promptExtend = true,
                    watermark = true
                )
            )

            val createResp = RetrofitClient.qwenApiService.startImageSynthesis(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!createResp.isSuccessful) {
                // 万相创建任务失败，回退到通义千问（qwen-image-plus）
                return tryFallbackToQianwenImage(context, prompt, aspectRatio)
            }

            val bodyCreate = createResp.body()
            val taskId = bodyCreate?.taskId
                ?: bodyCreate?.output?.taskId
                ?: extractTaskIdFromLocation(createResp.headers()["Location"])
            if (taskId.isNullOrBlank()) {
                // 万相返回未包含 task_id，回退到通义千问
                return tryFallbackToQianwenImage(context, prompt, aspectRatio)
            }

            // 轮询查询
            val start = System.currentTimeMillis()
            val timeoutMs = 90_000L
            val pollIntervalMs = 2_000L
            var finalUrl: String? = null
            var finalBase64: String? = null
            var lastError: String? = null

            while (System.currentTimeMillis() - start < timeoutMs) {
                kotlinx.coroutines.delay(pollIntervalMs)
                val taskResp = RetrofitClient.qwenApiService.getTask(
                    authorization = "Bearer $apiKey",
                    taskId = taskId
                )

                if (!taskResp.isSuccessful) {
                    lastError = buildHttpErrorDetail(taskResp)
                    continue
                }
                val body = taskResp.body()
                val status = body?.output?.taskStatus?.uppercase() ?: ""
                if (status == "SUCCEEDED") {
                    val results = body?.output?.results.orEmpty()
                    val first = results.firstOrNull()
                    finalUrl = first?.url ?: first?.imageUrl
                    finalBase64 = first?.b64Json ?: first?.imageBase64
                    break
                } else if (status == "FAILED") {
                    // 任务失败，回退到通义千问
                    return tryFallbackToQianwenImage(context, prompt, aspectRatio)
                } else {
                    // PENDING/RUNNING，继续轮询
                }
            }

            if (finalUrl.isNullOrBlank() && finalBase64.isNullOrBlank()) {
                // 未取到结果，回退到通义千问
                return tryFallbackToQianwenImage(context, prompt, aspectRatio)
            }

            // 将图片立即保存到应用数据（避免24小时后失效）
            val savedFileUrl = try {
                if (!finalUrl.isNullOrBlank()) {
                    saveImageToAppDataFromUrl(context, finalUrl, suggestedExt = "jpg")
                } else {
                    val raw = android.util.Base64.decode(finalBase64, android.util.Base64.DEFAULT)
                    saveImageToAppDataFromBytes(context, raw, suggestedExt = "png")
                }
            } catch (e: Exception) {
                // 保存失败则退回直接使用远程URL/数据URL（不理想，但不阻断显示）
                if (!finalUrl.isNullOrBlank()) finalUrl else "data:image/png;base64,${finalBase64}"
            }

            ApiResponse(
                imageUrl = savedFileUrl,
                responseText = generateResponseText(ModelGroupType.Qwen, modelName, prompt),
                success = true
            )
        } catch (e: Exception) {
            // 异常时回退到通义千问
            tryFallbackToQianwenImage(context, prompt, aspectRatio)
        }
    }

    private fun mapQwenSize(aspectRatio: String): String {
        // DashScope常见可用尺寸，若不确定则回落到 1024*1024
        return when (aspectRatio) {
            "1:1" -> "1024*1024"
            "3:4" -> "768*1024"
            "4:3" -> "1024*768"
            "9:16" -> "1024*1792"
            else -> "1024*1024"
        }
    }

    // 通义千问（Qwen-Image 系列）尺寸映射（以官方示例分辨率为参考）
    private fun mapQwenImageSize(aspectRatio: String): String {
        return when (aspectRatio) {
            "1:1" -> "1328*1328"
            "3:4" -> "1140*1472"
            "4:3" -> "1472*1140"
            "9:16" -> "928*1664"
            else -> "1328*1328"
        }
    }

    // 通义千问（Qwen-Image / qwen-image-plus）同步生成
    private suspend fun generateImageWithQwenImageSync(
        context: Context,
        modelName: String,
        prompt: String,
        aspectRatio: String
    ): ApiResponse {
        return try {
            val apiKey = ModelConfigStorage.loadApiKey(context, ModelGroupType.Qwen)
            if (apiKey.isEmpty()) {
                return ApiResponse(
                    imageUrl = "",
                    responseText = "",
                    success = false,
                    errorMessage = "请先在设置中配置Qwen API密钥"
                )
            }

            val request = QwenImageGenRequest(
                model = modelName,
                input = QwenImageGenInput(
                    messages = listOf(
                        QwenImageGenMessage(
                            role = "user",
                            content = listOf(QwenImageGenContent(text = prompt))
                        )
                    )
                ),
                parameters = QwenImageGenParameters(
                    size = mapQwenImageSize(aspectRatio),
                    n = 1,
                    promptExtend = true,
                    watermark = true,
                    negativePrompt = null
                )
            )

            val resp = RetrofitClient.qwenApiService.generateImageWithQwenImage(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!resp.isSuccessful) {
                val detail = buildHttpErrorDetail(resp)
                return ApiResponse(
                    imageUrl = "",
                    responseText = detail,
                    success = false,
                    errorMessage = detail
                )
            }

            val body = resp.body()
            val contents = body?.output?.choices?.firstOrNull()?.message?.content.orEmpty()
            val url = contents.firstOrNull { !it.image.isNullOrBlank() || !it.url.isNullOrBlank() }?.let { it.image ?: it.url }
            val b64 = contents.firstOrNull { !it.b64Json.isNullOrBlank() }?.b64Json

            if (url.isNullOrBlank() && b64.isNullOrBlank()) {
                return ApiResponse(
                    imageUrl = "",
                    responseText = "未返回图片数据",
                    success = false,
                    errorMessage = "未返回图片数据"
                )
            }

            val saved = try {
                if (!url.isNullOrBlank()) {
                    // 远程URL或dataURL
                    if (url.startsWith("http")) {
                        saveImageToAppDataFromUrl(context, url, suggestedExt = "jpg")
                    } else {
                        // 例如 data:image/png;base64,xxx
                        if (url.startsWith("data:")) {
                            val base64Part = url.substringAfter("base64,", "")
                            if (base64Part.isNotEmpty()) {
                                val bytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                                saveImageToAppDataFromBytes(context, bytes, suggestedExt = "png")
                            } else url
                        } else url
                    }
                } else {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    saveImageToAppDataFromBytes(context, bytes, suggestedExt = "png")
                }
            } catch (_: Exception) {
                url ?: "data:image/png;base64,$b64"
            }

            ApiResponse(
                imageUrl = saved,
                responseText = generateResponseText(ModelGroupType.Qwen, modelName, prompt),
                success = true
            )
        } catch (e: Exception) {
            ApiResponse(
                imageUrl = "",
                responseText = e.message ?: "未知错误",
                success = false,
                errorMessage = e.message
            )
        }
    }

    // 万相失败时，自动回退到通义千问 qwen-image-plus
    private suspend fun tryFallbackToQianwenImage(
        context: Context,
        prompt: String,
        aspectRatio: String
    ): ApiResponse {
        // 默认使用 qwen-image-plus 作为回退模型
        return generateImageWithQwenImageSync(
            context = context,
            modelName = "qwen-image-plus",
            prompt = prompt,
            aspectRatio = aspectRatio
        )
    }

    private fun extractTaskIdFromLocation(location: String?): String? {
        if (location.isNullOrBlank()) return null
        val trimmed = location.trim()
        val idx = trimmed.lastIndexOf('/')
        if (idx == -1 || idx == trimmed.length - 1) return null
        val last = trimmed.substring(idx + 1)
        return last.takeIf { it.isNotBlank() }
    }

    private suspend fun saveImageToAppDataFromUrl(
        context: Context,
        url: String,
        suggestedExt: String = "jpg"
    ): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = java.io.File(context.filesDir, "qwen_images").apply { mkdirs() }
            val file = java.io.File(dir, "qwen_${System.currentTimeMillis()}.$suggestedExt")
            java.net.URL(url).openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            "file://${file.absolutePath}"
        }
    }

    private suspend fun saveImageToAppDataFromBytes(
        context: Context,
        bytes: ByteArray,
        suggestedExt: String = "png"
    ): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = java.io.File(context.filesDir, "qwen_images").apply { mkdirs() }
            val file = java.io.File(dir, "qwen_${System.currentTimeMillis()}.$suggestedExt")
            file.outputStream().use { it.write(bytes) }
            "file://${file.absolutePath}"
        }
    }

    // 模拟API调用（用于其他厂商或测试）
    private suspend fun generateImageMock(
        provider: ModelGroupType,
        modelName: String,
        prompt: String
    ): ApiResponse {
        return try {
            // 模拟不同厂商的网络延迟
            val delayRange = apiDelays[provider] ?: (2000L..3000L)
            val delay = Random.nextLong(delayRange.first, delayRange.last)
            delay(delay)

            // 模拟网络失败
            val successRate = successRates[provider] ?: 0.9
            if (Random.nextDouble() > successRate) {
                return ApiResponse(
                    imageUrl = "",
                    responseText = "",
                    success = false,
                    errorMessage = "网络请求失败，请重试"
                )
            }

            // 随机选择该厂商的模拟图片
            val images = mockImages[provider] ?: mockImages[ModelGroupType.Google]!!
            val randomImage = images[Random.nextInt(images.size)]

            // 生成厂商特定的响应文本
            val responseText = generateResponseText(provider, modelName, prompt)

            ApiResponse(
                imageUrl = randomImage,
                responseText = responseText,
                success = true
            )

        } catch (e: Exception) {
            ApiResponse(
                imageUrl = "",
                responseText = "",
                success = false,
                errorMessage = "API调用异常：${e.message}"
            )
        }
    }
    
    /**
     * 生成厂商特定的响应文本
     */
    private fun generateResponseText(
        provider: ModelGroupType,
        modelName: String,
        prompt: String
    ): String {
        val providerPrefix = when (provider) {
            ModelGroupType.Google -> "Gemini"
            ModelGroupType.Doubao -> "豆包"
            ModelGroupType.Qwen -> "通义千问"
            ModelGroupType.MiniMax -> "MiniMax"
            ModelGroupType.OpenRouter -> "OpenRouter"
        }
        
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
        
        return "[$providerPrefix-$modelName] 于 $timestamp 完成生成\n生成说明：根据您的描述已生成预览图片，点击图片可查看详情并保存到本地。"
    }
    
    /**
     * 检查厂商API是否可用（模拟）
     */
    fun isProviderAvailable(provider: ModelGroupType): Boolean {
        // 模拟某些厂商偶尔不可用
        return when (provider) {
            ModelGroupType.Google -> Random.nextDouble() > 0.05 // 95% 可用率
            ModelGroupType.Doubao -> Random.nextDouble() > 0.08 // 92% 可用率
            ModelGroupType.Qwen -> Random.nextDouble() > 0.06   // 94% 可用率
            ModelGroupType.MiniMax -> Random.nextDouble() > 0.07 // 93% 可用率
            ModelGroupType.OpenRouter -> Random.nextDouble() > 0.06 // 94% 可用率
        }
    }
}