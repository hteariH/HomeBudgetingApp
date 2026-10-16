package com.homebudget.app.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** Makes the extractor that speaks a provider's wire format. */
@Component
class ExtractorFactory(private val json: ObjectMapper) {

    // Redirects are never followed: a key must only go to the address it was entered for.
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    /** The OpenAI-compatible servers, by address and model, that turned JSON mode down; kept until a restart. */
    private val withoutJsonMode: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun create(provider: ResolvedProvider, timeout: Duration): ReceiptExtractor = when (provider.type) {
        AiProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleExtractor(http, json, provider, timeout, withoutJsonMode)
        AiProviderType.ANTHROPIC -> AnthropicExtractor(http, json, provider, timeout)
        AiProviderType.GEMINI -> GeminiExtractor(http, json, provider, timeout)
    }
}
