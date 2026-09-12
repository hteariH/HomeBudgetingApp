package com.homebudget.app.bank

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.*
import com.homebudget.app.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class MonobankWebhookTest {

    @Autowired private lateinit var webhookController: MonobankWebhookController
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var currencyRepository: CurrencyRepository
    @Autowired private lateinit var accountRepository: AccountRepository
    @Autowired private lateinit var categoryRepository: CategoryRepository
    @Autowired private lateinit var transactionRepository: TransactionRepository
    @Autowired private lateinit var bankConnectionRepository: BankConnectionRepository
    @Autowired private lateinit var objectMapper: ObjectMapper

    private lateinit var user: User
    private lateinit var account: Account
    private lateinit var category: Category
    private lateinit var connection: BankConnection
    private lateinit var webhookToken: String

    @BeforeEach
    fun setUp() {
        webhookToken = "mono_test_token_${UUID.randomUUID()}"
        val uah = currencyRepository.findByCode("UAH") ?: currencyRepository.findByCode("USD")!!
        user = userRepository.save(
            User(
                email = "mono-test-${UUID.randomUUID()}@example.com",
                passwordHash = "x",
                firstName = "Mono",
                lastName = "Tester",
                role = Role.USER,
                baseCurrency = uah
            )
        )

        val credsJson = objectMapper.writeValueAsString(mapOf("token" to webhookToken))
        connection = bankConnectionRepository.save(
            BankConnection(
                user = user,
                provider = "MONOBANK",
                credentials = credsJson,
                label = "Monobank"
            )
        )

        account = accountRepository.save(
            Account(
                user = user,
                name = "Monobank Black",
                currency = uah,
                bankConnection = connection,
                externalAccountId = "mono_acc_001",
                initialBalanceMinor = 50000L
            )
        )

        category = categoryRepository.save(
            Category(
                user = user,
                name = "Groceries",
                type = CategoryType.EXPENSE,
                mccCodes = mutableSetOf(5411) // Supermarkets
            )
        )
    }

    @Test
    fun `ping webhook payload returns 200 ok`() {
        val payload = """{"type": "Webhook"}"""
        val response = webhookController.handleWebhook(webhookToken, payload)
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `valid statement item creates transaction with monobank prefix and matched category`() {
        val payload = """
            {
                "type": "StatementItem",
                "data": {
                    "account": "mono_acc_001",
                    "statementItem": {
                        "id": "tx_abc_123",
                        "time": 1716984000,
                        "description": "Silpo Supermarket",
                        "mcc": 5411,
                        "originalMcc": 5411,
                        "amount": -24550,
                        "operationAmount": -24550,
                        "currencyCode": 980,
                        "commissionRate": 0,
                        "cashbackAmount": 0,
                        "balance": 25450,
                        "hold": false
                    }
                }
            }
        """.trimIndent()

        val response = webhookController.handleWebhook(webhookToken, payload)
        assertEquals(HttpStatus.OK, response.statusCode)

        val txId = (response.body?.get("transactionId") as Number).toLong()
        val tx = transactionRepository.findById(txId).orElse(null)
        assertNotNull(tx, "Transaction should be created")
        assertEquals(24550L, tx!!.amountMinor)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Silpo Supermarket", tx.description)
        assertEquals("monobank:tx_abc_123", tx.externalId)
        assertNotNull(tx.category)
        assertEquals(category.id, tx.category?.id)
    }

    @Test
    fun `duplicate statement item is skipped idempotently`() {
        val payload = """
            {
                "data": {
                    "account": "mono_acc_001",
                    "statementItem": {
                        "id": "tx_dup_999",
                        "time": 1716984000,
                        "description": "Coffee Shop",
                        "mcc": 5814,
                        "amount": -6000
                    }
                }
            }
        """.trimIndent()

        val response1 = webhookController.handleWebhook(webhookToken, payload)
        assertEquals(HttpStatus.OK, response1.statusCode)

        val txCount1 = transactionRepository.count()

        // Second submission of the exact same statement item
        val response2 = webhookController.handleWebhook(webhookToken, payload)
        assertEquals(HttpStatus.OK, response2.statusCode)
        assertTrue(response2.body?.get("skipped") == "duplicate")

        val txCount2 = transactionRepository.count()
        assertEquals(txCount1, txCount2, "Duplicate transaction must not be inserted")
    }

    @Test
    fun `unknown token returns 401 unauthorized`() {
        val payload = """{"data": {"statementItem": {"id": "123"}}}"""
        val response = webhookController.handleWebhook("wrong_token", payload)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
