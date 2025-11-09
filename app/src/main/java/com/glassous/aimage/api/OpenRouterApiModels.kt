package com.glassous.aimage.api

// Request models
data class OpenRouterChatRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val modalities: List<String>? = listOf("text", "image"),
    val image_config: OpenRouterImageConfig? = null,
    val response_format: Any? = null // reserved for structured outputs; not required for images
)

data class OpenRouterImageConfig(
    val aspect_ratio: String? = null
)

data class OpenRouterMessage(
    val role: String,
    val content: List<OpenRouterContentPart>
)

data class OpenRouterContentPart(
    val type: String,
    val text: String? = null,
    val image_url: OpenRouterImageUrl? = null
)

data class OpenRouterImageUrl(
    val url: String
)

// Response models
data class OpenRouterChatResponse(
    val id: String? = null,
    val choices: List<OpenRouterChoice> = emptyList()
)

data class OpenRouterChoice(
    val index: Int? = null,
    val message: OpenRouterMessageResponse? = null,
    val finish_reason: String? = null
)

data class OpenRouterMessageResponse(
    val role: String? = null,
    // OpenRouter chat/completions aligns with OpenAI: content is a string or null
    val content: String? = null,
    // Some responses may include tool calls and content may be null
    val tool_calls: List<OpenRouterToolCall>? = null,
    // Image generation responses include an "images" array on the assistant message
    val images: List<OpenRouterImagePartResponse>? = null
)

data class OpenRouterToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: OpenRouterToolFunction? = null
)

data class OpenRouterToolFunction(
    val name: String? = null,
    val arguments: String? = null
)

data class OpenRouterImagePartResponse(
    val type: String? = null,
    val image_url: OpenRouterImageUrl? = null
)