package com.homebudget.app.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigDecimal
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.Duration
import java.time.LocalDate
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/** A model server in the test: records what it was sent and answers from a queue. */
class FakeModelServer : AutoCloseable {
    data class Recorded(val method: String, val path: String, val rawPath: String, val query: String?, val headers: Map<String, String>, val body: String)
    data class Reply(val status: Int, val body: String, val delayMillis: Long = 0, val headers: Map<String, String> = emptyMap())

    val requests = CopyOnWriteArrayList<Recorded>()
    private val replies = ConcurrentLinkedQueue<Reply>()
    private val pool = Executors.newCachedThreadPool()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        executor = pool
        createContext("/") { ex ->
            val headers = ex.requestHeaders.entries.associate { (k, v) -> k.lowercase() to v.joinToString(",") }
            requests += Recorded(
                ex.requestMethod, ex.requestURI.path, ex.requestURI.rawPath, ex.requestURI.rawQuery, headers,
                ex.requestBody.readAllBytes().toString(Charsets.UTF_8)
            )
            val reply = replies.poll() ?: Reply(500, """{"error":"no reply queued"}""")
            if (reply.delayMillis > 0) Thread.sleep(reply.delayMillis)
            reply.headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
            ex.responseHeaders.add("Content-Type", "application/json")
            val bytes = reply.body.toByteArray()
            ex.sendResponseHeaders(reply.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        start()
    }

    val port: Int get() = server.address.port
    val base: String get() = "http://127.0.0.1:$port"

    fun reply(status: Int, body: String, delayMillis: Long = 0, headers: Map<String, String> = emptyMap()) {
        replies += Reply(status, body, delayMillis, headers)
    }

    override fun close() {
        server.stop(0)
        pool.shutdownNow()
    }
}

class ExtractorsTest {

    private val json = ObjectMapper()
    private val factory = ExtractorFactory(json)
    private val server = FakeModelServer()
    private val jpeg = byteArrayOf(-1, -40, -1, -32, 1, 2, 3, 4, 5, 6, 7, 8)
    private val imageB64 = Base64.getEncoder().encodeToString(jpeg)
    private val context = ExtractionContext(listOf("Groceries", "Transport"), listOf("EUR", "UAH"))

    private val receiptJson = """{"merchant":"SHOP","date":"2026-10-14","currency":"EUR","total":"2.00",""" +
        """"items":[{"name":"Milk","quantity":"1","price":"1.20"},{"name":"Bread","quantity":null,"price":0.80}],""" +
        """"suggestedCategory":"Groceries"}"""

    @AfterEach
    fun stop() = server.close()

    /** Everything that differs between the three wire formats, for the shared tests. */
    private fun provider(type: AiProviderType, key: String? = "sk-test", model: String = "vision-1") = when (type) {
        AiProviderType.OPENAI_COMPATIBLE -> ResolvedProvider(type, "${server.base}/v1", model, key, AiProviderSource.INSTANCE)
        AiProviderType.ANTHROPIC -> ResolvedProvider(type, "${server.base}/v1/messages", model, key, AiProviderSource.INSTANCE)
        AiProviderType.GEMINI ->
            ResolvedProvider(type, "${server.base}/v1beta/models/{model}:generateContent", model, key, AiProviderSource.INSTANCE)
    }

    private fun envelope(type: AiProviderType, text: String): String {
        val t = json.writeValueAsString(text)
        return when (type) {
            AiProviderType.OPENAI_COMPATIBLE -> """{"choices":[{"index":0,"message":{"role":"assistant","content":$t}}]}"""
            AiProviderType.ANTHROPIC -> """{"type":"message","content":[{"type":"text","text":$t}]}"""
            AiProviderType.GEMINI -> """{"candidates":[{"content":{"role":"model","parts":[{"text":$t}]}}]}"""
        }
    }

    private fun extractor(type: AiProviderType, timeout: Duration = Duration.ofSeconds(10), key: String? = "sk-test", model: String = "vision-1") =
        factory.create(provider(type, key, model), timeout)

