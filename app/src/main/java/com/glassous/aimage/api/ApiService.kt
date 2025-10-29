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
        )
    )
    
    // 不同厂商的模拟延迟时间（毫秒）
    private val apiDelays = mapOf(
        ModelGroupType.Google to 1500L..2500L,
        ModelGroupType.Doubao to 2000L..3000L,
        ModelGroupType.Qwen to 1800L..2800L
    )
    
    // 不同厂商的成功率（模拟网络不稳定）
    private val successRates = mapOf(
        ModelGroupType.Google to 0.95,
        ModelGroupType.Doubao to 0.90,
        ModelGroupType.Qwen to 0.92
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
            else -> generateImageMock(provider, modelName, prompt)
        }
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
        }
    }
}