package com.glassous.aimage.api

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