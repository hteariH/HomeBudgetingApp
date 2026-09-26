package com.homebudget.app.controller

import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.domain.TransactionSplit
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.repository.TransactionSplitRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant

data class TransactionSplitResponse(
    val id: Long,
    val uuid: String,
    val transactionId: Long,
    val categoryId: Long?,
    val categoryName: String?,
    val amount: BigDecimal,
    val note: String?,
    val sortOrder: Int
)

data class SplitItemRequest(
    val categoryId: Long? = null,
    val amount: BigDecimal,
    val note: String? = null,
    val sortOrder: Int = 0
)

data class SaveSplitsRequest(
    val splits: List<SplitItemRequest>
)

@RestController
@RequestMapping("/api/transactions/{transactionId}/splits")
class TransactionSplitController(
    private val splitRepo: TransactionSplitRepository,
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val securityUtils: SecurityUtils
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun getSplits(@PathVariable transactionId: Long): List<TransactionSplitResponse> {
        val user = securityUtils.getCurrentUser()
        val tx = transactionRepo.findByIdAndUser(transactionId, user)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found")
        val decimals = tx.account.currency.decimals
        return splitRepo.findByTransactionId(tx.id).map { split ->
            TransactionSplitResponse(
                id = split.id,
                uuid = split.uuid,
                transactionId = tx.id,
                categoryId = split.category?.id,
                categoryName = split.category?.name,
                amount = MinorUnits.toDecimal(split.amountMinor, decimals),
                note = split.note,
                sortOrder = split.sortOrder
            )
        }
    }

    @PutMapping
    @Transactional
    fun saveSplits(
        @PathVariable transactionId: Long,
        @RequestBody request: SaveSplitsRequest
    ): List<TransactionSplitResponse> {
        val user = securityUtils.getCurrentUser()
        val tx = transactionRepo.findByIdAndUser(transactionId, user)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found")
        val decimals = tx.account.currency.decimals

        // Invariant: SUM(splits.amount_minor) == transaction.amount_minor
        val incomingSplits = request.splits
        if (incomingSplits.isNotEmpty()) {
            val sumMinor = incomingSplits.sumOf { MinorUnits.toMinor(it.amount, decimals) }
            if (sumMinor != tx.amountMinor) {
                throw ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Sum of split amounts ($sumMinor) must equal transaction amount (${tx.amountMinor})"
                )
            }
        }

        // Tombstone existing splits
        val existing = splitRepo.findByTransactionId(tx.id)
        val now = Instant.now()
        for (split in existing) {
            split.deleted = true
            split.updatedAt = now
            splitRepo.save(split)
        }

        // Create new splits
        val saved = incomingSplits.mapIndexed { index, req ->
            val category = req.categoryId?.let { catId ->
                categoryRepo.findByIdAndUser(catId, user)
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found: $catId")
            }
            val amountMinor = MinorUnits.toMinor(req.amount, decimals)
            splitRepo.save(
                TransactionSplit(
                    user = user,
                    transaction = tx,
                    category = category,
                    amountMinor = amountMinor,
                    note = req.note,
                    sortOrder = req.sortOrder.takeIf { it != 0 } ?: index,
                    updatedAt = now
                )
            )
        }

        return saved.map { split ->
            TransactionSplitResponse(
                id = split.id,
                uuid = split.uuid,
                transactionId = tx.id,
                categoryId = split.category?.id,
                categoryName = split.category?.name,
                amount = MinorUnits.toDecimal(split.amountMinor, decimals),
                note = split.note,
                sortOrder = split.sortOrder
            )
        }
    }
}
