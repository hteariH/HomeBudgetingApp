package com.personalmoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalmoney.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sort_order, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY archived, sort_order, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE accounts SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM accounts WHERE archived = 0")
    fun observeActiveCount(): Flow<Int>

    /**
     * Sum of (initial_balance + signed sums of transactions in account currency).
     * For expenses, account_amount_minor is positive; we subtract. Income adds. Transfers handled via account_amount sign.
     * For simplicity here, we return per-account computed balance via a separate query.
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
                           WHEN t.type = 'TRANSFER' AND t.amount_minor < 0 THEN t.account_amount_minor
                           WHEN t.type = 'TRANSFER' AND t.amount_minor >= 0 THEN t.account_amount_minor
                           ELSE 0
                       END
                   ) FROM transactions t WHERE t.account_id = a.id
               ), 0) AS balanceMinor
        FROM accounts a
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
                   WHERE t.account_id = a.id AND t.occurred_at <= :asOfMs
               ), 0) AS balanceMinor
        FROM accounts a
        WHERE a.include_in_total = 1
        """
    )
    suspend fun balancesAsOf(asOfMs: Long): List<AccountBalanceRow>
}

data class AccountBalanceRow(
    val accountId: Long,
    val currencyCode: String,
    val balanceMinor: Long
)
