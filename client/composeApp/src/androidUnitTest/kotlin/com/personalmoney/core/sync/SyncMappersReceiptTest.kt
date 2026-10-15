package com.personalmoney.core.sync

import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.network.SyncTransactionDto
import com.personalmoney.core.sync.SyncMappers.toDto
import com.personalmoney.core.sync.SyncMappers.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class SyncMappersReceiptTest {

    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Test
    fun `a pull brings the receipt in`() {
        val dto = SyncTransactionDto(
            uuid = "tx-1", accountUuid = "acc-1", amount = "-5.00", currencyCode = "USD",
            accountAmount = "-5.00", type = "EXPENSE", occurredAt = at.toString(), receiptUuid = "r-1",
        )

        val entity = dto.toEntity(0, 1, null, null, null, 2)

        assertEquals("r-1", entity.receiptUuid)
    }

    @Test
    fun `a push sends the receipt the entry was scanned from`() {
        val entity = TransactionEntity(
            type = TransactionType.EXPENSE, accountId = 1, categoryId = null, amountMinor = -500,
            currencyCode = "USD", accountAmountMinor = -500, occurredAt = at, createdAt = at,
            receiptUuid = "r-1",
        )

        val dto = entity.toDto("acc-1", null, null, null, 2)

        assertEquals("r-1", dto.receiptUuid)
    }
}
