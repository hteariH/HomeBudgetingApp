package com.personalmoney.core.network

import com.personalmoney.core.domain.CategoryType
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The HomeMoney import contract, in both directions.
 *
 * The decode half is the same job as [RestDtoContractTest]: these DTOs are written by hand
 * against a server whose responses only a test like this watches. The encode half exists
 * because of a bug that got all the way to a working screen - see
 * [encodes included and balance even when they hold their default values].
 */
class HomeMoneyDtoContractTest {

    private val json = createJson()

    @Test
    fun `decodes a real plan payload`() {
        // Copied verbatim from the running server. Note what is *not* here: the server is
        // configured to omit nulls, so `currencyCode`, `targetAccountId` and
        // `blockedReason` are simply absent rather than null.
        val payload = """
            {"accounts":[
               {"externalId":"homemoney:11:980","name":"Wallet (UAH)","currencyLabel":"UAH",
                "currencyCode":"UAH","balance":"4000.00","included":true},
               {"externalId":"homemoney:11:840","name":"Wallet (USD)","currencyLabel":"USD",
                "currencyCode":"USD","balance":"100.00","included":true}],
             "categories":[
               {"externalId":"homemoney:10","name":"Groceries","fullName":"Groceries",
                "type":"EXPENSE","archived":false},
               {"externalId":"homemoney:13","name":"Fuel","fullName":"Groceries / Fuel",
                "type":"EXPENSE","parentExternalId":"homemoney:10","archived":false},
               {"externalId":"homemoney:11","name":"Salary","fullName":"Salary",
                "type":"INCOME","archived":false,"targetCategoryId":257}],
             "transactionCount":3,"firstDate":"2026-03-01","lastDate":"2026-03-10",
             "newAccountCount":2,"newCategoryCount":2}
        """.trimIndent()

        val plan = json.decodeFromString<RestHomeMoneyPlan>(payload)

        assertEquals(2, plan.accounts.size)
        assertEquals("4000.00", plan.accounts[0].balance)
        assertTrue(plan.accounts[0].included)
        assertEquals(3, plan.categories.size)
        assertEquals(CategoryType.INCOME, plan.categories[2].type)
        assertEquals(257L, plan.categories[2].targetCategoryId)
        assertEquals("homemoney:10", plan.categories[1].parentExternalId)
        // Absent, not null - the server omits nulls entirely.
        assertNull(plan.accounts[0].targetAccountId)
        assertNull(plan.blockedReason)
        assertEquals("2026-03-01", plan.firstDate)
    }

    @Test
    fun `decodes a status payload`() {
        val payload = """
            {"state":"DONE","accountsCreated":3,"categoriesCreated":2,"imported":3,
             "skipped":2,"processedChunks":4,"totalChunks":4}
        """.trimIndent()

        val status = json.decodeFromString<RestHomeMoneyStatus>(payload)

        assertEquals("DONE", status.state)
        assertEquals(3, status.imported)
        assertEquals(2, status.skipped)
        assertNull(status.error)
    }

    @Test
    fun `encodes included and balance even when they hold their default values`() {
        // The regression this file exists for. kotlinx.serialization omits any value equal
        // to its declared default, so an account the user left switched on - `included`
        // true, which is also the default - went up with no `included` field at all, and
        // the server filled the gap with false. Every account arrived excluded, the import
        // reported success, and nothing was imported.
        val request = HomeMoneyApplyRequest(
            accounts = listOf(
                RestPlannedAccount(
                    externalId = "homemoney:11:980",
                    name = "Wallet",
                    currencyLabel = "UAH",
                    currencyCode = "UAH",
                    balance = "0",
                    included = true,
                )
            ),
            categories = emptyList(),
        )

        val encoded = json.encodeToString(request)

        assertTrue("\"included\":true" in encoded, "included went missing from: $encoded")
        assertTrue("\"balance\":\"0\"" in encoded, "balance went missing from: $encoded")
    }

    @Test
    fun `an excluded account survives the round trip`() {
        val request = HomeMoneyApplyRequest(
            accounts = listOf(
                RestPlannedAccount(
                    externalId = "homemoney:12:980",
                    name = "Cash box",
                    currencyLabel = "UAH",
                    included = false,
                )
            )
        )

        val back = json.decodeFromString<HomeMoneyApplyRequest>(json.encodeToString(request))

        assertEquals(false, back.accounts.single().included)
    }
}
