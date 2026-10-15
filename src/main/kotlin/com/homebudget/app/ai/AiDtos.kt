package com.homebudget.app.ai

/** What a client sees of a provider. The key itself never leaves the server. */
data class ProviderView(
    val source: AiProviderSource,
    val type: AiProviderType? = null,
    val baseUrl: String? = null,
    val model: String? = null,
    val hasKey: Boolean = false,
    /** The host receipts are sent to, so people can see where their photos go. */
    val host: String? = null
)

/**
 * [apiKey] null keeps the stored key; an empty string clears it.
 * [baseUrl] matters only for OPENAI_COMPATIBLE.
 */
data class ProviderRequest(
    val type: AiProviderType,
    val baseUrl: String? = null,
    val model: String,
    val apiKey: String? = null
)
