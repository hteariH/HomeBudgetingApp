package com.personalmoney.core.data

import com.personalmoney.core.domain.model.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** Recurring charges, wherever they live. See [AccountRepository] for the guarantees. */
interface SubscriptionRepository {

    fun observeAll(): Flow<List<Subscription>>

    /** Only the ones still running, so a screen does not have to filter cancelled rows. */
    fun observeActive(): Flow<List<Subscription>>

    suspend fun findById(id: Long): Subscription?

    /**
     * Subscriptions whose next charge has come round on or before [date].
     *
     * Android answers this locally, because the worker that posts the charges runs on the
     * device and must work with no network. The server posts its own on a nightly job, so
     * the web implementation has nothing to answer and returns nothing.
     */
    suspend fun findDueAsOf(date: LocalDate): List<Subscription>

    /** Returns the new subscription's local id. */
    suspend fun create(subscription: Subscription): Long

    suspend fun update(subscription: Subscription)

    suspend fun setActive(id: Long, active: Boolean)

    suspend fun setNextChargeDate(id: Long, date: LocalDate)

    suspend fun delete(id: Long)

    suspend fun refresh() {}
}
