package com.personalmoney.core.billing

import com.personalmoney.core.data.KeyValueStore
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.network.HomeBudgetApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the client does with the server's answer about premium - and, just as importantly,
 * what it does not do with it.
 *
 * The entitlement is the server's decision; this layer caches it so a launch with no
 * network does not flash the free plan at a subscriber, and hands purchase tokens over to
 * be checked. Everything below is about those two jobs. The bodies are the shape
 * `BillingController` actually returns.
 */
class EntitlementRepositoryTest {

    /** Enough of a [KeyValueStore] to remember something between two reads. */
    private class MemoryStore : KeyValueStore {
        private val values = MutableStateFlow<Map<String, Any>>(emptyMap())

        override fun observeString(key: String): Flow<String?> = values.map { it[key] as? String }
        override fun observeLong(key: String): Flow<Long?> = values.map { it[key] as? Long }
        override fun observeBoolean(key: String): Flow<Boolean?> = values.map { it[key] as? Boolean }
        override suspend fun putString(key: String, value: String) = put(key, value)
        override suspend fun putLong(key: String, value: Long) = put(key, value)
        override suspend fun putBoolean(key: String, value: Boolean) = put(key, value)
        override suspend fun remove(vararg keys: String) {
            values.value = values.value - keys.toSet()
        }

        override suspend fun updateLong(key: String, transform: (Long) -> Long): Long {
            val next = transform(values.value[key] as? Long ?: 0L)
            put(key, next)
            return next
        }

        private fun put(key: String, value: Any) {
            values.value = values.value + (key to value)
        }
    }

    /** A store that holds whatever the test says it holds. */
    private class FakeStore(
        override val available: Boolean = true,
        private val owned: List<PurchaseOutcome.Bought> = emptyList()
    ) : StoreBilling {
        override suspend fun products(productIds: List<String>) = emptyList<StoreProduct>()
        override suspend fun buy(productId: String): PurchaseOutcome = PurchaseOutcome.Cancelled
        override suspend fun ownedPurchases(): List<PurchaseOutcome.Bought> = owned
    }

    private val requests = mutableListOf<HttpRequestData>()

    private fun apiReturning(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HomeBudgetApi {
        val client = HttpClient(
            MockEngine { request ->
                requests += request
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        ) {
            expectSuccess = true
            defaultRequest { url("http://localhost/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return HomeBudgetApi(client)
    }

    private fun repository(
        body: String,
        store: StoreBilling = FakeStore(),
        preferences: UserPreferences = UserPreferences(MemoryStore()),
        status: HttpStatusCode = HttpStatusCode.OK
    ) = EntitlementRepository(apiReturning(body, status), store, preferences)

    private val premiumBody = """
        {"premium":true,"expiresAt":"2027-01-01T00:00:00Z","source":"PLAY","autoRenewing":true,
         "productId":"premium_monthly","purchasesAccepted":true,
         "products":["premium_monthly","premium_yearly"]}
    """.trimIndent()

    private val freeBody = """
        {"premium":false,"source":"NONE","autoRenewing":false,
         "purchasesAccepted":false,"products":[]}
    """.trimIndent()

    // -- reading the entitlement ----------------------------------------------

    @Test
    fun `the server's answer becomes the entitlement`() = runTest {
        val entitlement = repository(premiumBody).refresh()!!

        assertTrue(entitlement.premium)
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), entitlement.expiresAt)
        assertEquals(EntitlementSource.PLAY, entitlement.source)
        assertEquals(listOf("premium_monthly", "premium_yearly"), entitlement.products)
        assertTrue(entitlement.purchasesAccepted)
    }

    @Test
    fun `a browser is told it cannot buy here`() = runTest {
        val entitlement = repository(freeBody).refresh()!!

        assertFalse(entitlement.purchasesAccepted)
        assertTrue(entitlement.products.isEmpty())
    }

    @Test
    fun `a cancelled subscription that is still paid up reads as lapsing`() = runTest {
        val body = premiumBody.replace(""""autoRenewing":true""", """"autoRenewing":false""")

        assertTrue(repository(body).refresh()!!.lapsing)
    }

    @Test
    fun `a subscription that renews is not lapsing`() = runTest {
        assertFalse(repository(premiumBody).refresh()!!.lapsing)
    }

    @Test
    fun `an expiry the client cannot parse does not take premium away`() = runTest {
        // The server decided `premium`; this field is only ever displayed, so a date this
        // cannot read must cost the user the date and not the subscription.
        val body = premiumBody.replace("2027-01-01T00:00:00Z", "next Tuesday")

        val entitlement = repository(body).refresh()!!

        assertTrue(entitlement.premium)
        assertNull(entitlement.expiresAt)
    }

    @Test
    fun `an unreachable server leaves the entitlement alone rather than failing`() = runTest {
        val repository = repository("no", status = HttpStatusCode.InternalServerError)

        assertNull(repository.refresh())
        assertFalse(repository.entitlement.value.premium)
    }

    // -- the cache -------------------------------------------------------------

    @Test
    fun `premium reads from the cache before the server has answered`() = runTest {
        val preferences = UserPreferences(MemoryStore())
        preferences.setPremium(true, null)

        val repository = repository(freeBody, preferences = preferences)

        assertTrue(repository.premium.first(), "a subscriber should not be shown the free plan on launch")
    }

    @Test
    fun `the server overrules a stale cache`() = runTest {
        val preferences = UserPreferences(MemoryStore())
        preferences.setPremium(true, null)
        val repository = repository(freeBody, preferences = preferences)

        repository.refresh()

        assertFalse(repository.premium.first(), "the server said the subscription is over")
        assertFalse(preferences.premium.first(), "and the cache should have been corrected")
    }

    @Test
    fun `an answered entitlement is written to the cache`() = runTest {
        val preferences = UserPreferences(MemoryStore())

        repository(premiumBody, preferences = preferences).refresh()

        assertTrue(preferences.premium.first())
        assertEquals(
            Instant.parse("2027-01-01T00:00:00Z").toEpochMilliseconds(),
            preferences.premiumExpiresAtMs.first()
        )
    }

    // -- handing over purchases -------------------------------------------------

    @Test
    fun `a purchase is sent to the server to be checked`() = runTest {
        val repository = repository(premiumBody)

        val entitlement = repository.submit(PurchaseOutcome.Bought("token-123", "premium_monthly"))

        assertTrue(entitlement.premium)
        assertEquals("/api/billing/play/purchase", requests.single().url.encodedPath)
    }

    @Test
    fun `every purchase the store already holds is handed over on launch`() = runTest {
        // What makes a reinstall work: Play knows about the subscription, the server does
        // not, and nothing else would ever tell it.
        val store = FakeStore(
            owned = listOf(
                PurchaseOutcome.Bought("token-a", "premium_monthly"),
                PurchaseOutcome.Bought("token-b", "premium_yearly")
            )
        )

        repository(premiumBody, store = store).syncOwnedPurchases()

        assertEquals(2, requests.size)
        assertTrue(requests.all { it.url.encodedPath == "/api/billing/play/purchase" })
    }

    @Test
    fun `with nothing to hand over it just re-reads the entitlement`() = runTest {
        repository(freeBody, store = FakeStore(owned = emptyList())).syncOwnedPurchases()

        assertEquals("/api/billing/status", requests.single().url.encodedPath)
    }

    @Test
    fun `a platform with no store is not asked about purchases at all`() = runTest {
        repository(freeBody, store = FakeStore(available = false)).syncOwnedPurchases()

        assertTrue(requests.isEmpty(), "the browser has nothing to restore and should not ask")
    }
}
