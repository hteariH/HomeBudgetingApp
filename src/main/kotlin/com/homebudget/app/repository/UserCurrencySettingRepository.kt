package com.homebudget.app.repository

import com.homebudget.app.domain.User
import com.homebudget.app.domain.UserCurrencySetting
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface UserCurrencySettingRepository : JpaRepository<UserCurrencySetting, Long> {

    fun findAllByUser(user: User): List<UserCurrencySetting>
    fun findByUserAndCurrencyId(user: User, currencyId: Long): UserCurrencySetting?

    /** Every code switched on by anyone - the set the FX job needs to refresh. */
    @Query(
        """
        SELECT DISTINCT s.currency.code FROM UserCurrencySetting s
        WHERE s.active = true
        """
    )
    fun activeCurrencyCodes(): List<String>

    // ── Sync access ─────────────────────────────────────────────────────────
    // Native SQL on purpose: @SQLRestriction hides tombstones from every JPQL query,
    // and sync is the one place that must see them - a deletion the client never
    // learns about would be pushed straight back.

    @Query(
        value = "SELECT * FROM user_currency_settings WHERE user_id = :userId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(userId: Long, since: Instant, limit: Int): List<UserCurrencySetting>

    @Query(value = "SELECT * FROM user_currency_settings WHERE uuid = :uuid AND user_id = :userId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, userId: Long): UserCurrencySetting?
}
