package com.personalmoney.core.receipt

import com.personalmoney.core.auth.FakeTokenStore
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.createBackendClient
import com.personalmoney.core.network.createJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Upload, then poll until the server has read the receipt. Built over the real
 * [createBackendClient] so the multipart body is the one the phone actually sends.
 */
class ReceiptScannerTest {

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val uploads = mutableListOf<String>()
    private var polls = 0

    private fun scannerOver(
        poll: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ReceiptScanner {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Post && request.url.encodedPath == "/api/receipts") {
                uploads += request.body.toByteArray().decodeToString()
                respond("""{"uuid":"r-1","status":"PENDING"}""", HttpStatusCode.Accepted, json)
            } else {
                assertEquals("/api/receipts/r-1", request.url.encodedPath)
                polls++
                poll(request)
            }
        }
        val tokens = FakeTokenStore().apply { serverUrl = "https://budget.example/" }
        return ReceiptScanner(HomeBudgetApi(createBackendClient(engine, createJson(), { tokens.serverUrl }, tokens)))
    }

    @Test
    fun `a receipt that turns ready fills a draft`() = runTest {
        val scanner = scannerOver {
            if (polls == 1) {
                respond("""{"uuid":"r-1","status":"PENDING"}""", HttpStatusCode.OK, json)
            } else {
                respond(
                    """
                    {"uuid":"r-1","status":"READY","merchant":"Silpo","date":"2026-10-14",
                     "currency":"UAH","total":"249.90","totalMismatch":false,
                     "suggestedCategoryId":12,"suggestedCategoryUuid":"cat-food",
                     "items":[{"name":"Milk","quantity":"2","price":"80.00"}]}
                    """.trimIndent(),
                    HttpStatusCode.OK,
                    json,
                )
            }
        }

        val outcome = scanner.scan(byteArrayOf(1, 2, 3))

        assertEquals(
            ScanOutcome.Ready(
                ReceiptDraft(
                    receiptUuid = "r-1",
                    total = "249.90",
                    date = LocalDate(2026, 10, 14),
                    merchant = "Silpo",
                    suggestedCategoryUuid = "cat-food",
                )
            ),
            outcome,
        )
        assertEquals(2, polls)
        val body = uploads.single()
        assertTrue("""name="image"""" in body, "the server reads the part named image: $body")
        assertTrue("""filename="receipt.jpg"""" in body, body)
        assertTrue("image/jpeg" in body, body)
    }

    @Test
    fun `a dropped poll is ridden out`() = runTest {
        val scanner = scannerOver {
            when (polls) {
                1 -> respond("""{"uuid":"r-1","status":"PENDING"}""", HttpStatusCode.OK, json)
                2 -> throw IOException("connection reset")
                else -> respond("""{"uuid":"r-1","status":"READY","total":"10.00"}""", HttpStatusCode.OK, json)
            }
        }

        assertEquals(ScanOutcome.Ready(ReceiptDraft(receiptUuid = "r-1", total = "10.00")), scanner.scan(byteArrayOf(1)))
        assertEquals(3, polls)
    }

    @Test
    fun `three dropped polls in a row are offline`() = runTest {
        val scanner = scannerOver { throw IOException("no route") }

        assertEquals(ScanOutcome.Offline, scanner.scan(byteArrayOf(1)))
        assertEquals(3, polls)
    }

    @Test
    fun `a receipt the model could not read fails with the server's reason`() = runTest {
        val scanner = scannerOver {
            respond(
                """{"uuid":"r-1","status":"FAILED","failureReason":"This model cannot read images"}""",
                HttpStatusCode.OK,
                json,
            )
        }

        assertEquals(ScanOutcome.Failed("This model cannot read images"), scanner.scan(byteArrayOf(1)))
    }

    @Test
    fun `a refused upload fails with the server's message`() = runTest {
        val engine = MockEngine {
            respond(
                """{"status":415,"error":"Unsupported Media Type","message":"Send the photo as JPEG or PNG"}""",
                HttpStatusCode.UnsupportedMediaType,
                json,
            )
        }
        val tokens = FakeTokenStore().apply { serverUrl = "https://budget.example/" }
        val scanner = ReceiptScanner(HomeBudgetApi(createBackendClient(engine, createJson(), { tokens.serverUrl }, tokens)))

        assertEquals(ScanOutcome.Failed("Send the photo as JPEG or PNG"), scanner.scan(byteArrayOf(1)))
    }

    @Test
    fun `no route to the server is offline`() = runTest {
        val engine = MockEngine { throw IOException("Unable to resolve host") }
        val tokens = FakeTokenStore().apply { serverUrl = "https://budget.example/" }
        val scanner = ReceiptScanner(HomeBudgetApi(createBackendClient(engine, createJson(), { tokens.serverUrl }, tokens)))

        assertEquals(ScanOutcome.Offline, scanner.scan(byteArrayOf(1)))
    }

    @Test
    fun `a receipt still pending after six minutes is given up on`() = runTest {
        val scanner = scannerOver {
            respond("""{"uuid":"r-1","status":"PENDING"}""", HttpStatusCode.OK, json)
        }

        assertEquals(ScanOutcome.Failed(null), scanner.scan(byteArrayOf(1)))
        // Every two seconds for six minutes.
        assertEquals(180, polls)
    }
}