    private fun assertGoodReceipt(r: ExtractedReceipt) {
        assertEquals("SHOP", r.merchant)
        assertEquals(LocalDate.of(2026, 10, 14), r.date)
        assertEquals("EUR", r.currency)
        assertEquals(0, BigDecimal("2.00").compareTo(r.total))
        assertEquals(listOf("Milk", "Bread"), r.items.map { it.name })
        assertEquals(0, BigDecimal("1.20").compareTo(r.items[0].price))
        assertEquals(0, BigDecimal.ONE.compareTo(r.items[0].quantity))
        assertNull(r.items[1].quantity)
        assertEquals(0, BigDecimal("0.80").compareTo(r.items[1].price))
        assertEquals("Groceries", r.suggestedCategory)
    }

    @ParameterizedTest
    @EnumSource(AiProviderType::class)
    fun `sends the image, model and key in the provider's format and reads the reply`(type: AiProviderType) {
        server.reply(200, envelope(type, receiptJson))

        assertGoodReceipt(extractor(type).extract(jpeg, context))

        assertEquals(1, server.requests.size)
        val req = server.requests.single()
        assertEquals("POST", req.method)
        val body = json.readTree(req.body)
        when (type) {
            AiProviderType.OPENAI_COMPATIBLE -> {
                assertEquals("/v1/chat/completions", req.path)
                assertEquals("Bearer sk-test", req.headers["authorization"])
                assertEquals("vision-1", body["model"].asText())
                assertEquals("json_object", body["response_format"]["type"].asText())
                val parts = body["messages"][0]["content"]
                assertEquals("text", parts[0]["type"].asText())
                assertEquals("image_url", parts[1]["type"].asText())
                assertEquals("data:image/jpeg;base64,$imageB64", parts[1]["image_url"]["url"].asText())
                assertPromptMentionsContext(parts[0]["text"].asText())
            }
            AiProviderType.ANTHROPIC -> {
                assertEquals("/v1/messages", req.path)
                assertEquals("sk-test", req.headers["x-api-key"])
                assertEquals("2023-06-01", req.headers["anthropic-version"])
                assertNull(req.headers["authorization"])
                assertEquals("vision-1", body["model"].asText())
                assertEquals(2048, body["max_tokens"].asInt())
                val parts = body["messages"][0]["content"]
                assertEquals("image", parts[0]["type"].asText())
                assertEquals("base64", parts[0]["source"]["type"].asText())
                assertEquals("image/jpeg", parts[0]["source"]["media_type"].asText())
                assertEquals(imageB64, parts[0]["source"]["data"].asText())
                assertEquals("text", parts[1]["type"].asText())
                assertPromptMentionsContext(parts[1]["text"].asText())
            }
            AiProviderType.GEMINI -> {
                assertEquals("/v1beta/models/vision-1:generateContent", req.path)
                assertEquals("sk-test", req.headers["x-goog-api-key"])
                val parts = body["contents"][0]["parts"]
                assertEquals("image/jpeg", parts[0]["inline_data"]["mime_type"].asText())
                assertEquals(imageB64, parts[0]["inline_data"]["data"].asText())
                assertPromptMentionsContext(parts[1]["text"].asText())
                assertEquals("application/json", body["generationConfig"]["responseMimeType"].asText())
            }
        }
    }

    private fun assertPromptMentionsContext(prompt: String) {
        assertTrue(prompt.contains("Groceries") && prompt.contains("Transport"), prompt)
        assertTrue(prompt.contains("EUR") && prompt.contains("UAH"), prompt)
        assertTrue(prompt.contains("suggestedCategory"), prompt)
    }

    @ParameterizedTest
    @EnumSource(AiProviderType::class)
    fun `an answer that is not JSON gets one reminder, then succeeds`(type: AiProviderType) {
        server.reply(200, envelope(type, "Sure! The shop is SHOP and the total is 2.00."))
        server.reply(200, envelope(type, receiptJson))

        assertGoodReceipt(extractor(type).extract(jpeg, context))

        assertEquals(2, server.requests.size)
        val retry = server.requests[1].body
        assertTrue(retry.contains("Reply with the JSON object only."), "the retry carries the reminder")
        assertTrue(retry.contains(imageB64), "the retry still carries the image")
    }

