package com.glassous.aimage.api

import com.google.gson.annotations.SerializedName

data class QwenImageSynthesisRequest(
    val model: String,
    val input: QwenInput,
    val parameters: QwenParameters
)

data class QwenInput(
    val prompt: String
)

data class QwenParameters(
    val size: String,
    val n: Int = 1,
    @SerializedName("prompt_extend") val promptExtend: Boolean = true,
    val watermark: Boolean = true
)

// Create task response
data class QwenTaskCreateResponse(
    @SerializedName("task_id") val taskId: String?,
    val output: QwenTaskOutput?,
    @SerializedName("request_id") val requestId: String?,
    val code: String? = null,
    val message: String? = null,
)

// Query task response
data class QwenTaskQueryResponse(
    @SerializedName("task_id") val taskId: String?,
    val output: QwenTaskOutput?,
    val code: String? = null,
    val message: String? = null,
)

data class QwenTaskOutput(
    @SerializedName("task_id") val taskId: String? = null,
    @SerializedName("task_status") val taskStatus: String?,
    val results: List<QwenResultItem>? = null,
    @SerializedName("error_code") val errorCode: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class QwenResultItem(
    // Prefer official url field; fallbacks cover potential fields
    val url: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("b64_json") val b64Json: String? = null,
    @SerializedName("image_base64") val imageBase64: String? = null
)

// ================= 通义千问（Qwen-Image / qwen-image-plus）同步生成 =================

// Request
data class QwenImageGenRequest(
    val model: String,
    val input: QwenImageGenInput,
    val parameters: QwenImageGenParameters? = null
)

data class QwenImageGenInput(
    val messages: List<QwenImageGenMessage>
)

data class QwenImageGenMessage(
    val role: String = "user",
    val content: List<QwenImageGenContent>
)

data class QwenImageGenContent(
    val text: String? = null
)

data class QwenImageGenParameters(
    val size: String? = null,
    @SerializedName("n") val n: Int? = 1,
    @SerializedName("prompt_extend") val promptExtend: Boolean? = true,
    val watermark: Boolean? = true,
    @SerializedName("negative_prompt") val negativePrompt: String? = null
)

// Response
data class QwenImageGenResponse(
    @SerializedName("requestId") val requestId: String? = null,
    val usage: Any? = null,
    val output: QwenImageGenOutput? = null
)

data class QwenImageGenOutput(
    val choices: List<QwenImageGenChoice>? = null
)

data class QwenImageGenChoice(
    val message: QwenImageGenMessageResult? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class QwenImageGenMessageResult(
    val role: String? = null,
    val content: List<QwenImageGenContentResult>? = null
)

data class QwenImageGenContentResult(
    // 文档示例为 { "image": "http..." }
    val image: String? = null,
    // 兜底：有些实现可能返回 url 字段
    val url: String? = null,
    // 兜底：若返回 base64 数据
    @SerializedName("b64_json") val b64Json: String? = null
)