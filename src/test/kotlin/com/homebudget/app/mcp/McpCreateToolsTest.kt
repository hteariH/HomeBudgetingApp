package com.homebudget.app.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.Account
import com.homebudget.app.domain.AccountType
import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.mcp.tools.CreateTransactionTool
import com.homebudget.app.mcp.tools.CreateTransferTool
import com.homebudget.app.receipt.ReceiptRepository
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.SyncService
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** `create_transaction` and `create_transfer`: what they write, where it shows, and what they refuse. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:mcp-create-tools;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=true"
])
class McpCreateToolsTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var accounts: AccountRepository
    @Autowired private lateinit var categories: CategoryRepository
    @Autowired private lateinit var currencies: CurrencyRepository
    @Autowired private lateinit var transactions: TransactionRepository
    @Autowired private lateinit var receipts: ReceiptRepository
    @Autowired private lateinit var syncService: SyncService
    @Autowired private lateinit var createTransaction: CreateTransactionTool
    @Autowired private lateinit var createTransfer: CreateTransferTool
    @Autowired private lateinit var mapper: ObjectMapper

    private lateinit var me: UserPrincipal
    private lateinit var card: Account // UAH
    private lateinit var savings: Account // UAH
    private lateinit var cash: Account // USD
    private lateinit var food: Category
    private lateinit var salary: Category

    @BeforeEach
    fun setUp() {
        me = identity.newMember("writer")
        card = account(me, "Card", "UAH")
        savings = account(me, "Savings", "UAH")
        cash = account(me, "Cash", "USD")
        food = categories.save(Category(name = "Food", type = CategoryType.EXPENSE, household = me.household))
        salary = categories.save(Category(name = "Salary", type = CategoryType.INCOME, household = me.household))
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun account(owner: UserPrincipal, name: String, currency: String, archived: Boolean = false) = accounts.save(
        Account(name = name, type = AccountType.CHECKING, household = owner.household,
            currency = currencies.findByCode(currency)!!, initialBalanceMinor = 0, archived = archived)
    )

    private fun call(tool: McpTool, args: Map<String, Any?>, as_: UserPrincipal = me): CallToolResult =
        McpServerConfig.invoke(tool, as_, args)

    private fun json(result: CallToolResult): JsonNode {
        assertFalse(result.isError == true, "tool error: ${result.content()}")
        return mapper.readTree((result.content()[0] as TextContent).text())
    }

    private fun errorText(result: CallToolResult): String {
        assertTrue(result.isError == true, "expected a tool error, got ${result.content()}")
        return (result.content()[0] as TextContent).text()
    }

    private fun asUser(p: UserPrincipal) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

    private fun pulled(uuid: String) = McpPrincipalScope.runAs(me) {
        syncService.pull(Instant.EPOCH, 500).changes.transactions.filter { it.uuid == uuid }
    }

    // ---- create_transaction ------------------------------------------------------------

    @Test
    fun `an expense is created as the token owner, marked MCP, and shows through REST and sync`() {
        val out = json(call(createTransaction, mapOf(
            "account" to "card", "type" to "EXPENSE", "amount" to "125.50", "date" to "2026-10-01",
            "category" to "Food", "note" to "Lunch"
        )))
        assertEquals("125.50", out["amount"].asText())
        assertEquals("UAH", out["currency"].asText())
        assertEquals("2026-10-01", out["date"].asText())
        assertEquals("EXPENSE", out["type"].asText())
        assertEquals(card.uuid, out["account"]["uuid"].asText())
        assertEquals("Food", out["category"]["name"].asText())
        assertEquals("Lunch", out["note"].asText())
        assertEquals("writer Test", out["author"].asText())
        assertEquals("MCP", out["source"].asText())
        assertNull(out["receipt"])

        val saved = transactions.findByUuidAndHousehold(out["uuid"].asText(), me.household)!!
        assertEquals(me.user.id, saved.createdBy?.id)
        mvc.perform(get("/api/transactions/${saved.id}").with(asUser(me)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source").value("MCP"))
            .andExpect(jsonPath("$.amount").value(125.50))

        val synced = pulled(saved.uuid).single()
        assertEquals("MCP", synced.source)
        assertEquals(me.user.id, synced.createdByUserId)
    }

    @Test
    fun `income takes an income category and the date defaults to today in UTC`() {
        val out = json(call(createTransaction, mapOf(
            "account" to card.uuid, "type" to "INCOME", "amount" to "1000", "category" to salary.uuid
        )))
        assertEquals("1000.00", out["amount"].asText())
        assertEquals("INCOME", out["type"].asText())
        assertEquals(LocalDate.now(ZoneOffset.UTC).toString(), out["date"].asText())
    }

    @Test
    fun `a receipt is saved READY without a photo, in minor units, with the items' sum as its total`() {
        val out = json(call(createTransaction, mapOf(
            "account" to "Card", "type" to "EXPENSE", "amount" to "45.00", "date" to "2026-10-02",
            "receipt" to mapOf(
                "merchant" to "Silpo",
                "items" to listOf(
                    mapOf("name" to "Milk", "quantity" to "2", "price" to "30.00"),
                    mapOf("name" to "Bread", "price" to "15")
                )
            )
        )))
        assertEquals("Silpo", out["receipt"]["merchant"].asText())
        assertEquals("45.00", out["receipt"]["total"].asText())
        assertEquals(listOf("Milk", "Bread"), out["receipt"]["items"].map { it["name"].asText() })

        val saved = transactions.findByUuidAndHousehold(out["uuid"].asText(), me.household)!!
        val receiptUuid = saved.receiptUuid!!
        val receipt = receipts.findByUuidAndHouseholdId(receiptUuid, me.household.id)!!
        assertNull(receipt.imagePath)
        assertEquals("UAH", receipt.currencyCode)
        assertEquals(LocalDate.parse("2026-10-02"), receipt.receiptDate)
        assertEquals(4500L, receipt.totalMinor)
        assertEquals(me.user.id, receipt.createdBy?.id)

        mvc.perform(get("/api/receipts/$receiptUuid").with(asUser(me)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.hasImage").value(false))
            .andExpect(jsonPath("$.merchant").value("Silpo"))
            .andExpect(jsonPath("$.currency").value("UAH"))
            .andExpect(jsonPath("$.total").value("45.00"))
            .andExpect(jsonPath("$.totalMismatch").value(false))
            .andExpect(jsonPath("$.items[0].name").value("Milk"))
            .andExpect(jsonPath("$.items[0].quantity").value("2"))
            .andExpect(jsonPath("$.items[0].price").value("30.00"))
            .andExpect(jsonPath("$.items[1].price").value("15.00"))
        mvc.perform(get("/api/receipts/$receiptUuid/image").with(asUser(me)))
            .andExpect(status().isNotFound)
        assertEquals(receiptUuid, pulled(saved.uuid).single().receiptUuid)
    }

    @Test
    fun `a receipt whose items do not add up to its total is flagged`() {
        val out = json(call(createTransaction, mapOf(
            "account" to "Card", "type" to "EXPENSE", "amount" to "50.00",
            "receipt" to mapOf("total" to "50.00", "items" to listOf(mapOf("name" to "Milk", "price" to "30.00")))
        )))
        assertEquals("50.00", out["receipt"]["total"].asText())
        val receiptUuid = transactions.findByUuidAndHousehold(out["uuid"].asText(), me.household)!!.receiptUuid!!
        mvc.perform(get("/api/receipts/$receiptUuid").with(asUser(me)))
            .andExpect(jsonPath("$.totalMismatch").value(true))
            .andExpect(jsonPath("$.hasImage").value(false))
    }

    @Test
    fun `create_transaction refuses bad input with exact messages and writes nothing`() {
        account(me, "Old card", "UAH", archived = true)
        val other = identity.newMember("other")
        val theirs = account(other, "Theirs", "UAH")
        val before = transactions.count()
        val receiptsBefore = receipts.count()
        fun err(args: Map<String, Any?>) = errorText(call(createTransaction, args))
        val base = mapOf("account" to "Card", "type" to "EXPENSE", "amount" to "10.00")

        assertEquals("Category \"Salary\" is an income category", err(base + ("category" to "Salary")))
        assertEquals("Category \"Food\" is an expense category", err(base + ("type" to "INCOME") + ("category" to "food")))
        assertEquals("Account \"Old card\" is archived", err(base + ("account" to "old card")))
        assertEquals("Account \"${theirs.uuid}\" not found — list_accounts shows the accounts", err(base + ("account" to theirs.uuid)))
        assertEquals("\"amount\" has more decimal places than UAH allows (2)", err(base + ("amount" to "10.005")))
        assertEquals("\"amount\" must be greater than zero", err(base + ("amount" to "0")))
        assertEquals("\"amount\" must be greater than zero", err(base + ("amount" to "-5")))
        assertEquals("\"amount\" is required", err(base - "amount"))
        assertEquals("\"type\" must be INCOME or EXPENSE", err(base + ("type" to "TRANSFER")))
        assertEquals("\"note\" must be at most 500 characters", err(base + ("note" to "x".repeat(501))))
        assertEquals("\"receipt.merchant\" must be at most 255 characters",
            err(base + ("receipt" to mapOf("merchant" to "m".repeat(256), "items" to emptyList<Any>()))))
        assertEquals("\"receipt.items\" must have at most 200 items",
            err(base + ("receipt" to mapOf("items" to List(201) { mapOf("name" to "x", "price" to "0.01") }))))
        assertEquals("\"receipt.items[0].name\" must be 1 to 255 characters",
            err(base + ("receipt" to mapOf("items" to listOf(mapOf("name" to " ", "price" to "1"))))))
        assertEquals("\"receipt.items[0].price\" has more decimal places than UAH allows (2)",
            err(base + ("receipt" to mapOf("items" to listOf(mapOf("name" to "Milk", "price" to "1.001"))))))
        assertEquals("\"receipt.items[0].price\" is required",
            err(base + ("receipt" to mapOf("items" to listOf(mapOf("name" to "Milk"))))))

        assertEquals(before, transactions.count())
        assertEquals(receiptsBefore, receipts.count())
    }

    // ---- create_transfer ---------------------------------------------------------------

    @Test
    fun `a same-currency transfer is created, marked MCP on both legs, and shows through sync`() {
        val out = json(call(createTransfer, mapOf(
            "fromAccount" to "Card", "toAccount" to "Savings", "amount" to "200", "date" to "2026-10-03", "note" to "Stash"
        )))
        assertEquals("TRANSFER", out["type"].asText())
        assertEquals("Card", out["account"]["name"].asText())
        assertEquals("Savings", out["toAccount"]["name"].asText())
        assertEquals("200.00", out["amount"].asText())
        assertEquals("200.00", out["toAmount"].asText())
        assertEquals("UAH", out["toCurrency"].asText())
        assertEquals("Stash", out["note"].asText())
        assertEquals("MCP", out["source"].asText())
        assertEquals("writer Test", out["author"].asText())

        val debit = transactions.findByUuidAndHousehold(out["uuid"].asText(), me.household)!!
        val legs = transactions.findAllByTransferGroupIdAndHousehold(debit.transferGroupId!!, me.household)
        assertEquals(2, legs.size)
        legs.forEach { assertEquals("MCP", it.source); assertEquals(me.user.id, it.createdBy?.id) }
        val synced = McpPrincipalScope.runAs(me) { syncService.pull(Instant.EPOCH, 500).changes.transactions }
            .filter { it.transferGroupId == debit.transferGroupId }
        assertEquals(listOf("MCP", "MCP"), synced.map { it.source })
    }

    @Test
    fun `a cross-currency transfer takes the amount that arrives`() {
        val out = json(call(createTransfer, mapOf(
            "fromAccount" to card.uuid, "toAccount" to cash.uuid, "amount" to "410.00", "toAmount" to "10"
        )))
        assertEquals("410.00", out["amount"].asText())
        assertEquals("UAH", out["currency"].asText())
        assertEquals("10.00", out["toAmount"].asText())
        assertEquals("USD", out["toCurrency"].asText())
        assertEquals(LocalDate.now(ZoneOffset.UTC).toString(), out["date"].asText())
    }

    @Test
    fun `create_transfer refuses bad input with exact messages`() {
        account(me, "Old card", "UAH", archived = true)
        val before = transactions.count()
        fun err(args: Map<String, Any?>) = errorText(call(createTransfer, args))

        assertEquals("fromAccount and toAccount must be different accounts",
            err(mapOf("fromAccount" to "Card", "toAccount" to card.uuid, "amount" to "1")))
        assertEquals("The accounts use UAH and USD — pass toAmount, the amount that arrives",
            err(mapOf("fromAccount" to "Card", "toAccount" to "Cash", "amount" to "1")))
        assertEquals("Both accounts use UAH — leave out toAmount or make it equal to amount",
            err(mapOf("fromAccount" to "Card", "toAccount" to "Savings", "amount" to "1", "toAmount" to "2")))
        assertEquals("Account \"Old card\" is archived",
            err(mapOf("fromAccount" to "Card", "toAccount" to "Old card", "amount" to "1")))
        assertEquals("\"toAmount\" has more decimal places than USD allows (2)",
            err(mapOf("fromAccount" to "Card", "toAccount" to "Cash", "amount" to "1", "toAmount" to "0.001")))
        assertEquals("\"amount\" must be greater than zero",
            err(mapOf("fromAccount" to "Card", "toAccount" to "Savings", "amount" to "0")))
        assertEquals("\"note\" must be at most 500 characters",
            err(mapOf("fromAccount" to "Card", "toAccount" to "Savings", "amount" to "1", "note" to "x".repeat(501))))
        assertEquals(before, transactions.count())
    }
}
