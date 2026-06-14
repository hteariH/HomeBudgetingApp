package com.personalmoney.core.homemoney

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies parsing of the real HomeMoney wire shapes and the response → candidate mapping.
 * The JSON samples mirror the live `BalanceList` / `TokenPassword` responses observed against
 * homemoney.ua (HTTP 200 in both success and error cases).
 */
class HomeMoneyMapperTest {

    // Same configuration as the production Json (see NetworkModule).
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val balanceSample = """
        {
          "Error": {"code": 0, "message": null},
          "defaultcurrency": "UAH",
          "name": "Test User",
          "ListGroupInfo": [
            {
              "id": "g1", "name": "Cash", "order": 0,
              "ListAccountInfo": [
                {
                  "id": "a1", "name": "Wallet", "isDefault": true, "display": true,
                  "includeInBalance": true,
                  "ListCurrencyInfo": [
                    {"id":"c1","shortname":"UAH","rate":1.0,"balance":1234.56,"display":true},
                    {"id":"c2","shortname":"USD","rate":41.0,"balance":0,"display":true}
                  ]
                },
                {
                  "id":"a2","name":"Card","display":true,"includeInBalance":false,
                  "ListCurrencyInfo":[{"id":"c3","shortname":"UAH","rate":1.0,"balance":"500.00","display":true}]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `maps multi-currency account into one candidate per currency with suffix`() {
        val response = json.decodeFromString<BalanceListResponse>(balanceSample)
        val candidates = HomeMoneyMapper.toCandidates(response)

        assertEquals(3, candidates.size)

        val walletUah = candidates.first { it.displayName == "Wallet (UAH)" }
        assertEquals("UAH", walletUah.currencyCode)
        assertEquals(123456L, walletUah.balanceMinor) // 1234.56 → minor units
        assertTrue(walletUah.includeInTotal)
        assertEquals("Cash", walletUah.groupName)
        assertTrue(walletUah.defaultSelected)

        val walletUsd = candidates.first { it.displayName == "Wallet (USD)" }
        assertEquals(0L, walletUsd.balanceMinor)
        assertFalse("zero-balance rows are not pre-selected", walletUsd.defaultSelected)
    }

    @Test
    fun `single-currency account keeps its plain name and parses string balances`() {
        val response = json.decodeFromString<BalanceListResponse>(balanceSample)
        val candidates = HomeMoneyMapper.toCandidates(response)

        val card = candidates.first { it.displayName == "Card" }
        assertEquals(50000L, card.balanceMinor) // "500.00" (string) → minor units
        assertFalse("includeInBalance=false maps to includeInTotal=false", card.includeInTotal)
        assertTrue(card.defaultSelected)
    }

    @Test
    fun `existing accounts are flagged and not pre-selected`() {
        val response = json.decodeFromString<BalanceListResponse>(balanceSample)
        val existing = setOf("wallet (uah)" to "UAH")

        val candidates = HomeMoneyMapper.toCandidates(response, existing)

        val walletUah = candidates.first { it.displayName == "Wallet (UAH)" }
        assertTrue(walletUah.alreadyExists)
        assertFalse(walletUah.defaultSelected)
    }

    @Test
    fun `error envelope parses and yields no candidates`() {
        val errorSample =
            """{"Error":{"code":7,"message":"Invalid token"},"defaultcurrency":null,"name":null,"ListGroupInfo":[]}"""
        val response = json.decodeFromString<BalanceListResponse>(errorSample)

        assertEquals(7, response.error?.code)
        assertEquals("Invalid token", response.error?.message)
        assertTrue(HomeMoneyMapper.toCandidates(response).isEmpty())
    }

    @Test
    fun `token error response parses`() {
        val tokenError =
            """{"Error":{"code":2,"message":"Invalid username"},"access_token":null,"token_type":"example","expires_in":"315360000","refresh_token":null}"""
        val response = json.decodeFromString<TokenResponse>(tokenError)

        assertEquals(2, response.error?.code)
        assertEquals(null, response.accessToken)
    }

    @Test
    fun `currency line hidden in HomeMoney is not pre-selected`() {
        val sample = """
            {"Error":{"code":0},"ListGroupInfo":[{"name":"G","ListAccountInfo":[
              {"name":"Hidden line","display":true,"includeInBalance":true,"ListCurrencyInfo":[
                {"shortname":"UAH","balance":100.00,"display":false}
              ]}
            ]}]}
        """.trimIndent()
        val candidates = HomeMoneyMapper.toCandidates(json.decodeFromString(sample))

        val row = candidates.single()
        assertEquals(10000L, row.balanceMinor)
        assertFalse("a hidden currency line must not be pre-checked", row.defaultSelected)
    }

    @Test
    fun `single-to-multi currency change still detects the existing account`() {
        // Account was imported while single-currency (stored bare as "Wallet"); it later gained USD.
        val sample = """
            {"Error":{"code":0},"ListGroupInfo":[{"name":"Cash","ListAccountInfo":[
              {"name":"Wallet","display":true,"includeInBalance":true,"ListCurrencyInfo":[
                {"shortname":"UAH","balance":50.00,"display":true},
                {"shortname":"USD","balance":10.00,"display":true}
              ]}
            ]}]}
        """.trimIndent()
        val existing = setOf("wallet" to "UAH") // bare name, as a prior single-currency import stored it

        val candidates = HomeMoneyMapper.toCandidates(json.decodeFromString(sample), existing)

        val walletUah = candidates.first { it.displayName == "Wallet (UAH)" }
        assertTrue("bare-name fallback should flag the pre-existing UAH account", walletUah.alreadyExists)
        assertFalse(walletUah.defaultSelected)
    }

    @Test
    fun `same name and currency in different groups are disambiguated by group`() {
        val sample = """
            {"Error":{"code":0},"ListGroupInfo":[
              {"name":"Home","ListAccountInfo":[
                {"name":"Cash","display":true,"includeInBalance":true,"ListCurrencyInfo":[{"shortname":"UAH","balance":1.00,"display":true}]}
              ]},
              {"name":"Office","ListAccountInfo":[
                {"name":"Cash","display":true,"includeInBalance":true,"ListCurrencyInfo":[{"shortname":"UAH","balance":2.00,"display":true}]}
              ]}
            ]}
        """.trimIndent()
        val candidates = HomeMoneyMapper.toCandidates(json.decodeFromString(sample))

        val names = candidates.map { it.displayName }.toSet()
        assertEquals(setOf("Cash · Home", "Cash · Office"), names)
    }
}
