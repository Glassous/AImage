package com.glassous.aimage.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface QwenApiService {
    @POST("api/v1/services/aigc/text2image/image-synthesis")
    suspend fun startImageSynthesis(
        @Header("Authorization") authorization: String,
        @Header("X-DashScope-Async") asyncHeader: String = "enable",
        @Body request: QwenImageSynthesisRequest
    ): Response<QwenTaskCreateResponse>

    @GET("api/v1/tasks/{taskId}")
    suspend fun getTask(
        @Header("Authorization") authorization: String,
        @Path("taskId") taskId: String
    ): Response<QwenTaskQueryResponse>

    // 通义千问 Qwen-Image / qwen-image-plus 同步生成
    @POST("api/v1/services/aigc/multimodal-generation/generation")
    suspend fun generateImageWithQwenImage(
        @Header("Authorization") authorization: String,
        @Body request: QwenImageGenRequest
    ): Response<QwenImageGenResponse>
}