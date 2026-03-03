package com.homebudget.app.service

import com.homebudget.app.domain.Account
import com.homebudget.app.dto.CreateAccountRequest
import com.homebudget.app.dto.UpdateAccountRequest
import com.homebudget.app.repository.AccountRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class AccountService(
    private val accountRepo: AccountRepository,
    private val currencyService: CurrencyService,
    private val securityUtils: com.homebudget.app.security.SecurityUtils
) {
    fun findAll(): List<Account> = accountRepo.findAllByUser(securityUtils.getCurrentUser())

    fun findById(id: Long): Account = accountRepo.findByIdAndUser(id, securityUtils.getCurrentUser())
        ?: throw NoSuchElementException("Account not found: $id")

    fun create(req: CreateAccountRequest): Account {
        val user = securityUtils.getCurrentUser()
        val currency = currencyService.findById(req.currencyId)
        return accountRepo.save(
            Account(
                name = req.name,
                type = req.type,
                balance = req.initialBalance,
                user = user,
                currency = currency,
                description = req.description,
                color = req.color
            )
        )
    }

    fun update(id: Long, req: UpdateAccountRequest): Account {
        val account = findById(id)
        req.name?.let { account.name = it }
        req.type?.let { account.type = it }
        req.description?.let { account.description = it }
        req.color?.let { account.color = it }
        account.updatedAt = LocalDateTime.now()
        return accountRepo.save(account)
    }

    fun delete(id: Long) {
        val account = findById(id)
        accountRepo.delete(account)
    }

    fun adjustBalance(id: Long, delta: java.math.BigDecimal) {
        val account = findById(id)
        account.balance = account.balance.add(delta)
        account.updatedAt = LocalDateTime.now()
        accountRepo.save(account)
    }
}
