package com.homebudget.app.repository

import com.homebudget.app.domain.Subscription
import com.homebudget.app.domain.Household
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
interface SubscriptionRepository : JpaRepository<Subscription, Long> {
    fun findAllByHousehold(household: Household): List<Subscription>
    fun findByIdAndHousehold(id: Long, household: Household): Subscription?
    fun findByUuidAndHousehold(uuid: String, household: Household): Subscription?
    fun countByCategoryId(categoryId: Long): Long

    /** Soft deletes cascade by hand; see [TransactionRepository.tombstoneByAccount]. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Subscription s SET s.deleted = true, s.updatedAt = :now WHERE s.account.id = :accountId AND s.deleted = false")
    fun tombstoneByAccount(accountId: Long, now: Instant): Int

    /** Active subscriptions whose next charge has come due, across all users. */
    @Query(
        """
        SELECT s FROM Subscription s
        WHERE s.active = true
          AND s.nextChargeDate <= :onOrBefore
          AND (s.endDate IS NULL OR s.nextChargeDate <= s.endDate)
        """
    )
    fun findDue(onOrBefore: LocalDate): List<Subscription>

    // ── Sync access ─────────────────────────────────────────────────────────
    // Native SQL on purpose: @SQLRestriction hides tombstones from every JPQL query,
    // and sync is the one place that must see them - a deletion the client never
    // learns about would be pushed straight back.

    @Query(
        value = "SELECT * FROM subscriptions WHERE household_id = :householdId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(householdId: Long, since: Instant, limit: Int): List<Subscription>

    @Query(value = "SELECT * FROM subscriptions WHERE uuid = :uuid AND household_id = :householdId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, householdId: Long): Subscription?
}
