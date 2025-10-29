package com.glassous.aimage.api

import com.google.gson.annotations.SerializedName

data class DoubaoImageRequest(
    val model: String,
    val prompt: String,
    val size: String,
    @SerializedName("response_format") val responseFormat: String = "url",
    val watermark: Boolean = false
)

data class DoubaoImageResponse(
    val model: String?,
    val created: Long?,
    val data: List<DoubaoDataItem>?,
    val usage: DoubaoUsage?,
    val error: DoubaoError?
)

data class DoubaoDataItem(
    val url: String?,
    @SerializedName("b64_json") val b64Json: String?,
    val size: String?
)

data class DoubaoUsage(
    @SerializedName("generated_images") val generatedImages: Int?
)

data class DoubaoError(
    val code: String?,
    val message: String?
)