    @ParameterizedTest
    @EnumSource(AiProviderType::class)
    fun `two unreadable answers fail with the unreadable reason`(type: AiProviderType) {
        server.reply(200, envelope(type, "no idea"))
        server.reply(200, envelope(type, "still no idea"))

        val e = assertThrows<ExtractionException> { extractor(type).extract(jpeg, context) }
        assertEquals("The model's answer could not be read", e.reason)
        assertEquals(2, server.requests.size)
    }

    @ParameterizedTest
    @EnumSource(AiProviderType::class)
    fun `a model that rejects images says so`(type: AiProviderType) {
        server.reply(400, """{"error":{"message":"This model does not support image input"}}""")

        val e = assertThrows<ExtractionException> { extractor(type).extract(jpeg, context) }
        assertEquals("This model cannot read images", e.reason)
        assertEquals(1, server.requests.size)
    }

    @ParameterizedTest
    @EnumSource(AiProviderType::class)
    fun `a slow model times out with the configured timeout in the reason`(type: AiProviderType) {
        server.reply(200, envelope(type, receiptJson), delayMillis = 3000)

        val e = assertThrows<ExtractionException> { extractor(type, timeout = Duration.ofSeconds(1)).extract(jpeg, context) }
        assertEquals("The model took longer than 1s", e.reason)
    }

    @ParameterizedTest
    @EnumSource(AiProviderType::class)
    fun `a refused key says so`(type: AiProviderType) {
        server.reply(401, """{"error":{"message":"Invalid API key"}}""")
        server.reply(403, """{"error":{"message":"This key may not use image input"}}""")

        val unauthorized = assertThrows<ExtractionException> { extractor(type).extract(jpeg, context) }
        val forbidden = assertThrows<ExtractionException> { extractor(type).extract(jpeg, context) }
        assertEquals("The model refused the API key", unauthorized.reason)
        assertEquals("The model refused the API key", forbidden.reason)
    }

    @ParameterizedTest
    @EnumSource(AiProviderType::class)
    fun `other errors say the model could not be reached`(type: AiProviderType) {
        server.reply(404, """{"error":{"message":"model not found"}}""")

        val e = assertThrows<ExtractionException> { extractor(type).extract(jpeg, context) }
        assertEquals("Could not reach the model at 127.0.0.1", e.reason)
    }

    @ParameterizedTest
    @EnumSource(AiProviderType::class)
    fun `a complaint about the image that is not about reading images is not reported as one`(type: AiProviderType) {
        server.reply(400, """{"error":{"message":"image exceeds 5 MB maximum"}}""")
        server.reply(429, """{"error":{"message":"Too many image requests"}}""")

        repeat(2) {
            val e = assertThrows<ExtractionException> { extractor(type).extract(jpeg, context) }
            assertEquals("Could not reach the model at 127.0.0.1", e.reason)
        }
    }

    @Test
    fun `the ways servers say a model takes no images are recognised`() {
        listOf(
            "This model does not support image input",
            "Invalid content type. image_url is only supported by certain models.",
            "llava: model doesn't support images",
            "image input is not supported - hint: if this is unexpected, you may need to provide the mmproj",
            "Multimodal input is not supported by this model",
            "The model is not a multimodal model"
        ).forEach { message ->
            server.reply(400, json.writeValueAsString(mapOf("error" to mapOf("message" to message))))
            val e = assertThrows<ExtractionException> { extractor(AiProviderType.OPENAI_COMPATIBLE).extract(jpeg, context) }
            assertEquals("This model cannot read images", e.reason, message)
        }
    }

    @ParameterizedTest
    @EnumSource(AiProviderType::class)
    fun `a redirect is not followed`(type: AiProviderType) {
        server.reply(307, "", headers = mapOf("Location" to "${server.base}/elsewhere"))
        server.reply(200, envelope(type, receiptJson))

        val e = assertThrows<ExtractionException> { extractor(type).extract(jpeg, context) }
        assertEquals("Could not reach the model at 127.0.0.1", e.reason)
        assertEquals(1, server.requests.size, "the key must not be carried to another address")
    }

