package com.personalmoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query(
        """
        SELECT * FROM transactions
        WHERE (:fromMs IS NULL OR occurred_at >= :fromMs)
          AND (:toMs IS NULL OR occurred_at <= :toMs)
          AND (:accountId IS NULL OR account_id = :accountId)
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (:typeName IS NULL OR type = :typeName)
        ORDER BY occurred_at DESC, id DESC
        """
    )
    fun observeFiltered(
        fromMs: Long?,
        toMs: Long?,
        accountId: Long?,
        categoryId: Long?,
        typeName: String?
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transfer_group_id = :groupId")
    suspend fun findTransferPair(groupId: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions WHERE transfer_group_id = :groupId")
    suspend fun deleteByTransferGroup(groupId: String)

    @Transaction
    suspend fun insertTransferPair(debit: TransactionEntity, credit: TransactionEntity): Pair<Long, Long> {
        val a = insert(debit)
        val b = insert(credit)
        return a to b
    }

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = :typeName THEN account_amount_minor ELSE 0 END), 0) AS totalMinor
        FROM transactions
        WHERE occurred_at BETWEEN :fromMs AND :toMs
          AND account_id = :accountId
        """
    )
    fun observeSumByTypeInAccount(typeName: String, accountId: Long, fromMs: Long, toMs: Long): Flow<Long>

    @Query(
        """
        SELECT category_id AS categoryId,
               COALESCE(SUM(account_amount_minor), 0) AS totalMinor,
               COUNT(*) AS uses
        FROM transactions
        WHERE type = :typeName
          AND occurred_at BETWEEN :fromMs AND :toMs
        GROUP BY category_id
        ORDER BY totalMinor DESC
        """
    )
    fun observeCategoryTotals(typeName: String, fromMs: Long, toMs: Long): Flow<List<CategoryTotalRow>>
}

data class CategoryTotalRow(
    val categoryId: Long?,
    val totalMinor: Long,
    val uses: Int
)
