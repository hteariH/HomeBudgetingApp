package com.homebudget.app.repository

import com.homebudget.app.domain.Account
import com.homebudget.app.domain.Household
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
    fun findAllByHousehold(household: Household): List<Account>

    fun findByIdAndHousehold(id: Long, household: Household): Account?
    fun findByUuidAndHousehold(uuid: String, household: Household): Account?

    /**
     * Every currency somebody actually holds money in.
     *
     * The FX job quotes these: an account is the reason a rate is ever needed, and on the
     * web nothing else says which currencies a household has - the catalogue is global and
     * `household_currency_settings` is written by the phone alone.
     */
    @Query("SELECT DISTINCT a.currency.code FROM Account a")
    fun currencyCodesInUse(): List<String>

    /** Accounts a previous import created, keyed by the source's own id. */
    fun findAllByHouseholdAndExternalAccountIdIsNotNull(household: Household): List<Account>

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
        WHERE a.household = :household
        """
    )
    fun balancesOf(household: Household): List<AccountBalanceRow>

    // ── Sync access ─────────────────────────────────────────────────────────
    // Native SQL on purpose: @SQLRestriction hides tombstones from every JPQL query,
    // and sync is the one place that must see them - a deletion the client never
    // learns about would be pushed straight back.

    @Query(
        value = "SELECT * FROM accounts WHERE household_id = :householdId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(householdId: Long, since: Instant, limit: Int): List<Account>

    @Query(value = "SELECT * FROM accounts WHERE uuid = :uuid AND household_id = :householdId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, householdId: Long): Account?
}
