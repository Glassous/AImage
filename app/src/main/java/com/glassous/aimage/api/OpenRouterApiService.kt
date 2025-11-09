package com.glassous.aimage.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApiService {
    @POST("chat/completions")
    suspend fun createCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenRouterChatRequest
    ): Response<OpenRouterChatResponse>
}