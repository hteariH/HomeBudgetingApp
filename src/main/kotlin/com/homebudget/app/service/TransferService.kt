package com.homebudget.app.service

import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.CreateTransferRequest
import com.homebudget.app.household.HouseholdChangedException
import com.homebudget.app.repository.HouseholdRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * A transfer is two transaction rows sharing a `transferGroupId`: a negative debit on
 * the source account and a positive credit on the destination.
 *
 * There used to be a `transfers` table. Folding transfers into the ledger means the
 * sync protocol carries one entity type instead of two, the balance is a single SUM,
 * and cross-currency transfers need no special case - each leg simply records the
 * amount in its own account's currency.
 *
 * A "transfer" here is identified by its debit leg, whose id the API exposes as the
 * transfer id so that existing clients keep working.
 */
@Service
@Transactional
class TransferService(
    private val transactionRepo: TransactionRepository,
    private val accountService: AccountService,
    private val securityUtils: SecurityUtils,
    private val householdRepo: HouseholdRepository,
    private val syncClock: SyncClock
) {

    /** The debit leg of each transfer, newest first. */
    fun findAll(pageable: Pageable): Page<Transaction> =
        transactionRepo.findTransferDebitLegs(securityUtils.getCurrentHousehold(), pageable)

    fun findById(id: Long): Transaction {
        val leg = transactionRepo.findByIdAndHousehold(id, securityUtils.getCurrentHousehold())
            ?: throw NoSuchElementException("Transfer not found: $id")
        require(leg.type == TransactionType.TRANSFER) { "Transaction $id is not a transfer" }
        return leg
    }

    /** The credit leg paired with [debitLeg]. */
    fun creditLegOf(debitLeg: Transaction): Transaction {
        val groupId = debitLeg.transferGroupId
            ?: throw IllegalStateException("Transfer row ${debitLeg.id} has no group id")
        return transactionRepo.findAllByTransferGroupIdAndHousehold(groupId, debitLeg.household)
            .firstOrNull { it.id != debitLeg.id }
            ?: throw IllegalStateException("Transfer group $groupId is missing its credit leg")
    }

    /** [source] is [Transaction.SOURCE_MCP] only for the assistant's create tool. */
    fun create(req: CreateTransferRequest, source: String = Transaction.SOURCE_APP): Transaction {
        val household = securityUtils.getCurrentHousehold()
        // Both legs stamped after everything the household already has, under its lock as a
        // sync push is, so a device whose cursor is the latest stamp still pulls them.
        householdRepo.lockById(household.id) ?: throw HouseholdChangedException()
        val user = securityUtils.getCurrentUser()
        val fromAccount = accountService.findById(req.fromAccountId)
        val toAccount = accountService.findById(req.toAccountId)
        require(fromAccount.id != toAccount.id) { "Cannot transfer to the same account" }

        val fromMinor = MinorUnits.toMinor(req.fromAmount, fromAccount.currency.decimals)
        val toMinor = MinorUnits.toMinor(req.toAmount, toAccount.currency.decimals)
        val feeMinor = req.fee?.let { MinorUnits.toMinor(it, fromAccount.currency.decimals) }

        val rate = if (req.fromAmount.signum() != 0) {
            req.toAmount.divide(req.fromAmount, 10, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ONE
        }

        val stamp = syncClock.next(household.id)

        val groupId = UUID.randomUUID().toString()
        val occurredAt = req.occurredAt ?: req.date.atStartOfDay(ZoneOffset.UTC).toInstant()

        val debit = Transaction(
            household = household,
            createdBy = user,
            account = fromAccount,
            category = null,
            // Signed: the balance query adds TRANSFER rows as-is.
            amountMinor = -fromMinor,
            currencyCode = fromAccount.currency.code,
            accountAmountMinor = -fromMinor,
            fxRateToAccount = BigDecimal.ONE,
            type = TransactionType.TRANSFER,
            occurredAt = occurredAt,
            description = req.description,
            transferGroupId = groupId,
            transferCounterpartAccountId = toAccount.id,
            transferFeeMinor = feeMinor,
            source = source,
            updatedAt = stamp
        )

        val credit = Transaction(
            household = household,
            createdBy = user,
            account = toAccount,
            category = null,
            amountMinor = toMinor,
            currencyCode = toAccount.currency.code,
            accountAmountMinor = toMinor,
            fxRateToAccount = rate,
            type = TransactionType.TRANSFER,
            occurredAt = occurredAt,
            description = req.description,
            transferGroupId = groupId,
            transferCounterpartAccountId = fromAccount.id,
            source = source,
            updatedAt = stamp
        )

        // Both legs in one transaction: a half-written transfer is a corrupted balance.
        val saved = transactionRepo.saveAll(listOf(debit, credit))
        return saved.first { it.accountAmountMinor < 0 }
    }

    fun delete(id: Long) {
        val leg = findById(id)
        val groupId = leg.transferGroupId
            ?: throw IllegalStateException("Transfer row ${leg.id} has no group id")
        val now = Instant.now()
        val legs = transactionRepo.findAllByTransferGroupIdAndHousehold(groupId, securityUtils.getCurrentHousehold())
        legs.forEach {
            it.deleted = true
            it.updatedAt = now
        }
        transactionRepo.saveAll(legs)
    }
}
