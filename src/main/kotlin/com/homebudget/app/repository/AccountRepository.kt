package com.homebudget.app.repository

import com.homebudget.app.domain.Account
import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

/** One account's balance, summed from the ledger rather than read from a column. */
data class AccountBalanceRow(
    val accountId: Long,
    val balanceMinor: Long
)

@Repository
interface AccountRepository : JpaRepository<Account, Long> {
    fun findAllByUser(user: User): List<Account>

    /**
     * Accounts that count towards the free plan's cap - see
     * [com.homebudget.app.domain.PlanLimits]. Tombstoned rows are already excluded by the
     * entity's `@SQLRestriction`, so this is live, unarchived accounts.
     */
    fun countByUserAndArchivedFalse(user: User): Long
    fun findByIdAndUser(id: Long, user: User): Account?
    fun findByUuidAndUser(uuid: String, user: User): Account?

    /** Accounts a previous import created, keyed by the source's own id. */
    fun findAllByUserAndExternalAccountIdIsNotNull(user: User): List<Account>

    /** Detaches accounts from a connection being deleted; history is untouched. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Account a SET a.bankConnection = null, a.externalAccountId = null WHERE a.bankConnection.id = :connectionId")
    fun unlinkConnection(connectionId: Long): Int

    /**
     * Opening balance plus the signed ledger, per account, in one query.
     *
     * The CASE mirrors the sign convention documented on
     * [com.homebudget.app.domain.Transaction] and the Android client's balance query:
     * INCOME and EXPENSE store a positive magnitude and take their sign from the type,
     * while TRANSFER rows are already signed.
     */
    @Query(
        """
        SELECT new com.homebudget.app.repository.AccountBalanceRow(
            a.id,
            a.initialBalanceMinor + COALESCE((
                SELECT SUM(CASE
                    WHEN t.type = com.homebudget.app.domain.TransactionType.INCOME  THEN  t.accountAmountMinor
                    WHEN t.type = com.homebudget.app.domain.TransactionType.EXPENSE THEN -t.accountAmountMinor
                    ELSE t.accountAmountMinor
                END)
                FROM Transaction t WHERE t.account = a
            ), 0L)
        )
        FROM Account a
        WHERE a.user = :user
        """
    )
    fun balancesOf(user: User): List<AccountBalanceRow>

    // ── Sync access ─────────────────────────────────────────────────────────
    // Native SQL on purpose: @SQLRestriction hides tombstones from every JPQL query,
    // and sync is the one place that must see them - a deletion the client never
    // learns about would be pushed straight back.

    @Query(
        value = "SELECT * FROM accounts WHERE user_id = :userId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(userId: Long, since: Instant, limit: Int): List<Account>

    @Query(value = "SELECT * FROM accounts WHERE uuid = :uuid AND user_id = :userId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, userId: Long): Account?
}
