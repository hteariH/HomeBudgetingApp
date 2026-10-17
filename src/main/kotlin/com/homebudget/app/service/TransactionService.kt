package com.homebudget.app.service

import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.CreateTransactionRequest
import com.homebudget.app.dto.UpdateTransactionRequest
import com.homebudget.app.receipt.ReceiptService
import com.homebudget.app.repository.HouseholdRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
@Transactional
class TransactionService(
    private val transactionRepo: TransactionRepository,
    private val accountService: AccountService,
    private val categoryService: CategoryService,
    private val securityUtils: SecurityUtils,
    private val receiptService: ReceiptService,
    private val householdRepo: HouseholdRepository,
    private val syncClock: SyncClock
) {
    fun findFiltered(
        accountId: Long?,
        categoryId: Long?,
        type: TransactionType?,
        createdBy: Long?,
        dateFrom: LocalDate?,
        dateTo: LocalDate?,
        pageable: Pageable
    ): Page<Transaction> = transactionRepo.findFiltered(
        household = securityUtils.getCurrentHousehold(),
        accountId = accountId,
        categoryId = categoryId,
        type = type,
        createdBy = createdBy,
        from = dateFrom?.atStartOfDay(ZoneOffset.UTC)?.toInstant(),
        // Exclusive upper bound on the day *after* dateTo, so the whole of dateTo is
        // included now that the column carries a time as well as a date.
        until = dateTo?.plusDays(1)?.atStartOfDay(ZoneOffset.UTC)?.toInstant(),
        pageable = pageable
    )

    fun findById(id: Long): Transaction = transactionRepo.findByIdAndHousehold(id, securityUtils.getCurrentHousehold())
        ?: throw NoSuchElementException("Transaction not found: $id")

    /** [source] is [Transaction.SOURCE_MCP] only for the assistant's create tool. */
    fun create(req: CreateTransactionRequest, source: String = Transaction.SOURCE_APP): Transaction {
        require(req.type != TransactionType.TRANSFER) {
            "Transfers are created through /api/transfers so that both legs are written together"
        }
        val user = securityUtils.getCurrentUser()
        val account = accountService.findById(req.accountId)
        val category = req.categoryId?.let { categoryService.findById(it) }

        val expectedType = if (req.type == TransactionType.INCOME) {
            com.homebudget.app.domain.CategoryType.INCOME
        } else {
            com.homebudget.app.domain.CategoryType.EXPENSE
        }
        require(category == null || category.type == expectedType) {
            "Category '${category?.name}' is a ${category?.type} category, not ${req.type}"
        }

        val amountMinor = MinorUnits.toMinor(req.amount, account.currency.decimals)
        val household = securityUtils.getCurrentHousehold()
        // Stamped after everything the household already has, under its lock as a sync push
        // is, so a device whose cursor is the latest stamp still pulls this row.
        householdRepo.lockById(household.id)
        val receipt = req.receiptUuid?.let { receiptService.linkable(it, household, transactionUuid = null) }

        // No balance bookkeeping here on purpose - the balance is derived from these
        // rows, so inserting the row *is* the balance update.
        val transaction = Transaction(
            household = household,
            createdBy = user,
            account = account,
            category = category,
            amountMinor = amountMinor,
            currencyCode = account.currency.code,
            accountAmountMinor = amountMinor,
            type = req.type,
            occurredAt = req.occurredAt ?: req.date.atStartOfDay(ZoneOffset.UTC).toInstant(),
            description = req.description,
            source = source,
            updatedAt = syncClock.next(household.id)
        )
        transaction.linkReceipt(receipt)
        return transactionRepo.save(transaction)
    }

    fun update(id: Long, req: UpdateTransactionRequest): Transaction {
        val transaction = findById(id)
        require(transaction.type != TransactionType.TRANSFER) {
            "Edit a transfer through /api/transfers so both legs stay consistent"
        }

        req.categoryId?.let { transaction.category = categoryService.findById(it) }
        req.amount?.let {
            val minor = MinorUnits.toMinor(it, transaction.account.currency.decimals)
            transaction.amountMinor = minor
            transaction.accountAmountMinor = minor
        }
        req.date?.let { transaction.occurredAt = it.atStartOfDay(ZoneOffset.UTC).toInstant() }
        req.occurredAt?.let { transaction.occurredAt = it }
        req.description?.let { transaction.description = it }
        req.receiptUuid?.let {
            transaction.linkReceipt(receiptService.linkable(it, securityUtils.getCurrentHousehold(), transaction.uuid))
        }
        transaction.updatedAt = Instant.now()

        return transactionRepo.save(transaction)
    }

    fun delete(id: Long) {
        val transaction = findById(id)
        if (transaction.type == TransactionType.TRANSFER) {
            // Removing one leg would leave the other stranded and both balances wrong.
            val groupId = transaction.transferGroupId
                ?: throw IllegalStateException("Transfer row ${transaction.id} has no group id")
            tombstoneAll(transactionRepo.findAllByTransferGroupIdAndHousehold(groupId, securityUtils.getCurrentHousehold()))
            return
        }
        tombstoneAll(listOf(transaction))
    }

    /** Tombstones [rows]; a receipt saved with one of them is deleted, photo and all. */
    private fun tombstoneAll(rows: List<Transaction>) {
        val now = Instant.now()
        val receipts = rows.mapNotNull { it.receiptId }
        rows.forEach {
            it.deleted = true
            it.updatedAt = now
            it.linkReceipt(null)
        }
        transactionRepo.saveAll(rows)
        receipts.forEach(receiptService::deleteWithPhoto)
    }

    /** Used by the subscription job, which supplies its own instant. */
    fun instantFor(date: LocalDate): Instant = date.atStartOfDay(ZoneOffset.UTC).toInstant()
}
