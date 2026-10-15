package com.homebudget.app.service

import com.homebudget.app.domain.Account
import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.dto.CreateAccountRequest
import com.homebudget.app.dto.UpdateAccountRequest
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.SubscriptionRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class AccountService(
    private val accountRepo: AccountRepository,
    private val currencyService: CurrencyService,
    private val transactionRepo: TransactionRepository,
    private val subscriptionRepo: SubscriptionRepository,
    private val securityUtils: SecurityUtils
) {
    fun findAll(): List<Account> = accountRepo.findAllByHousehold(securityUtils.getCurrentHousehold())

    fun findById(id: Long): Account = accountRepo.findByIdAndHousehold(id, securityUtils.getCurrentHousehold())
        ?: throw NoSuchElementException("Account not found: $id")

    /**
     * Current balance per account id, in minor units, summed from the ledger.
     *
     * There is deliberately no `adjustBalance` counterpart. The balance used to be a
     * stored column that every write nudged by a delta; replaying a write - which an
     * offline client's outbox does by design - applied the delta twice and the figure
     * drifted with nothing to detect it. Deriving it makes writes idempotent.
     */
    @Transactional(readOnly = true)
    fun balances(): Map<Long, Long> =
        accountRepo.balancesOf(securityUtils.getCurrentHousehold())
            .associate { it.accountId to it.balanceMinor }

    @Transactional(readOnly = true)
    fun balanceOf(accountId: Long): Long = balances()[accountId] ?: 0L

    fun create(req: CreateAccountRequest): Account {
        val household = securityUtils.getCurrentHousehold()
        val currency = currencyService.findById(req.currencyId)
        return accountRepo.save(
            Account(
                name = req.name,
                type = req.type,
                initialBalanceMinor = MinorUnits.toMinor(req.initialBalance, currency.decimals),
                household = household,
                currency = currency,
                description = req.description,
                icon = req.icon,
                color = req.color,
                sortOrder = req.sortOrder,
                includeInTotal = req.includeInTotal
            )
        )
    }

    fun update(id: Long, req: UpdateAccountRequest): Account {
        val account = findById(id)
        req.name?.let { account.name = it }
        req.type?.let { account.type = it }
        req.description?.let { account.description = it }
        req.icon?.let { account.icon = it }
        req.color?.let { account.color = it }
        req.archived?.let { account.archived = it }
        req.sortOrder?.let { account.sortOrder = it }
        req.includeInTotal?.let { account.includeInTotal = it }
        // Editing the opening balance shifts the whole history, which is exactly what
        // a user correcting a starting figure expects.
        req.initialBalance?.let {
            account.initialBalanceMinor = MinorUnits.toMinor(it, account.currency.decimals)
        }
        account.updatedAt = Instant.now()
        return accountRepo.save(account)
    }

    /**
     * Tombstones the account and everything hanging off it.
     *
     * A hard delete is invisible to a client that was offline when it happened - it
     * would simply push the rows back on next sync. The FK cascade cannot help either,
     * because a soft delete is an UPDATE, so dependants are walked explicitly.
     */
    fun delete(id: Long) {
        val account = findById(id)
        val now = Instant.now()
        transactionRepo.tombstoneByAccount(account.id, now)
        subscriptionRepo.tombstoneByAccount(account.id, now)
        account.deleted = true
        account.updatedAt = now
        accountRepo.save(account)
    }
}
