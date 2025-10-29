package com.glassous.aimage.api

data class GeminiRequest(
    val instances: List<GeminiInstance>,
    val parameters: GeminiParameters
)

data class GeminiInstance(
    val prompt: String
)

data class GeminiParameters(
    val sampleCount: Int,
    val aspectRatio: String? = null,
    val personGeneration: String? = null
)

data class GeminiPrediction(
    val bytesBase64Encoded: String?,
    val mimeType: String?,
    val generatedText: String? = null
)

data class GeminiError(
    val message: String?
)

data class GeminiResponse(
    val predictions: List<GeminiPrediction>?,
    val error: GeminiError? = null
)

// -------------------------------
// generateContent 模型（Gemini 2.5 Flash Image）
// -------------------------------

data class GeminiInlineData(
    val mimeType: String? = null,
    val data: String? = null
)

data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null
)

data class GeminiImageConfig(
    val aspectRatio: String? = null,
    // Gemini Flash Image 的 imageConfig 目前仅支持 aspectRatio
    // 多张图片请使用顶层 generationConfig 的 candidateCount（若支持）
    // 这里不再发送不被识别的字段以避免 400 错误
)

data class GeminiGenerationConfig(
    val responseModalities: List<String>? = null,
    val imageConfig: GeminiImageConfig? = null
)

data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContentResponse(
    val parts: List<GeminiPart>? = null
)

data class GeminiCandidate(
    val content: GeminiContentResponse? = null
)

data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null
)