package com.personalmoney.core.network

import com.personalmoney.core.domain.SubscriptionFrequency
import com.personalmoney.core.domain.TransactionType
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
    fun `decodes a real subscription payload`() {
        val payload = """
            {"id":1,"uuid":"aab937a9-032d-44d8-be4d-4eb4b68373cc","name":"Netflix",
             "account":{"id":1,"uuid":"ed4a0c0a-b74c-411d-927c-d331a5da55d7","name":"Checking",
                        "type":"CHECKING","balance":"3694.26","initialBalance":"1250.75",
                        "currency":{"id":1,"code":"USD","name":"US Dollar","symbol":"${'$'}",
                                    "decimals":2,"sortOrder":0,"exchangeRateToBase":"1.000000",
                                    "isBase":true,"createdAt":"2026-08-18T17:24:07.658552"},
                        "icon":"wallet","color":"#4F46E5","archived":false,"sortOrder":0,
                        "includeInTotal":true,"createdAt":"2026-08-18T15:24:51.162992",
                        "updatedAt":"2026-08-18T13:24:51.163Z"},
             "category":{"id":6,"uuid":"3092d75c-74a7-48a8-8019-838a1b343471",
                         "name":"Entertainment","type":"EXPENSE","icon":"film","color":"#EC4899",
                         "archived":false,"sortOrder":5,"isDefault":true,"mccCodes":[],
                         "createdAt":"2026-08-18T15:24:25.15747"},
             "amount":"15.99","currencyCode":"USD","frequency":"MONTHLY","intervalCount":1,
             "nextChargeDate":"2026-09-01","startDate":"2026-08-01","active":true,
             "reminderMinutesBefore":1440,"icon":"subscriptions","color":"#008080",
             "note":"family plan","createdAt":"2026-08-19T00:49:47.372695",
             "updatedAt":"2026-08-18T22:49:47.373Z"}
        """.trimIndent()

        val subscription = json.decodeFromString<RestSubscription>(payload)

        assertEquals("Netflix", subscription.name)
        assertEquals(SubscriptionFrequency.MONTHLY, subscription.frequency)
        assertEquals("15.99", subscription.amount)
        // The account and category arrive nested, not as ids - the shared model flattens them.
        assertEquals(1L, subscription.account.id)
        assertEquals(6L, subscription.category?.id)
        // endDate is simply absent when the subscription never ends, rather than null.
        assertNull(subscription.endDate)
        assertEquals("2026-09-01", subscription.nextChargeDate)
    }

    @Test
    fun `decodes an fx rate payload`() {
        // Hand-built from FxController.FxRateResponse rather than captured: the rate table
        // is filled by a nightly job, so a fresh install has none to capture. Five plain
        // fields, and `rate` is a BigDecimal - hence a string, like every other amount.
        val payload =
            """[{"baseCode":"USD","quoteCode":"EUR","rate":"0.857100",
                 "rateDate":"2026-08-18","source":"FRANKFURTER"}]"""

        val rates = json.decodeFromString<List<RestFxRate>>(payload)

        assertEquals(1, rates.size)
        assertEquals("USD", rates[0].baseCode)
        assertEquals("0.857100", rates[0].rate)
        assertEquals("FRANKFURTER", rates[0].source)
    }

    @Test
    fun `decodes a real bank provider payload`() {
        val payload = """
            [{"id":"MONOBANK","displayName":"Monobank (personal token)","helpUrl":"https://api.monobank.ua",
              "authMode":"CREDENTIALS",
              "credentialFields":[{"name":"token","label":"Personal API token","type":"SECRET",
                "placeholder":"u1cx...","helpText":"Open api.monobank.ua, confirm in the Monobank app, and copy the token.",
                "required":true}]}]
        """.trimIndent()

        val providers = json.decodeFromString<List<RestBankProvider>>(payload)

        assertEquals(1, providers.size)
        assertEquals("CREDENTIALS", providers[0].authMode)
        // The credential form is built from this list rather than hardcoded per provider,
        // so a new field on the server reaches the UI without a client change.
        val field = providers[0].credentialFields.single()
        assertEquals("token", field.name)
        // SECRET, not "password" - the server's own vocabulary, and what decides masking.
        assertEquals("SECRET", field.type)
        assertTrue(field.required)
    }

    @Test
    fun `decodes a consent provider and the challenge it starts`() {
        // No credentialFields at all, which is the whole point: there is nothing to type.
        val providerPayload = """
            [{"id":"MONOBANK_CORPORATE","displayName":"Monobank","authMode":"CONSENT",
              "credentialFields":[]}]
        """.trimIndent()

        val provider = json.decodeFromString<List<RestBankProvider>>(providerPayload).single()

        assertEquals("CONSENT", provider.authMode)
        assertTrue(provider.credentialFields.isEmpty())
        // Omitted by the server, which drops nulls - so it has to have a default here.
        assertNull(provider.helpUrl)

        val challengePayload = """
            {"provider":"MONOBANK_CORPORATE","reference":"uLkwh3NzFAfEkj7urj5C7AU_",
             "approveUrl":"https://mbnk.app/auth/uLkwh3NzFAfEkj7urj5C7AU_",
             "expiresAt":"2026-09-10T12:34:56Z","qrRows":["101","010","101"]}
        """.trimIndent()

        val challenge = json.decodeFromString<RestConsentChallenge>(challengePayload)

        assertEquals("uLkwh3NzFAfEkj7urj5C7AU_", challenge.reference)
        // Rows of '1' and '0', one per line of the code - the client draws it, so a
        // different shape here is a QR nobody can scan rather than a decoding failure.
        assertEquals(3, challenge.qrRows.size)

        // The poll's ordinary answer: no connection yet, and not an error.
        val pending = json.decodeFromString<RestConsentStatus>("""{"status":"PENDING"}""")

        assertEquals("PENDING", pending.status)
        assertNull(pending.connection)
    }

    @Test
    fun `decodes a real bank connection payload`() {
        val payload = """
            [{"id":1,"provider":"MONOBANK","providerDisplayName":"Monobank","label":"Dev Holder",
              "status":"INVALID","linkedAccountIds":[],"createdAt":"2026-08-20T00:10:38.01122"}]
        """.trimIndent()

        val connections = json.decodeFromString<List<RestBankConnection>>(payload)

        val connection = connections.single()
        // Upper case, and the server's own spelling. The repository maps it to an enum so
        // that no screen ever compares this string.
        assertEquals("INVALID", connection.status)
        assertEquals("Dev Holder", connection.label)
        assertTrue(connection.linkedAccountIds.isEmpty())
        // Absent from the JSON entirely: the server omits nulls
        // (`default-property-inclusion: non_null`), so every optional field here has to
        // carry a default rather than merely be nullable.
        assertNull(connection.lastVerifiedAt)
    }

    @Test
    fun `decodes a real csv preview payload`() {
        val payload = """
            {"headers":["Date","Description","Amount","Currency"],
             "mapping":{"date":"Date","description":"Description","amount":"Amount","currency":"Currency"},
             "rows":[{"externalId":"csv:299586ed9ab50e5077ece49c","occurredAt":"2026-08-01T00:00:00Z",
                      "date":"2026-08-01","description":"Coffee shop","amountMinor":450,
                      "currencyCode":"USD","type":"EXPENSE","alreadyImported":false},
                     {"externalId":"csv:2019495562d78fff3201ee66","occurredAt":"2026-08-02T00:00:00Z",
                      "date":"2026-08-02","description":"Salary","amountMinor":200000,
                      "currencyCode":"USD","type":"INCOME","alreadyImported":false}],
             "skipped":["unparseable date 'not-a-date'"],"currencyMismatch":[]}
        """.trimIndent()

        val preview = json.decodeFromString<RestCsvPreview>(payload)

        // Detection filled in all four; `debit`, `credit` and `dateFormat` are absent from
        // the JSON, which is why every one of them needs a default and not just a nullable
        // type - the server omits nulls entirely.
        assertEquals("Date", preview.mapping.date)
        assertEquals("Amount", preview.mapping.amount)
        assertNull(preview.mapping.debit)
        assertNull(preview.mapping.dateFormat)

        val expense = preview.rows.first()
        // Positive magnitude with the direction in `type`, the same convention the rest of
        // the app uses - not a negative number.
        assertEquals(450L, expense.amountMinor)
        assertEquals(TransactionType.EXPENSE, expense.type)
        assertEquals(TransactionType.INCOME, preview.rows[1].type)
        // Kept as text and handed straight back on confirm; parsing it would only risk
        // returning something subtly different.
        assertEquals("2026-08-01T00:00:00Z", expense.occurredAt)
        assertNull(expense.suggestedCategoryId)

        // The reason, not just a count - which is what makes showing these lines useful.
        assertEquals(listOf("unparseable date 'not-a-date'"), preview.skipped)
    }

    @Test
    fun `decodes an empty transaction page`() {
        val payload = """{"content":[],"page":0,"size":1,"totalElements":0,"totalPages":0}"""

        val page = json.decodeFromString<PagedResponse<RestTransaction>>(payload)

        assertTrue(page.content.isEmpty())
        assertEquals(0, page.totalPages)
    }
}
