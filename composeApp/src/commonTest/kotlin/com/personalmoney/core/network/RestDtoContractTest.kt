package com.personalmoney.core.network

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Decodes payloads captured from the running server.
 *
 * These DTOs are written by hand against a server in another repository, so nothing but a
 * test like this notices when a field is renamed or a type changes. The JSON below is
 * copied verbatim from a live response - editing it to make a test pass defeats the point.
 */
class RestDtoContractTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `decodes a real account payload`() {
        val payload = """
            {"id":1,"uuid":"ed4a0c0a-b74c-411d-927c-d331a5da55d7","name":"Checking",
             "type":"CHECKING","balance":"1250.75","initialBalance":"1250.75",
             "currency":{"id":1,"code":"USD","name":"US Dollar","symbol":"${'$'}","decimals":2,
                         "sortOrder":0,"exchangeRateToBase":"1.000000","isBase":true,
                         "createdAt":"2026-08-18T17:24:07.658552"},
             "icon":"wallet","color":"#4F46E5","archived":false,"sortOrder":0,
             "includeInTotal":true,"createdAt":"2026-08-18T15:24:51.162992",
             "updatedAt":"2026-08-18T13:24:51.163Z"}
        """.trimIndent()

        val account = json.decodeFromString<RestAccount>(payload)

        assertEquals("Checking", account.name)
        assertEquals("USD", account.currency.code)
        assertEquals(2, account.currency.decimals)
        // Money must survive as text. A Double here would already have lost the point.
        assertEquals("1250.75", account.balance)
        assertNull(account.bankConnectionId)
    }

    @Test
    fun `decodes an empty transaction page`() {
        val payload = """{"content":[],"page":0,"size":1,"totalElements":0,"totalPages":0}"""

        val page = json.decodeFromString<PagedResponse<RestTransaction>>(payload)

        assertTrue(page.content.isEmpty())
        assertEquals(0, page.totalPages)
    }
}
