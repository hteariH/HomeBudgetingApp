package com.personalmoney.core.data.repository

import com.personalmoney.core.data.SubscriptionRepository as SharedSubscriptionRepository
import com.personalmoney.core.database.entity.SubscriptionEntity
import com.personalmoney.core.domain.model.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** The shared subscription contract, backed by Room. */
class RoomSubscriptionRepository(
    private val delegate: SubscriptionRepository,
) : SharedSubscriptionRepository {

    override fun observeAll(): Flow<List<Subscription>> =
        delegate.observeAll().map { rows -> rows.map { it.toSubscription() } }

    override fun observeActive(): Flow<List<Subscription>> =
        delegate.observeActive().map { rows -> rows.map { it.toSubscription() } }

    override suspend fun findById(id: Long): Subscription? =
        delegate.findById(id)?.toSubscription()

    override suspend fun findDueAsOf(date: LocalDate): List<Subscription> =
        delegate.findDueAsOf(date).map { it.toSubscription() }

    override suspend fun create(subscription: Subscription): Long =
        delegate.create(subscription.toEntity())

    override suspend fun update(subscription: Subscription) {
        // Room writes the whole row, so the sync columns have to be carried over from the
        // stored one - a fresh uuid would make the server treat the edit as a new
        // subscription, and a zeroed updatedAt would lose the optimistic-concurrency base.
        val stored = delegate.findById(subscription.id)
        delegate.update(
            subscription.toEntity(
                uuid = stored?.uuid,
                updatedAt = stored?.updatedAt ?: 0L,
                deleted = stored?.deleted ?: false,
            )
        )
    }

    override suspend fun setActive(id: Long, active: Boolean) = delegate.setActive(id, active)

    override suspend fun setNextChargeDate(id: Long, date: LocalDate) =
        delegate.setNextChargeDate(id, date)

    override suspend fun delete(id: Long) = delegate.delete(id)
}

private fun SubscriptionEntity.toSubscription() = Subscription(
    id = id,
    uuid = uuid,
    name = name,
    accountId = accountId,
    categoryId = categoryId,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    frequency = frequency,
    interval = interval,
    nextChargeDate = nextChargeDate,
    startDate = startDate,
    endDate = endDate,
    active = active,
    reminderMinutesBefore = reminderMinutesBefore,
    icon = icon,
    color = color,
    note = note,
)

private fun Subscription.toEntity(
    uuid: String? = null,
    updatedAt: Long = 0L,
    deleted: Boolean = false,
): SubscriptionEntity {
    val base = SubscriptionEntity(
        id = id,
        name = name,
        accountId = accountId,
        categoryId = categoryId,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        frequency = frequency,
        interval = interval,
        nextChargeDate = nextChargeDate,
        startDate = startDate,
        endDate = endDate,
        active = active,
        reminderMinutesBefore = reminderMinutesBefore,
        icon = icon,
        color = color,
        note = note,
        updatedAt = updatedAt,
        deleted = deleted,
    )
    // Only override the freshly minted uuid when there is a stored one to preserve.
    return if (uuid != null) base.copy(uuid = uuid) else base
}
