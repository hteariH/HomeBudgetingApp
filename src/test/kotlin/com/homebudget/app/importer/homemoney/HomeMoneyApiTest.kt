package com.homebudget.app.importer.homemoney

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

/**
 * Reading what homemoney.ua sends back.
 *
 * The payloads below are shaped from their published operation documentation - the WSDL
 * declares every response type as empty, so there is nothing to generate from. They are
 * the contract this client is written against; replace them with captured responses from
 * a live account as those become available, and any drift will fail here rather than
 * halfway through somebody's migration.
 */
class HomeMoneyApiTest {

    private val api = HomeMoneyApi(jacksonObjectMapper(), "https://homemoney.ua")

    // ── Errors ──────────────────────────────────────────────────────────────

    @Test
    fun `a refused token is an auth failure, not an empty list`() {
        // The trap this whole client is built around: HomeMoney answers 200 with an
        // error object, so anything that only checks the status code reads "invalid
        // token" as "you have no categories" and cheerfully imports nothing.
        val body = """{"Error":{"code":7,"message":"Invalid token"},"ListCategory":[]}"""

        val thrown = assertThrows<HomeMoneyAuthException> {
            api.parse("CategoryList", body, HomeMoneyCategoryList::class.java)
        }
        assertTrue(thrown.message!!.contains("Invalid token"))
    }

    @Test
    fun `an expired token is also an auth failure, so the refresh path runs`() {
        val body = """{"Error":{"code":6,"message":"Token is expired"},"ListCategory":[]}"""
        assertThrows<HomeMoneyAuthException> {
            api.parse("CategoryList", body, HomeMoneyCategoryList::class.java)
        }
    }

    @Test
    fun `any other error code is a request failure`() {
        val body = """{"Error":{"code":1,"message":"General error"},"ListCategory":[]}"""
        assertThrows<HomeMoneyRequestException> {
            api.parse("CategoryList", body, HomeMoneyCategoryList::class.java)
        }
    }

    @Test
    fun `code zero means it worked`() {
        val body = """{"Error":{"code":0,"message":""},"ListCategory":[]}"""
        val parsed = api.parse("CategoryList", body, HomeMoneyCategoryList::class.java)
        assertEquals(0, parsed.categories.size)
    }

    @Test
    fun `an HTML answer is reported as such`() {
        // What a path with a trailing slash actually returns. Left as a Jackson stack
        // trace it sends whoever is debugging it entirely the wrong way.
        val thrown = assertThrows<HomeMoneyRequestException> {
            api.parse("TokenPassword", "TokenPassword/ Web Service method name is not valid.", HomeMoneyToken::class.java)
        }
        assertTrue(thrown.message!!.contains("not JSON"))
    }

    // ── Payloads ────────────────────────────────────────────────────────────

    @Test
    fun `a balance list unfolds into groups, accounts and their currencies`() {
        val body = """
            {
              "Error": {"code": 0, "message": ""},
              "defaultcurrency": "980",
              "name": "UAH",
              "ListGroupInfo": [
                {
                  "id": "1", "name": "Cash", "hasAccounts": true, "order": 0,
                  "ListAccountInfo": [
                    {
                      "id": "11", "name": "Wallet", "isDefault": true, "display": true,
                      "includeInBalance": true,
                      "ListCurrencyInfo": [
                        {"id": "980", "shortname": "UAH", "rate": "1", "balance": "1234.56", "display": true},
                        {"id": "840", "shortname": "USD", "rate": "41.2", "balance": "100.00", "display": true}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = api.parse("BalanceList", body, HomeMoneyBalanceList::class.java)

        assertEquals(1, parsed.groups.size)
        val account = parsed.groups[0].accounts.single()
        assertEquals("Wallet", account.name)
        assertEquals(2, account.currencies.size)
        assertEquals(BigDecimal("1234.56"), account.currencies[0].balance)
        assertEquals("USD", account.currencies[1].shortName)
    }

    @Test
    fun `a transaction list is read including the three-r currency field`() {
        // CurrrencyId is spelled that way by HomeMoney. Getting it wrong costs nothing
        // loud: the field simply arrives empty and every operation lands on no account.
        val body = """
            {
              "Error": {"code": 0, "message": ""},
              "ListTransaction": [
                {
                  "TransactionId": "555", "Date": "2026-03-04T10:15:00", "DateUnix": 1772619300,
                  "CategoryId": "7", "CategoryFullName": "Food / Groceries",
                  "Description": "Silpo", "isPlan": false, "type": 1,
                  "Total": "-249.90", "AccountId": "11", "CurrrencyId": "980",
                  "TransTotal": "0", "TransAccountId": null, "TransCurrencyId": null,
                  "GUID": "9d1f-aaa", "CreateDate": "2026-03-04T10:16:00"
                }
              ]
            }
        """.trimIndent()

        val parsed = api.parse("TransactionListByPeriod", body, HomeMoneyTransactionList::class.java)

        val operation = parsed.transactions.single()
        assertEquals("980", operation.currencyId)
        assertEquals("11", operation.accountId)
        assertEquals(BigDecimal("-249.90"), operation.total)
        assertEquals("9d1f-aaa", operation.guid)
        assertEquals(HomeMoneyKind.EXPENSE, operation.type)
    }

    @Test
    fun `a token response carries the access and refresh tokens`() {
        val body = """
            {"Error":{"code":0,"message":""},"access_token":"abc","token_type":"example",
             "expires_in":"315360000","refresh_token":"def"}
        """.trimIndent()

        val token = api.parse("TokenAuthorizationCode", body, HomeMoneyToken::class.java)

        assertEquals("abc", token.accessToken)
        assertEquals("def", token.refreshToken)
    }

    @Test
    fun `unknown fields do not break the read`() {
        // Their API grows fields without warning, and an import that stops working
        // because HomeMoney added a flag would be an own goal.
        val body = """{"Error":{"code":0},"UserName":"anna","IsPremium":true,"SomethingNew":42}"""
        val info = api.parse("UserInfo", body, HomeMoneyUserInfo::class.java)
        assertEquals("anna", info.userName)
        assertTrue(info.isPremium)
    }
}
