package com.glassous.aimage.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface DoubaoApiService {
    @POST("images/generations")
    suspend fun generateImage(
        @Header("Authorization") authorization: String,
        @Body request: DoubaoImageRequest
    ): Response<DoubaoImageResponse>
}