    @ParameterizedTest
    @EnumSource(AiProviderType::class)
    fun `a key the JDK refuses in a header fails as unreachable without quoting it`(type: AiProviderType) {
        val e = assertThrows<ExtractionException> { extractor(type, key = "sk-leak\nme").extract(jpeg, context) }
        assertEquals("Could not reach the model at 127.0.0.1", e.reason)
        assertFalse(e.message!!.contains("sk-leak"))
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `a read interrupted by a shutdown says so`() {
        server.reply(200, envelope(AiProviderType.OPENAI_COMPATIBLE, receiptJson), delayMillis = 10_000)
        var failure: Throwable? = null
        val reading = Thread {
            failure = runCatching { extractor(AiProviderType.OPENAI_COMPATIBLE).extract(jpeg, context) }.exceptionOrNull()
        }
        reading.start()
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (server.requests.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10)
        reading.interrupt()
        reading.join(5_000)

        assertEquals(ExtractionException.INTERRUPTED, (failure as ExtractionException).reason)
    }

    @Test
    fun `an unreachable host names the host`() {
        val freePort = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
        val p = ResolvedProvider(AiProviderType.OPENAI_COMPATIBLE, "http://127.0.0.1:$freePort/v1", "m", null, AiProviderSource.INSTANCE)
        val e = assertThrows<ExtractionException> { factory.create(p, Duration.ofSeconds(5)).extract(jpeg, context) }
        assertEquals("Could not reach the model at 127.0.0.1", e.reason)
    }

    @Test
    fun `a server without response_format support is asked again without it`() {
        server.reply(400, """{"error":{"message":"Unrecognized request argument supplied: response_format"}}""")
        server.reply(200, envelope(AiProviderType.OPENAI_COMPATIBLE, receiptJson))

        assertGoodReceipt(extractor(AiProviderType.OPENAI_COMPATIBLE).extract(jpeg, context))

        assertEquals(2, server.requests.size)
        assertTrue(json.readTree(server.requests[0].body).has("response_format"))
        assertFalse(json.readTree(server.requests[1].body).has("response_format"))
    }

    @Test
    fun `a server without response_format support is not offered it again on the next receipt`() {
        server.reply(400, """{"error":{"message":"Unrecognized request argument supplied: response_format"}}""")
        server.reply(200, envelope(AiProviderType.OPENAI_COMPATIBLE, receiptJson))
        server.reply(200, envelope(AiProviderType.OPENAI_COMPATIBLE, receiptJson))

        extractor(AiProviderType.OPENAI_COMPATIBLE).extract(jpeg, context)
        assertGoodReceipt(extractor(AiProviderType.OPENAI_COMPATIBLE).extract(jpeg, context))

        assertEquals(3, server.requests.size)
        assertFalse(json.readTree(server.requests[2].body).has("response_format"))

        // Another model on the same server is still offered JSON mode.
        server.reply(200, envelope(AiProviderType.OPENAI_COMPATIBLE, receiptJson))
        extractor(AiProviderType.OPENAI_COMPATIBLE, model = "vision-2").extract(jpeg, context)
        assertTrue(json.readTree(server.requests[3].body).has("response_format"))
    }

    @Test
    fun `a local model without a key gets no authorization header`() {
        server.reply(200, envelope(AiProviderType.OPENAI_COMPATIBLE, receiptJson))
        extractor(AiProviderType.OPENAI_COMPATIBLE, key = null).extract(jpeg, context)
        assertNull(server.requests.single().headers["authorization"])
    }

    @Test
    fun `the Gemini model name cannot leave its path segment`() {
        server.reply(200, envelope(AiProviderType.GEMINI, receiptJson))
        extractor(AiProviderType.GEMINI, model = "../x/y?key=1#f").extract(jpeg, context)
        val req = server.requests.single()
        assertEquals("/v1beta/models/..%2Fx%2Fy%3Fkey%3D1%23f:generateContent", req.rawPath)
        assertNull(req.query)
    }

    @Test
    fun `the timeout is shown in the largest whole unit`() {
        assertEquals("5m", formatTimeout(Duration.ofMinutes(5)))
        assertEquals("90s", formatTimeout(Duration.ofSeconds(90)))
        assertEquals("1h", formatTimeout(Duration.ofHours(1)))
        assertEquals("1500ms", formatTimeout(Duration.ofMillis(1500)))
    }
}

class ReceiptPromptTest {

