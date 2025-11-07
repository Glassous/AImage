package com.glassous.aimage.api

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface MiniMaxApiService {
    // 文生图（T2I/I2I）创建任务（统一端点）
    @Headers("Content-Type: application/json")
    @POST("v1/image_generation")
    suspend fun generateImage(
        @Header("Authorization") authorization: String,
        @Body request: MiniMaxT2IRequest
    ): Response<JsonObject>
}