package com.homebudget.app.repository

import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface CategoryRepository : JpaRepository<Category, Long> {
    fun findAllByUser(user: User): List<Category>
    fun findByUserAndType(user: User, type: CategoryType): List<Category>
    fun existsByNameAndTypeAndUser(name: String, type: CategoryType, user: User): Boolean
    fun findByIdAndUser(id: Long, user: User): Category?
    fun findByUuidAndUser(uuid: String, user: User): Category?

    /** Categories a previous import created, keyed by the source's own id. */
    fun findAllByUserAndExternalIdIsNotNull(user: User): List<Category>

    @Query("SELECT DISTINCT c FROM Category c JOIN c.mccCodes m WHERE c.user = :user AND m = :mcc and c.type = :type")
    fun findByUserAndMccCodeAndType(user: User, mcc: Int, type: CategoryType): List<Category>

    // ── Sync access ─────────────────────────────────────────────────────────
    // Native SQL on purpose: @SQLRestriction hides tombstones from every JPQL query,
    // and sync is the one place that must see them - a deletion the client never
    // learns about would be pushed straight back.

    @Query(
        value = "SELECT * FROM categories WHERE user_id = :userId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(userId: Long, since: Instant, limit: Int): List<Category>

    @Query(value = "SELECT * FROM categories WHERE uuid = :uuid AND user_id = :userId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, userId: Long): Category?
}
