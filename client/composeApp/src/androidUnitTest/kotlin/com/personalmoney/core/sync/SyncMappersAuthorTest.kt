package com.personalmoney.core.sync

import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.network.SyncTransactionDto
import com.personalmoney.core.network.createJson
import com.personalmoney.core.sync.SyncMappers.toDto
import com.personalmoney.core.sync.SyncMappers.toEntity
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.time.Instant

class SyncMappersAuthorTest {

    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Test
    fun `a pull brings the author in`() {
        val dto = SyncTransactionDto(
            uuid = "tx-1", accountUuid = "acc-1", amount = "-5.00", currencyCode = "USD",
            accountAmount = "-5.00", type = "EXPENSE", occurredAt = at.toString(), createdByUserId = 7L,
        )

        val entity = dto.toEntity(0, 1, null, null, null, 2)

        assertEquals(7L, entity.createdByUserId)
    }

    @Test
    fun `a push never sends the author`() {
        val entity = TransactionEntity(
            type = TransactionType.EXPENSE, accountId = 1, categoryId = null, amountMinor = -500,
            currencyCode = "USD", accountAmountMinor = -500, occurredAt = at, createdAt = at,
            createdByUserId = 7L,
        )

        val dto = entity.toDto("acc-1", null, null, null, 2)
        val json = createJson().encodeToString(dto)

        assertFalse("author must not be on the wire: $json", json.contains("createdByUserId"))
    }
}
