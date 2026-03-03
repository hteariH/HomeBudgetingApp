package com.homebudget.app.service

import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.CreateTransactionRequest
import com.homebudget.app.dto.UpdateTransactionRequest
import com.homebudget.app.repository.TransactionRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional
class TransactionService(
    private val transactionRepo: TransactionRepository,
    private val accountService: AccountService,
    private val categoryService: CategoryService
) {
    fun findFiltered(
        accountId: Long?,
        categoryId: Long?,
        type: TransactionType?,
        dateFrom: LocalDate?,
        dateTo: LocalDate?,
        pageable: Pageable
    ): Page<Transaction> = transactionRepo.findFiltered(accountId, categoryId, type, dateFrom, dateTo, pageable)

    fun findById(id: Long): Transaction = transactionRepo.findByIdOrNull(id)
        ?: throw NoSuchElementException("Transaction not found: $id")

    fun create(req: CreateTransactionRequest): Transaction {
        val account = accountService.findById(req.accountId)
        val category = categoryService.findById(req.categoryId)

        val transaction = transactionRepo.save(
            Transaction(
                account = account,
                category = category,
                amount = req.amount,
                type = req.type,
                date = req.date,
                description = req.description
            )
        )

        // Update account balance
        val balanceDelta = if (req.type == TransactionType.INCOME) req.amount else req.amount.negate()
        accountService.adjustBalance(req.accountId, balanceDelta)

        return transaction
    }

    fun update(id: Long, req: UpdateTransactionRequest): Transaction {
        val transaction = findById(id)

        // Reverse the old balance effect
        val oldDelta = if (transaction.type == TransactionType.INCOME) transaction.amount.negate() else transaction.amount
        accountService.adjustBalance(transaction.account.id, oldDelta)

        req.categoryId?.let { transaction.category = categoryService.findById(it) }
        req.amount?.let { transaction.amount = it }
        req.date?.let { transaction.date = it }
        req.description?.let { transaction.description = it }
        transaction.updatedAt = LocalDateTime.now()

        val updated = transactionRepo.save(transaction)

        // Apply new balance effect
        val newDelta = if (updated.type == TransactionType.INCOME) updated.amount else updated.amount.negate()
        accountService.adjustBalance(updated.account.id, newDelta)

        return updated
    }

    fun delete(id: Long) {
        val transaction = findById(id)

        // Reverse balance effect
        val delta = if (transaction.type == TransactionType.INCOME) transaction.amount.negate() else transaction.amount
        accountService.adjustBalance(transaction.account.id, delta)

        transactionRepo.delete(transaction)
    }
}
