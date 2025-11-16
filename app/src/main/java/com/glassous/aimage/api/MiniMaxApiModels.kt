package com.glassous.aimage.api

data class MiniMaxT2IRequest(
    val model: String,
    val prompt: String,
    val aspect_ratio: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val image_url: String? = null,
    val image_base64: String? = null,
    val response_format: String = "url",
    val seed: Int? = null,
    val n: Int = 1,
    val prompt_optimizer: Boolean = false,
    val aigc_watermark: Boolean = false
)