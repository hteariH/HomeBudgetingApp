package com.personalmoney.core.sync

import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.network.SyncTransactionDto
import com.personalmoney.core.network.createJson
import com.personalmoney.core.sync.SyncMappers.toDto
import com.personalmoney.core.sync.SyncMappers.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.time.Instant

/** Where an entry came from is the server's to say; the phone reads it and never sends it. */
class SyncMappersSourceTest {

    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun dto(source: String?) = SyncTransactionDto(
        uuid = "tx-1", accountUuid = "acc-1", amount = "-5.00", currencyCode = "USD",
        accountAmount = "-5.00", type = "EXPENSE", occurredAt = at.toString(), source = source,
    )

    @Test
    fun `a pull brings the source in`() {
        assertEquals("MCP", dto("MCP").toEntity(0, 1, null, null, null, 2).source)
    }

    @Test
    fun `a pull without a source is an app entry`() {
        assertEquals("APP", dto(null).toEntity(0, 1, null, null, null, 2).source)
    }

    @Test
    fun `a push never sends the source`() {
        val entity = TransactionEntity(
            type = TransactionType.EXPENSE, accountId = 1, categoryId = null, amountMinor = -500,
            currencyCode = "USD", accountAmountMinor = -500, occurredAt = at, createdAt = at,
            source = "MCP",
        )

        val json = createJson().encodeToString(entity.toDto("acc-1", null, null, null, 2))

        assertFalse("source must not be on the wire: $json", json.contains("source"))
    }
}
