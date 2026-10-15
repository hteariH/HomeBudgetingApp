package com.homebudget.app.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.io.IOException
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.time.LocalDate
import java.util.Base64

/** What the household offers the model to choose from: expense category names and active currency codes. */
data class ExtractionContext(val expenseCategories: List<String>, val currencies: List<String>)

data class ExtractedItem(val name: String, val quantity: BigDecimal?, val price: BigDecimal)

/** What a model read off a receipt, before it is checked against the household's catalogue. */
data class ExtractedReceipt(
    val merchant: String?,
    val date: LocalDate?,
    val currency: String?,
    val total: BigDecimal?,
    val items: List<ExtractedItem>,
    val suggestedCategory: String?
)

/** A receipt could not be read; [reason] is shown to the person as it is. */
class ExtractionException(val reason: String) : RuntimeException(reason) {
    companion object {
        const val CANNOT_READ_IMAGES = "This model cannot read images"
        const val UNREADABLE = "The model's answer could not be read"
        /** The server stopped while the model was reading. */
        const val INTERRUPTED = "Reading was interrupted — scan again"
        fun unreachable(host: String) = ExtractionException("Could not reach the model at $host")
        fun tooSlow(timeout: Duration) = ExtractionException("The model took longer than ${formatTimeout(timeout)}")
    }
}

interface ReceiptExtractor {
    /** Reads one compressed JPEG; throws [ExtractionException] with a user-facing reason. */
    fun extract(jpeg: ByteArray, context: ExtractionContext): ExtractedReceipt
}

/** A duration the way `AI_TIMEOUT` is written: `5m`, `90s`, `1h`. */
fun formatTimeout(d: Duration): String = when {
    d.toMillis() % 3_600_000 == 0L -> "${d.toHours()}h"
    d.toMillis() % 60_000 == 0L -> "${d.toMinutes()}m"
    d.toMillis() % 1_000 == 0L -> "${d.seconds}s"
    else -> "${d.toMillis()}ms"
}

/**
 * The part the three wire formats share: send the prompt and the photo, read the reply,
 * and when it is not the JSON asked for, remind the model once in the same conversation.
 *
 * Nothing here logs the key, the photo or the model's reply - only the host and the status.
 */
abstract class HttpReceiptExtractor(
    private val http: HttpClient,
    protected val json: ObjectMapper,
    protected val provider: ResolvedProvider,
    private val timeout: Duration
) : ReceiptExtractor {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Where requests go. */
    protected abstract val endpoint: URI

    /** Authentication and version headers. */
    protected abstract fun headers(): Map<String, String>

    /**
     * The request body: the prompt and the image, and - for the retry - the model's
     * previous [reply] followed by the reminder.
     */
    protected abstract fun body(image: String, prompt: String, reply: String?): ObjectNode

    /** The text of the model's answer from a successful response, or null when there is none. */
    protected abstract fun replyText(response: JsonNode): String?

    protected val host: String get() = endpoint.host ?: provider.baseUrl

    override fun extract(jpeg: ByteArray, context: ExtractionContext): ExtractedReceipt {
        val image = Base64.getEncoder().encodeToString(jpeg)
        val prompt = ReceiptPrompt.text(context)
        val first = ask(image, prompt, null)
        try {
            return ReceiptPrompt.parse(first)
        } catch (_: ExtractionException) {
            log.info("The model at {} did not answer with the receipt JSON; reminding it once", host)
        }
        return ReceiptPrompt.parse(ask(image, prompt, first))
    }

    /** One exchange with the model; returns the text of its answer. */
    protected open fun ask(image: String, prompt: String, reply: String?): String =
        answerOf(post(body(image, prompt, reply)))

    protected fun post(body: ObjectNode): HttpResponse<String> =
        try {
            // Built in here: the JDK refuses a bad header with a message that quotes the
            // value - the key - and that message must not travel any further.
            val builder = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
            headers().forEach { (k, v) -> builder.header(k, v) }
            val request = builder
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build()
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IllegalArgumentException) {
            log.warn("Could not build the request to the model at {}", host)
            throw ExtractionException.unreachable(host)
        } catch (e: HttpConnectTimeoutException) {
            log.warn("Could not connect to the model at {}", host)
            throw ExtractionException.unreachable(host)
        } catch (e: HttpTimeoutException) {
            log.warn("The model at {} did not answer within {}", host, formatTimeout(timeout))
            throw ExtractionException.tooSlow(timeout)
        } catch (e: IOException) {
            log.warn("Could not reach the model at {}: {}", host, e.javaClass.simpleName)
            throw ExtractionException.unreachable(host)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ExtractionException(ExtractionException.INTERRUPTED)
        }

    /** The answer's text from a 2xx response, or the reason the request failed. */
    protected fun answerOf(response: HttpResponse<String>): String {
        val status = response.statusCode()
        if (status !in 200..299) {
            log.warn("The model at {} answered HTTP {}", host, status)
            if (status in 400..499 && IMAGE_COMPLAINT.containsMatchIn(response.body() ?: "")) {
                throw ExtractionException(ExtractionException.CANNOT_READ_IMAGES)
            }
            throw ExtractionException.unreachable(host)
        }
        val tree = runCatching { json.readTree(response.body()) }.getOrNull()
        // An envelope without text is handled like any unreadable answer: it gets the reminder.
        return tree?.let(::replyText) ?: ""
    }

    private companion object {
        val IMAGE_COMPLAINT = Regex("image|vision|multimodal", RegexOption.IGNORE_CASE)
    }
}

/** The text parts of a JSON array of content parts, joined. */
internal fun joinText(parts: JsonNode?): String? =
    parts?.takeIf { it.isArray }?.mapNotNull { p -> p["text"]?.takeIf { it.isTextual }?.asText() }
        ?.takeIf { it.isNotEmpty() }?.joinToString("")
