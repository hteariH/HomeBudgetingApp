package com.homebudget.app.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

/** Google's `generateContent`, with the photo as `inline_data` and a JSON response type. */
class GeminiExtractor(
    http: HttpClient,
    json: ObjectMapper,
    provider: ResolvedProvider,
    timeout: Duration
) : HttpReceiptExtractor(http, json, provider, timeout) {

    override val endpoint: URI = URI.create(provider.baseUrl.replace("{model}", pathSegment(provider.model)))

    // The header rather than `?key=`, so the key never sits in a URL.
    override fun headers(): Map<String, String> =
        provider.apiKey?.takeIf { it.isNotEmpty() }?.let { mapOf("x-goog-api-key" to it) } ?: emptyMap()

    override fun body(image: String, prompt: String, reply: String?): ObjectNode = json.createObjectNode().apply {
        putArray("contents").apply {
            addObject().apply {
                put("role", "user")
                putArray("parts").apply {
                    addObject().putObject("inline_data").put("mime_type", "image/jpeg").put("data", image)
                    addObject().put("text", prompt)
                }
            }
            if (reply != null) {
                addObject().put("role", "model").putArray("parts").addObject().put("text", reply)
                addObject().put("role", "user").putArray("parts").addObject().put("text", ReceiptPrompt.REMINDER)
            }
        }
        putObject("generationConfig").put("responseMimeType", "application/json")
    }

    override fun replyText(response: JsonNode): String? =
        joinText(response["candidates"]?.get(0)?.get("content")?.get("parts"))

    companion object {
        /**
         * The model name as one path segment: everything but unreserved characters is
         * percent-encoded, so `/`, `?` and `#` cannot reach another path, query or fragment.
         */
        fun pathSegment(value: String): String = buildString {
            for (b in value.toByteArray(Charsets.UTF_8)) {
                val c = (b.toInt() and 0xFF).toChar()
                if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~") append(c)
                else append('%').append(String.format("%02X", b.toInt() and 0xFF))
            }
        }
    }
}
