package com.homebudget.app.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

/** Anthropic's Messages API, with the photo as a base64 `image` block. */
class AnthropicExtractor(
    http: HttpClient,
    json: ObjectMapper,
    provider: ResolvedProvider,
    timeout: Duration
) : HttpReceiptExtractor(http, json, provider, timeout) {

    override val endpoint: URI = URI.create(provider.baseUrl)

    override fun headers(): Map<String, String> = buildMap {
        put("anthropic-version", "2023-06-01")
        provider.apiKey?.takeIf { it.isNotEmpty() }?.let { put("x-api-key", it) }
    }

    override fun body(image: String, prompt: String, reply: String?): ObjectNode = json.createObjectNode().apply {
        put("model", provider.model)
        put("max_tokens", 2048)
        putArray("messages").apply {
            addObject().apply {
                put("role", "user")
                putArray("content").apply {
                    addObject().put("type", "image").putObject("source")
                        .put("type", "base64").put("media_type", "image/jpeg").put("data", image)
                    addObject().put("type", "text").put("text", prompt)
                }
            }
            if (reply != null) {
                // The API refuses an empty assistant turn.
                addObject().put("role", "assistant").put("content", reply.ifBlank { "-" })
                addObject().put("role", "user").put("content", ReceiptPrompt.REMINDER)
            }
        }
    }

    override fun replyText(response: JsonNode): String? =
        joinText(response["content"]?.filter { it["type"]?.asText() == "text" }?.let { json.createArrayNode().addAll(it) })
}
