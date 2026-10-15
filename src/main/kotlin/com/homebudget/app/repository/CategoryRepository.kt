package com.homebudget.app.repository

import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.Household
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface CategoryRepository : JpaRepository<Category, Long> {
    fun findAllByHousehold(household: Household): List<Category>
    fun findByHouseholdAndType(household: Household, type: CategoryType): List<Category>
    fun existsByNameAndTypeAndHousehold(name: String, type: CategoryType, household: Household): Boolean
    fun findByIdAndHousehold(id: Long, household: Household): Category?
    fun findByUuidAndHousehold(uuid: String, household: Household): Category?

    /** Categories a previous import created, keyed by the source's own id. */
    fun findAllByHouseholdAndExternalIdIsNotNull(household: Household): List<Category>

    @Query("SELECT DISTINCT c FROM Category c JOIN c.mccCodes m WHERE c.household = :household AND m = :mcc and c.type = :type")
    fun findByHouseholdAndMccCodeAndType(household: Household, mcc: Int, type: CategoryType): List<Category>

    // ── Sync access ─────────────────────────────────────────────────────────
    // Native SQL on purpose: @SQLRestriction hides tombstones from every JPQL query,
    // and sync is the one place that must see them - a deletion the client never
    // learns about would be pushed straight back.

    @Query(
        value = "SELECT * FROM categories WHERE household_id = :householdId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(householdId: Long, since: Instant, limit: Int): List<Category>

    @Query(value = "SELECT * FROM categories WHERE uuid = :uuid AND household_id = :householdId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, householdId: Long): Category?
}