    @Test
    fun `reads a reply wrapped in a code fence`() {
        val r = ReceiptPrompt.parse("```json\n{\"merchant\":\"A\",\"total\":\"3.50\",\"items\":[]}\n```")
        assertEquals("A", r.merchant)
        assertEquals(0, BigDecimal("3.50").compareTo(r.total))
        assertTrue(r.items.isEmpty())
    }

    @Test
    fun `numbers may come as strings or numbers, with a comma or a currency sign`() {
        val r = ReceiptPrompt.parse(
            """{"total":"12,40 €","items":[{"name":"A","quantity":2,"price":"1 234.50"},{"name":"B","price":3.1},{"name":"C","price":"1.234,50"}]}"""
        )
        assertEquals(0, BigDecimal("12.40").compareTo(r.total))
        assertEquals(0, BigDecimal("2").compareTo(r.items[0].quantity))
        assertEquals(0, BigDecimal("1234.50").compareTo(r.items[0].price))
        assertEquals(0, BigDecimal("3.1").compareTo(r.items[1].price))
        assertEquals(0, BigDecimal("1234.50").compareTo(r.items[2].price))
    }

    @Test
    fun `thousands separators are not read as a decimal point`() {
        val r = ReceiptPrompt.parse(
            """{"total":"1,234","items":[{"name":"A","price":"1,234,567.89"},{"name":"B","price":"1.234.567"},""" +
                """{"name":"C","price":"12,5"},{"name":"D","price":"0,125"},{"name":"E","price":"1.234"},{"name":"F","price":"-1,234.5"}]}"""
        )
        assertEquals(0, BigDecimal("1234").compareTo(r.total))
        assertEquals(0, BigDecimal("1234567.89").compareTo(r.items[0].price))
        assertEquals(0, BigDecimal("1234567").compareTo(r.items[1].price))
        assertEquals(0, BigDecimal("12.5").compareTo(r.items[2].price))
        assertEquals(0, BigDecimal("0.125").compareTo(r.items[3].price), "a leading zero is a decimal, not a thousand")
        assertEquals(0, BigDecimal("1.234").compareTo(r.items[4].price), "a dot is the decimal point the prompt asks for")
        assertEquals(0, BigDecimal("-1234.5").compareTo(r.items[5].price))
    }

    @Test
    fun `a bad date, blank strings and items without a price become nothing`() {
        val r = ReceiptPrompt.parse(
            """{"merchant":" ","date":"14/10/2026","currency":"eur","items":[{"name":"A"},{"name":"B","price":"x"},{"name":"C","price":"1"}],"suggestedCategory":null}"""
        )
        assertNull(r.merchant)
        assertNull(r.date)
        assertEquals("EUR", r.currency)
        assertEquals(listOf("C"), r.items.map { it.name })
        assertNull(r.suggestedCategory)
    }

    @Test
    fun `text around the object is ignored`() {
        val r = ReceiptPrompt.parse("Here you go: {\"merchant\":\"A\",\"items\":[]} Hope it helps")
        assertEquals("A", r.merchant)
    }

    @Test
    fun `not JSON or the wrong shape cannot be read`() {
        for (bad in listOf("hello", "[1,2]", """{"items":"none"}""", """{"merchant":"A",""")) {
            val e = assertThrows<ExtractionException>(bad) { ReceiptPrompt.parse(bad) }
            assertEquals("The model's answer could not be read", e.reason)
        }
    }

    @Test
    fun `the prompt lists the categories and currencies to choose from`() {
        val text = ReceiptPrompt.text(ExtractionContext(listOf("Food"), listOf("PLN")))
        assertTrue(text.contains("Food") && text.contains("PLN"))
        val empty = ReceiptPrompt.text(ExtractionContext(emptyList(), emptyList()))
        assertTrue(empty.contains("suggestedCategory"))
    }
}
