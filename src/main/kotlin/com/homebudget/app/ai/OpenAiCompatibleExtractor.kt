package com.homebudget.app.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

/**
 * `POST {base}/chat/completions` - OpenAI, OpenRouter, Ollama, LM Studio, vLLM.
 *
 * JSON mode (`response_format`) is asked for; a server that rejects the field gets the
 * same request once more without it, and is remembered in [withoutJsonMode] - shared by
 * every extractor the factory makes - so later receipts for that model go without it at once.
 */
class OpenAiCompatibleExtractor(
    http: HttpClient,
    json: ObjectMapper,
    provider: ResolvedProvider,
    timeout: Duration,
    private val withoutJsonMode: MutableSet<String> = mutableSetOf()
) : HttpReceiptExtractor(http, json, provider, timeout) {

    override val endpoint: URI = URI.create(provider.baseUrl.trimEnd('/') + "/chat/completions")

    private val memoryKey = "$endpoint ${provider.model}"

    private val jsonMode: Boolean get() = memoryKey !in withoutJsonMode

    override fun headers(): Map<String, String> =
        provider.apiKey?.takeIf { it.isNotEmpty() }?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()

    override fun ask(image: String, prompt: String, reply: String?): String {
        if (jsonMode) {
            val response = post(body(image, prompt, reply))
            if (response.statusCode() != 400 || !(response.body() ?: "").contains("response_format", ignoreCase = true)) {
                return answerOf(response)
            }
            withoutJsonMode += memoryKey
        }
        return answerOf(post(body(image, prompt, reply)))
    }

    override fun body(image: String, prompt: String, reply: String?): ObjectNode = json.createObjectNode().apply {
        put("model", provider.model)
        putArray("messages").apply {
            addObject().apply {
                put("role", "user")
                putArray("content").apply {
                    addObject().put("type", "text").put("text", prompt)
                    addObject().put("type", "image_url").putObject("image_url").put("url", "data:image/jpeg;base64,$image")
                }
            }
            if (reply != null) {
                addObject().put("role", "assistant").put("content", reply)
                addObject().put("role", "user").put("content", ReceiptPrompt.REMINDER)
            }
        }
        if (jsonMode) putObject("response_format").put("type", "json_object")
    }

    override fun replyText(response: JsonNode): String? {
        val content = response["choices"]?.get(0)?.get("message")?.get("content") ?: return null
        return if (content.isTextual) content.asText() else joinText(content)
    }
}
