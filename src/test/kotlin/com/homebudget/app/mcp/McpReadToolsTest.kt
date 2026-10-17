package com.homebudget.app.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.Account
import com.homebudget.app.domain.AccountType
import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.CreateTransferRequest
import com.homebudget.app.mcp.tools.SearchTransactionsTool
import com.homebudget.app.mcp.tools.SummarizeSpendingTool
import com.homebudget.app.receipt.Receipt
import com.homebudget.app.receipt.ReceiptItem
import com.homebudget.app.receipt.ReceiptRepository
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.TransferService
import com.homebudget.app.token.AccessTokenService
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.ZoneOffset

/** `search_transactions` and `summarize_spending`, called directly and once each over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestIdentity::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:mcp-read-tools;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=true",
    "app.base-url=https://money.example.com"
])
class McpReadToolsTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var tokens: AccessTokenService
    @Autowired private lateinit var accounts: AccountRepository
    @Autowired private lateinit var categories: CategoryRepository
    @Autowired private lateinit var currencies: CurrencyRepository
    @Autowired private lateinit var transactions: TransactionRepository
    @Autowired private lateinit var receipts: ReceiptRepository
    @Autowired private lateinit var transfers: TransferService
    @Autowired private lateinit var search: SearchTransactionsTool
    @Autowired private lateinit var summarize: SummarizeSpendingTool
    @Autowired private lateinit var mapper: ObjectMapper

    private val http = HttpClient.newHttpClient()
    private lateinit var me: UserPrincipal
    private lateinit var card: Account // UAH
    private lateinit var cash: Account // USD
    private lateinit var food: Category
    private lateinit var fun_: Category

    @BeforeEach
    fun setUp() {
        me = identity.newMember("reader")
        card = account(me, "Card", "UAH")
        cash = account(me, "Cash", "USD")
        food = category(me, "Food", CategoryType.EXPENSE)
        fun_ = category(me, "Fun", CategoryType.EXPENSE)
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun account(owner: UserPrincipal, name: String, currency: String) = accounts.save(
        Account(name = name, type = AccountType.CHECKING, household = owner.household,
            currency = currencies.findByCode(currency)!!, initialBalanceMinor = 0)
    )

    private fun category(owner: UserPrincipal, name: String, type: CategoryType) =
        categories.save(Category(name = name, type = type, household = owner.household))

    private fun tx(
        owner: UserPrincipal, account: Account, category: Category?, minor: Long, day: String,
        type: TransactionType = TransactionType.EXPENSE, note: String? = null, receipt: Receipt? = null,
        deleted: Boolean = false
    ): Transaction = transactions.save(
        Transaction(
            household = owner.household, createdBy = owner.user, account = account, category = category,
            amountMinor = minor, currencyCode = account.currency.code, accountAmountMinor = minor, type = type,
            occurredAt = LocalDate.parse(day).atTime(12, 0).toInstant(ZoneOffset.UTC), description = note,
            receiptId = receipt?.id, deleted = deleted
        )
    )

    private fun call(tool: McpTool, args: Map<String, Any?> = emptyMap(), as_: UserPrincipal = me): CallToolResult =
        McpServerConfig.invoke(tool, as_, args)

    private fun json(result: CallToolResult): JsonNode {
        assertFalse(result.isError == true, "tool error: ${result.content()}")
        return mapper.readTree((result.content()[0] as TextContent).text())
    }

    private fun errorText(result: CallToolResult): String {
        assertTrue(result.isError == true, "expected a tool error")
        return (result.content()[0] as TextContent).text()
    }

    private fun uuids(node: JsonNode) = node["transactions"].map { it["uuid"].asText() }

    // ---- search ----------------------------------------------------------------------

    @Test
    fun `search returns newest first with amounts per currency and the author`() {
        val a = tx(me, card, food, 12550, "2026-03-01", note = "Lunch")
        val b = tx(me, cash, fun_, 900, "2026-03-05", note = "Movie")
        val result = json(call(search))
        assertEquals(listOf(b.uuid, a.uuid), uuids(result))
        val first = result["transactions"][1]
        assertEquals("125.50", first["amount"].asText())
        assertEquals("UAH", first["currency"].asText())
        assertEquals("2026-03-01", first["date"].asText())
        assertEquals("Card", first["account"]["name"].asText())
        assertEquals("Food", first["category"]["name"].asText())
        assertEquals("reader Test", first["author"].asText())
        assertEquals("APP", first["source"].asText())
        assertEquals("Lunch", first["note"].asText())
        assertNull(result["nextCursor"])
    }

    @Test
    fun `search filters by range, account, category, type, text and amount`() {
        val lunch = tx(me, card, food, 5000, "2026-03-01", note = "Lunch with Ann")
        val movie = tx(me, cash, fun_, 900, "2026-03-10", note = "Movie")
        val pay = tx(me, card, null, 100000, "2026-03-15", TransactionType.INCOME, note = "Pay")

        assertEquals(listOf(movie.uuid, lunch.uuid), uuids(json(call(search, mapOf("to" to "2026-03-14")))))
        assertEquals(listOf(pay.uuid, movie.uuid), uuids(json(call(search, mapOf("from" to "2026-03-10")))))
        assertEquals(listOf(pay.uuid, lunch.uuid), uuids(json(call(search, mapOf("account" to "card")))))
        assertEquals(listOf(movie.uuid), uuids(json(call(search, mapOf("category" to fun_.uuid)))))
        assertEquals(listOf(pay.uuid), uuids(json(call(search, mapOf("type" to "INCOME")))))
        assertEquals(listOf(lunch.uuid), uuids(json(call(search, mapOf("text" to "ANN")))))
        assertEquals(listOf(pay.uuid), uuids(json(call(search, mapOf("minAmount" to "500")))))
        assertEquals(listOf(movie.uuid), uuids(json(call(search, mapOf("maxAmount" to "9.00")))))
        assertEquals(listOf(lunch.uuid), uuids(json(call(search, mapOf("minAmount" to "10", "maxAmount" to "100")))))
    }

    @Test
    fun `search text matches a receipt merchant and the receipt is returned`() {
        val receipt = receipts.save(Receipt(household = me.household, createdBy = me.user, imagePath = "x.jpg",
            merchant = "Silpo Market", currencyCode = "UAH", totalMinor = 3000))
        receipt.items.add(ReceiptItem(receipt = receipt, position = 0, name = "Milk", quantity = BigDecimal("2"), priceMinor = 1500))
        receipts.save(receipt)
        val t = tx(me, card, food, 3000, "2026-03-02", receipt = receipt)
        tx(me, card, food, 700, "2026-03-03", note = "Bus")

        val found = json(call(search, mapOf("text" to "silpo")))
        assertEquals(listOf(t.uuid), uuids(found))
        val r = found["transactions"][0]["receipt"]
        assertEquals("Silpo Market", r["merchant"].asText())
        assertEquals("30.00", r["total"].asText())
        assertEquals("Milk", r["items"][0]["name"].asText())
        assertEquals("2", r["items"][0]["quantity"].asText())
        assertEquals("15.00", r["items"][0]["price"].asText())
    }

    @Test
    fun `search lists a transfer once with its destination`() {
        McpPrincipalScope.runAs(me) {
            transfers.create(CreateTransferRequest(card.id, cash.id, BigDecimal("400.00"), BigDecimal("10.00"),
                date = LocalDate.parse("2026-03-04")))
        }
        val result = json(call(search, mapOf("type" to "TRANSFER")))
        assertEquals(1, result["transactions"].size())
        val t = result["transactions"][0]
        assertEquals("400.00", t["amount"].asText())
        assertEquals("UAH", t["currency"].asText())
        assertEquals("Cash", t["toAccount"]["name"].asText())
        assertEquals("10.00", t["toAmount"].asText())
        assertEquals("USD", t["toCurrency"].asText())
        // Filtering by the destination account finds it too, and still only once.
        assertEquals(1, json(call(search, mapOf("account" to "Cash")))["transactions"].size())
    }

    @Test
    fun `search never shows tombstones or another household's rows`() {
        val mine = tx(me, card, food, 100, "2026-03-01")
        tx(me, card, food, 200, "2026-03-02", deleted = true)
        val other = identity.newMember("other")
        tx(other, account(other, "Theirs", "UAH"), null, 300, "2026-03-03")
        assertEquals(listOf(mine.uuid), uuids(json(call(search))))
    }

    @Test
    fun `search pages are disjoint and cover every row`() {
        val all = (1..7).map { tx(me, card, food, it * 100L, "2026-03-0${if (it > 4) 4 else it}") }
        val seen = mutableListOf<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val args = mutableMapOf<String, Any?>("limit" to 3)
            cursor?.let { args["cursor"] = it }
            val page = json(call(search, args))
            assertTrue(page["transactions"].size() <= 3)
            seen += uuids(page)
            cursor = page["nextCursor"]?.asText()
            pages++
        } while (cursor != null)
        assertEquals(3, pages)
        assertEquals(7, seen.size)
        assertEquals(all.map { it.uuid }.toSet(), seen.toSet())
        // Same order a single big page gives.
        assertEquals(uuids(json(call(search))), seen)
    }

    @Test
    fun `search pages through an amount filter`() {
        (1..6).forEach { tx(me, card, food, it * 1000L, "2026-03-0$it") } // 10.00 .. 60.00
        val page1 = json(call(search, mapOf("minAmount" to "25", "limit" to 2)))
        assertEquals(listOf("60.00", "50.00"), page1["transactions"].map { it["amount"].asText() })
        val page2 = json(call(search, mapOf("minAmount" to "25", "limit" to 2, "cursor" to page1["nextCursor"].asText())))
        assertEquals(listOf("40.00", "30.00"), page2["transactions"].map { it["amount"].asText() })
        assertNull(page2["nextCursor"])
    }

    @Test
    fun `search rejects bad input as a tool error`() {
        assertEquals("\"from\" must be a date like 2026-03-31", errorText(call(search, mapOf("from" to "yesterday"))))
        assertEquals("\"limit\" must be between 1 and 100", errorText(call(search, mapOf("limit" to 101))))
        assertTrue(errorText(call(search, mapOf("cursor" to "garbage"))).startsWith("Invalid cursor"))
        assertEquals("Account \"Nope\" not found — list_accounts shows the accounts",
            errorText(call(search, mapOf("account" to "Nope"))))
    }

    @Test
    fun `search finds rows of an archived category by uuid and by name`() {
        val old = categories.save(Category(name = "Old hobby", type = CategoryType.EXPENSE, household = me.household, archived = true))
        val t = tx(me, card, old, 500, "2026-03-01")
        tx(me, card, food, 600, "2026-03-02")
        assertEquals(listOf(t.uuid), uuids(json(call(search, mapOf("category" to old.uuid)))))
        assertEquals(listOf(t.uuid), uuids(json(call(search, mapOf("category" to "old hobby")))))
    }

    @Test
    fun `a cursor naming another household's row leaks nothing`() {
        val mine = tx(me, card, food, 100, "2026-03-01")
        val other = identity.newMember("other")
        val theirs = tx(other, account(other, "Theirs", "UAH"), null, 300, "2026-03-03")
        val cursor = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("${theirs.occurredAt}|${theirs.id + 1}".toByteArray())
        assertEquals(listOf(mine.uuid), uuids(json(call(search, mapOf("cursor" to cursor)))))
    }

    @Test
    fun `a cursor without its separator is a tool error`() {
        val cursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("2026-03-01T12:00:00Z".toByteArray())
        assertTrue(errorText(call(search, mapOf("cursor" to cursor))).startsWith("Invalid cursor"))
    }

    // ---- summary ---------------------------------------------------------------------

    private fun seedSpending() {
        tx(me, card, food, 10000, "2026-01-10")
        tx(me, card, food, 5000, "2026-02-10")
        tx(me, cash, food, 2000, "2026-02-11")
        tx(me, card, fun_, 30000, "2026-02-12")
        tx(me, card, null, 700, "2026-02-13")
        tx(me, card, food, 99900, "2026-02-14", deleted = true)
        tx(me, card, null, 88800, "2026-02-15", TransactionType.INCOME)
        McpPrincipalScope.runAs(me) {
            transfers.create(CreateTransferRequest(card.id, cash.id, BigDecimal("400.00"), BigDecimal("10.00"),
                date = LocalDate.parse("2026-02-16")))
        }
        val other = identity.newMember("other")
        tx(other, account(other, "Theirs", "UAH"), null, 777700, "2026-02-12")
    }

    private fun totals(node: JsonNode) = node.associate { it["currency"].asText() to it["amount"].asText() }

    @Test
    fun `summary by category keeps currencies apart and leaves out transfers, tombstones and other households`() {
        seedSpending()
        val r = json(call(summarize, mapOf("from" to "2026-01-01", "to" to "2026-12-31")))
        assertEquals(listOf("Fun", "Food", "Uncategorised"), r["groups"].map { it["key"]["name"].asText() })
        assertEquals(mapOf("UAH" to "150.00", "USD" to "20.00"), totals(r["groups"][1]["totals"]))
        assertEquals(food.uuid, r["groups"][1]["key"]["uuid"].asText())
        assertEquals(mapOf("UAH" to "7.00"), totals(r["groups"][2]["totals"]))
        assertNull(r["groups"][2]["key"]["uuid"])
        assertEquals(mapOf("UAH" to "457.00", "USD" to "20.00"), totals(r["totals"]))
    }

    @Test
    fun `summary by account and by month, and for income`() {
        seedSpending()
        val byAccount = json(call(summarize, mapOf("from" to "2026-01-01", "to" to "2026-12-31", "groupBy" to "ACCOUNT")))
        assertEquals(listOf("Card", "Cash"), byAccount["groups"].map { it["key"]["name"].asText() })
        assertEquals(mapOf("UAH" to "457.00"), totals(byAccount["groups"][0]["totals"]))

        val byMonth = json(call(summarize, mapOf("from" to "2026-01-01", "to" to "2026-12-31", "groupBy" to "MONTH")))
        assertEquals(listOf("2026-01", "2026-02"), byMonth["groups"].map { it["key"].asText() })
        assertEquals(mapOf("UAH" to "100.00"), totals(byMonth["groups"][0]["totals"]))
        assertEquals(mapOf("UAH" to "357.00", "USD" to "20.00"), totals(byMonth["groups"][1]["totals"]))

        val income = json(call(summarize, mapOf("from" to "2026-01-01", "to" to "2026-12-31", "type" to "INCOME")))
        assertEquals(mapOf("UAH" to "888.00"), totals(income["totals"]))
    }

    @Test
    fun `summary range is inclusive of its last day`() {
        tx(me, card, food, 100, "2026-02-10")
        val r = json(call(summarize, mapOf("from" to "2026-02-10", "to" to "2026-02-10")))
        assertEquals(mapOf("UAH" to "1.00"), totals(r["totals"]))
    }

    @Test
    fun `summary rejects bad ranges and dates`() {
        assertEquals("\"from\" must not be after \"to\"",
            errorText(call(summarize, mapOf("from" to "2026-02-02", "to" to "2026-02-01"))))
        assertEquals("The range must not exceed 5 years",
            errorText(call(summarize, mapOf("from" to "2020-01-01", "to" to "2025-01-02"))))
        assertEquals("\"from\" must be a date like 2026-03-31",
            errorText(call(summarize, mapOf("from" to "01/02/2026", "to" to "2026-02-01"))))
        assertEquals("\"to\" is required (YYYY-MM-DD)", errorText(call(summarize, mapOf("from" to "2026-02-01"))))
    }

    // ---- over HTTP -------------------------------------------------------------------

    private fun rpc(token: String, body: String, session: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/mcp"))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        session?.let { request.header("Mcp-Session-Id", it) }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun result(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        val sse = response.headers().firstValue("Content-Type").orElse("").contains("text/event-stream")
        val rpc = if (sse) {
            response.body().lines().filter { it.startsWith("data:") }
                .map { mapper.readTree(it.removePrefix("data:").trim()) }.first { it.has("id") }
        } else mapper.readTree(response.body())
        assertFalse(rpc.has("error"), "JSON-RPC error: $rpc")
        return rpc["result"]
    }

    @Test
    fun `both tools answer over HTTP`() {
        tx(me, card, food, 4200, "2026-03-01", note = "Over the wire")
        identity.signIn(me)
        val token = try { tokens.createPersonal("Claude", null).token } finally { SecurityContextHolder.clearContext() }

        val init = rpc(token, """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
            "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""")
        val session = init.headers().firstValue("Mcp-Session-Id").orElse(null)
        assertNotNull(session)
        result(init)
        rpc(token, """{"jsonrpc":"2.0","method":"notifications/initialized"}""", session)

        val searched = result(rpc(token, """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search_transactions",
            "arguments":{"text":"wire"}}}""", session))
        assertFalse(searched["isError"]?.asBoolean() ?: false, searched.toString())
        assertEquals("42.00", mapper.readTree(searched["content"][0]["text"].asText())["transactions"][0]["amount"].asText())
        assertNotNull(searched["structuredContent"]["transactions"])

        val summed = result(rpc(token, """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"summarize_spending",
            "arguments":{"from":"2026-03-01","to":"2026-03-31"}}}""", session))
        assertFalse(summed["isError"]?.asBoolean() ?: false, summed.toString())
        assertEquals("42.00", summed["structuredContent"]["totals"][0]["amount"].asText())
    }
}
