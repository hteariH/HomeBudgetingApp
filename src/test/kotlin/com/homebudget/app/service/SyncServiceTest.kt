package com.homebudget.app.service

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.*
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * The sync protocol's load-bearing behaviours: uuid-keyed graphs written in one push,
 * tombstones that replicate, conflicts decided by version, and - most importantly -
 * references that cannot reach across households.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
class SyncServiceTest {

    @Autowired private lateinit var syncService: SyncService
    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var transactionService: TransactionService
    @Autowired private lateinit var transferService: TransferService
    @Autowired private lateinit var jdbc: org.springframework.jdbc.core.JdbcTemplate
    @Autowired private lateinit var identity: TestIdentity

    private lateinit var alice: UserPrincipal
    private lateinit var bob: UserPrincipal

    @BeforeEach
    fun setUp() {
        alice = identity.newMember("alice")
        bob = identity.newMember("bob")
        identity.signIn(alice)
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun uuid() = UUID.randomUUID().toString()

    @Test
    fun `a push writes an account and a transaction that references it by uuid`() {
        // The offline case: neither row has a server id, and the transaction points at
        // an account the server has never seen.
        val accountUuid = uuid()
        val txUuid = uuid()

        val response = syncService.push(
            SyncPushRequest(
                SyncPayload(
                    accounts = listOf(
                        SyncAccount(uuid = accountUuid, name = "Cash", currencyCode = "USD",
                            initialBalance = BigDecimal("100.00"))
                    ),
                    transactions = listOf(
                        SyncTransaction(
                            uuid = txUuid,
                            accountUuid = accountUuid,
                            amount = BigDecimal("25.00"),
                            currencyCode = "USD",
                            accountAmount = BigDecimal("25.00"),
                            type = TransactionType.EXPENSE,
                            occurredAt = Instant.parse("2026-04-01T10:00:00Z")
                        )
                    )
                )
            )
        )

        assertEquals(2, response.applied)
        assertTrue(response.rejected.isEmpty(), "unexpected rejections: ${response.rejected}")

        val account = accountService.findAll().single { it.uuid == accountUuid }
        assertEquals(7500L, accountService.balanceOf(account.id))
    }

    @Test
    fun `a pull returns everything after the cursor and nothing before it`() {
        val accountUuid = uuid()
        syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = accountUuid, name = "Cash", currencyCode = "USD")
            )))
        )

        val first = syncService.pull(Instant.EPOCH, 500)
        assertEquals(1, first.changes.accounts.size)

        // Nothing has changed since that cursor.
        val second = syncService.pull(first.serverTime, 500)
        assertTrue(second.changes.accounts.isEmpty(), "cursor did not advance")
    }

    @Test
    fun `a deletion reaches the other client as a tombstone`() {
        val accountUuid = uuid()
        syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = accountUuid, name = "Cash", currencyCode = "USD")
            )))
        )
        val afterCreate = syncService.pull(Instant.EPOCH, 500)
        val base = afterCreate.changes.accounts.single { it.uuid == accountUuid }.updatedAt

        syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = accountUuid, baseUpdatedAt = base, deleted = true,
                    name = "Cash", currencyCode = "USD")
            )))
        )

        // A hard delete would simply be absent here, and a client that had been offline
        // would push the row straight back.
        val changed = syncService.pull(afterCreate.serverTime, 500).changes.accounts
        assertEquals(1, changed.size)
        assertTrue(changed.single().deleted, "deletion must arrive as a tombstone")
    }

    @Test
    fun `an edit based on a superseded version is rejected as stale`() {
        val accountUuid = uuid()
        syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = accountUuid, name = "Cash", currencyCode = "USD")
            )))
        )
        val v1 = syncService.pull(Instant.EPOCH, 500).changes.accounts.single().updatedAt

        // Someone else (the web client, say) edits it.
        syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = accountUuid, baseUpdatedAt = v1, name = "Wallet", currencyCode = "USD")
            )))
        )

        // The phone, still holding v1, tries to write its own edit.
        val late = syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = accountUuid, baseUpdatedAt = v1, name = "Pocket", currencyCode = "USD")
            )))
        )

        assertEquals(0, late.applied)
        assertEquals(SyncRejectionReason.STALE, late.rejected.single().reason)
        assertEquals("Wallet", accountService.findAll().single { it.uuid == accountUuid }.name)
    }

    @Test
    fun `a record the server has never seen is accepted without a base version`() {
        val response = syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = uuid(), name = "New", currencyCode = "USD")
            )))
        )
        assertEquals(1, response.applied)
    }

    @Test
    fun `a client claiming a record is new when the server has it is rejected`() {
        // Guards against an overwrite that silently discards the server's version.
        val accountUuid = uuid()
        syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = accountUuid, name = "Cash", currencyCode = "USD")
            )))
        )
        val again = syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = accountUuid, baseUpdatedAt = null, name = "Other", currencyCode = "USD")
            )))
        )
        assertEquals(SyncRejectionReason.STALE, again.rejected.single().reason)
    }

    @Test
    fun `one household cannot write against another household's uuid`() {
        // The attack this closes: a uuid in a push body is a pointer, and an unchecked
        // pointer writes into somebody else's ledger.
        val aliceAccountUuid = uuid()
        syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = aliceAccountUuid, name = "Alice cash", currencyCode = "USD")
            )))
        )

        identity.signIn(bob)
        val response = syncService.push(
            SyncPushRequest(SyncPayload(transactions = listOf(
                SyncTransaction(
                    uuid = uuid(),
                    accountUuid = aliceAccountUuid,
                    amount = BigDecimal("999.00"),
                    currencyCode = "USD",
                    accountAmount = BigDecimal("999.00"),
                    type = TransactionType.EXPENSE,
                    occurredAt = Instant.parse("2026-04-01T10:00:00Z")
                )
            )))
        )

        assertEquals(0, response.applied)
        assertEquals(SyncRejectionReason.UNKNOWN_REFERENCE, response.rejected.single().reason)
    }

    @Test
    fun `a pull never leaks another household's rows`() {
        syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = uuid(), name = "Alice cash", currencyCode = "USD")
            )))
        )

        identity.signIn(bob)
        val bobsView = syncService.pull(Instant.EPOCH, 500)
        assertTrue(bobsView.changes.accounts.isEmpty(), "Bob can see Alice's accounts")
    }

    @Test
    fun `a category and its child survive a round trip in one push`() {
        val parentUuid = uuid()
        val childUuid = uuid()
        // Deliberately child-first in the payload: the server has to order them itself.
        val response = syncService.push(
            SyncPushRequest(SyncPayload(categories = listOf(
                SyncCategory(uuid = childUuid, name = "Groceries", type = CategoryType.EXPENSE,
                    parentUuid = parentUuid),
                SyncCategory(uuid = parentUuid, name = "Food", type = CategoryType.EXPENSE)
            )))
        )
        assertEquals(2, response.applied, "rejections: ${response.rejected}")

        val pulled = syncService.pull(Instant.EPOCH, 500).changes.categories
        val child = pulled.single { it.uuid == childUuid }
        assertEquals(parentUuid, child.parentUuid)
    }

    @Test
    fun `a transfer keeps both legs and its counterpart link across a round trip`() {
        val fromUuid = uuid()
        val toUuid = uuid()
        val groupId = uuid()

        syncService.push(
            SyncPushRequest(SyncPayload(
                accounts = listOf(
                    SyncAccount(uuid = fromUuid, name = "Checking", currencyCode = "USD",
                        initialBalance = BigDecimal("500.00")),
                    SyncAccount(uuid = toUuid, name = "Savings", currencyCode = "USD")
                ),
                transactions = listOf(
                    SyncTransaction(uuid = uuid(), accountUuid = fromUuid,
                        amount = BigDecimal("-120.00"), currencyCode = "USD",
                        accountAmount = BigDecimal("-120.00"), type = TransactionType.TRANSFER,
                        occurredAt = Instant.parse("2026-04-03T09:00:00Z"),
                        transferGroupId = groupId, transferCounterpartAccountUuid = toUuid),
                    SyncTransaction(uuid = uuid(), accountUuid = toUuid,
                        amount = BigDecimal("120.00"), currencyCode = "USD",
                        accountAmount = BigDecimal("120.00"), type = TransactionType.TRANSFER,
                        occurredAt = Instant.parse("2026-04-03T09:00:00Z"),
                        transferGroupId = groupId, transferCounterpartAccountUuid = fromUuid)
                )
            ))
        )

        val from = accountService.findAll().single { it.uuid == fromUuid }
        val to = accountService.findAll().single { it.uuid == toUuid }
        assertEquals(38000L, accountService.balanceOf(from.id))
        assertEquals(12000L, accountService.balanceOf(to.id))

        val legs = syncService.pull(Instant.EPOCH, 500).changes.transactions
            .filter { it.transferGroupId == groupId }
        assertEquals(2, legs.size)
        // The counterpart is stored as an id and has to come back out as a uuid.
        assertTrue(legs.all { it.transferCounterpartAccountUuid != null }) {
            "counterpart link lost in translation"
        }
    }

    @Test
    fun `an unknown currency is reported rather than silently dropped`() {
        val response = syncService.push(
            SyncPushRequest(SyncPayload(accounts = listOf(
                SyncAccount(uuid = uuid(), name = "Odd", currencyCode = "ZZZ")
            )))
        )
        assertEquals(0, response.applied)
        assertEquals(SyncRejectionReason.UNKNOWN_REFERENCE, response.rejected.single().reason)
        assertNotNull(response.rejected.single().message)
        assertFalse(response.rejected.single().message.isBlank())
    }

    @Test
    fun `a pulled transaction names its author`() {
        val accountUuid = uuid()
        val txUuid = uuid()
        syncService.push(
            SyncPushRequest(
                SyncPayload(
                    accounts = listOf(SyncAccount(uuid = accountUuid, name = "Cash", currencyCode = "USD")),
                    transactions = listOf(
                        SyncTransaction(
                            uuid = txUuid, accountUuid = accountUuid, amount = BigDecimal("5.00"),
                            currencyCode = "USD", accountAmount = BigDecimal("5.00"),
                            type = TransactionType.EXPENSE, occurredAt = Instant.parse("2026-04-01T10:00:00Z"),
                            // A client has no say in who the author is.
                            createdByUserId = bob.user.id
                        )
                    )
                )
            )
        )

        val pulled = syncService.pull(Instant.EPOCH, 500).changes.transactions.single()
        assertEquals(alice.user.id, pulled.createdByUserId)
    }

    @Test
    fun `a pull carries the source, and a push can never set or change it`() {
        val accountUuid = uuid()
        val pushedUuid = uuid()
        fun tx(uuid: String, source: String?, base: Instant? = null, amount: String = "5.00") = SyncTransaction(
            uuid = uuid, baseUpdatedAt = base, accountUuid = accountUuid, amount = BigDecimal(amount),
            currencyCode = "USD", accountAmount = BigDecimal(amount),
            type = TransactionType.EXPENSE, occurredAt = Instant.parse("2026-04-01T10:00:00Z"), source = source
        )
        syncService.push(SyncPushRequest(SyncPayload(
            accounts = listOf(SyncAccount(uuid = accountUuid, name = "Cash", currencyCode = "USD")),
            // A client cannot claim an assistant wrote its row.
            transactions = listOf(tx(pushedUuid, "MCP"))
        )))
        val account = accountService.findAll().single { it.uuid == accountUuid }
        val mcp = transactionService.create(
            CreateTransactionRequest(accountId = account.id, amount = BigDecimal("7.00"), type = TransactionType.EXPENSE,
                date = java.time.LocalDate.parse("2026-04-02")),
            source = "MCP"
        )

        val pulled = syncService.pull(Instant.EPOCH, 500).changes.transactions.associateBy { it.uuid }
        assertEquals("APP", pulled.getValue(pushedUuid).source)
        assertEquals("MCP", pulled.getValue(mcp.uuid).source)

        // Editing the assistant's row from a phone keeps it the assistant's.
        val edit = syncService.push(SyncPushRequest(SyncPayload(
            transactions = listOf(tx(mcp.uuid, "APP", base = pulled.getValue(mcp.uuid).updatedAt, amount = "8.00"))
        )))
        assertTrue(edit.rejected.isEmpty(), "unexpected rejections: ${edit.rejected}")
        val after = syncService.pull(Instant.EPOCH, 500).changes.transactions.single { it.uuid == mcp.uuid }
        assertEquals(BigDecimal("8.00"), after.amount)
        assertEquals("MCP", after.source)
    }

    @Test
    fun `entries created over REST are stamped after the household's latest sync stamp`() {
        val cashUuid = uuid()
        val cardUuid = uuid()
        syncService.push(SyncPushRequest(SyncPayload(accounts = listOf(
            SyncAccount(uuid = cashUuid, name = "Cash", currencyCode = "USD"),
            SyncAccount(uuid = cardUuid, name = "Card", currencyCode = "USD")
        ))))
        // A device has pulled up to the push's stamp. Put that stamp ahead of the wall clock, as a
        // same-millisecond write or a stamp bumped by SyncClock would be: a create stamped with the
        // bare clock would land at or before it, and that device would never see it.
        val pushStamp = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        jdbc.update("UPDATE accounts SET updated_at = ? WHERE uuid IN (?, ?)",
            java.sql.Timestamp.from(pushStamp), cashUuid, cardUuid)
        val cash = accountService.findAll().single { it.uuid == cashUuid }
        val card = accountService.findAll().single { it.uuid == cardUuid }

        val expense = transactionService.create(CreateTransactionRequest(accountId = cash.id, amount = BigDecimal("3.00"),
            type = TransactionType.EXPENSE, date = java.time.LocalDate.parse("2026-04-03")))
        val debit = transferService.create(CreateTransferRequest(fromAccountId = cash.id, toAccountId = card.id,
            fromAmount = BigDecimal("1.00"), toAmount = BigDecimal("1.00"), date = java.time.LocalDate.parse("2026-04-03")))

        val pulled = syncService.pull(pushStamp, 500).changes.transactions
        assertTrue(pulled.any { it.uuid == expense.uuid }, "the REST expense is missing from the pull")
        assertEquals(2, pulled.count { it.transferGroupId == debit.transferGroupId }, "a transfer leg is missing from the pull")
        pulled.forEach { assertTrue(it.updatedAt.isAfter(pushStamp)) }
    }
}
