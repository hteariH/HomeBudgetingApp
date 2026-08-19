package com.personalmoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalmoney.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/** Every read filters `deleted = 0`; see the note on [TransactionDao]. */
@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE deleted = 0 AND archived = 0 ORDER BY sort_order, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY archived, sort_order, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id AND deleted = 0 LIMIT 1")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET archived = :archived, pending_sync = 1 WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE accounts SET sort_order = :order, pending_sync = 1 WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    /**
     * Tombstones the account and its ledger together. The foreign key cascade only
     * fires on a real DELETE, so the transactions have to be walked explicitly or they
     * stay visible and keep counting towards other totals.
     */
    @Query("UPDATE accounts SET deleted = 1, pending_sync = 1 WHERE id = :id")
    suspend fun tombstoneById(id: Long)

    @Query("UPDATE transactions SET deleted = 1, pending_sync = 1 WHERE account_id = :id")
    suspend fun tombstoneTransactionsOf(id: Long)

    @Query("UPDATE subscriptions SET deleted = 1, pending_sync = 1 WHERE account_id = :id")
    suspend fun tombstoneSubscriptionsOf(id: Long)

    /** Only for a full local reset, e.g. signing out. */
    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions WHERE account_id = :id AND deleted = 0")
    suspend fun transactionCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE deleted = 0 AND archived = 0")
    fun observeActiveCount(): Flow<Int>

    /**
     * Sum of (initial_balance + signed sums of transactions in account currency).
     * For expenses, account_amount_minor is positive; we subtract. Income adds.
     * Transfer rows are already signed and are added as they stand.
     */
    @Query(
        """
        SELECT a.id AS accountId,
               a.currency_code AS currencyCode,
               a.initial_balance_minor + COALESCE((
                   SELECT SUM(
                       CASE
                           WHEN t.type = 'INCOME' THEN t.account_amount_minor
                           WHEN t.type = 'EXPENSE' THEN -t.account_amount_minor
                           WHEN t.type = 'TRANSFER' THEN t.account_amount_minor
                           ELSE 0
                       END
                   ) FROM transactions t WHERE t.account_id = a.id AND t.deleted = 0
               ), 0) AS balanceMinor
        FROM accounts a
        WHERE a.deleted = 0
        """
    )
    fun observeBalances(): Flow<List<AccountBalanceRow>>

    /**
     * Per-account balance counting only transactions up to [asOfMs] (inclusive), restricted to
     * accounts included in the total. Used to reconstruct historical net worth for the trend.
     */
    @Query(
        """
        SELECT a.id AS accountId,
               a.currency_code AS currencyCode,
               a.initial_balance_minor + COALESCE((
                   SELECT SUM(
                       CASE
                           WHEN t.type = 'INCOME' THEN t.account_amount_minor
                           WHEN t.type = 'EXPENSE' THEN -t.account_amount_minor
                           WHEN t.type = 'TRANSFER' THEN t.account_amount_minor
                           ELSE 0
                       END
                   ) FROM transactions t
                   WHERE t.account_id = a.id AND t.deleted = 0 AND t.occurred_at <= :asOfMs
               ), 0) AS balanceMinor
        FROM accounts a
        WHERE a.deleted = 0 AND a.include_in_total = 1
        """
    )
    suspend fun balancesAsOf(asOfMs: Long): List<AccountBalanceRow>

    // ── Sync ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM accounts WHERE pending_sync = 1 ORDER BY id LIMIT :limit")
    suspend fun pendingForSync(limit: Int): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): AccountEntity?

    @Query("UPDATE accounts SET pending_sync = 0, updated_at = :serverStamp WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String, serverStamp: Long)

    @Query("SELECT COUNT(*) FROM accounts WHERE pending_sync = 1")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM accounts WHERE deleted = 1 AND pending_sync = 0")
    suspend fun purgeSyncedTombstones(): Int
}

data class AccountBalanceRow(
    val accountId: Long,
    val currencyCode: String,
    val balanceMinor: Long
)
