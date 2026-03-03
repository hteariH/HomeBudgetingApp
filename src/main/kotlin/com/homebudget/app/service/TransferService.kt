package com.homebudget.app.service

import com.homebudget.app.domain.Transfer
import com.homebudget.app.dto.CreateTransferRequest
import com.homebudget.app.repository.TransferRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.RoundingMode

@Service
@Transactional
class TransferService(
    private val transferRepo: TransferRepository,
    private val accountService: AccountService,
    private val securityUtils: SecurityUtils
) {
    fun findAll(pageable: Pageable): Page<Transfer> =
        transferRepo.findAllByUser(securityUtils.getCurrentUser(), pageable)

    fun findById(id: Long): Transfer =
        transferRepo.findByIdAndUser(id, securityUtils.getCurrentUser())
            ?: throw NoSuchElementException("Transfer not found: $id")

    fun create(req: CreateTransferRequest): Transfer {
        val user = securityUtils.getCurrentUser()
        val fromAccount = accountService.findById(req.fromAccountId)
        val toAccount = accountService.findById(req.toAccountId)

        val rate = if (req.fromAmount.signum() != 0) {
            req.toAmount.divide(req.fromAmount, 6, RoundingMode.HALF_UP)
        } else {
            java.math.BigDecimal.ONE
        }

        val transfer = transferRepo.save(
            Transfer(
                user = user,
                fromAccount = fromAccount,
                toAccount = toAccount,
                fromAmount = req.fromAmount,
                toAmount = req.toAmount,
                exchangeRate = rate,
                date = req.date,
                description = req.description
            )
        )

        // Adjust balances
        accountService.adjustBalance(fromAccount.id, req.fromAmount.negate())
        accountService.adjustBalance(toAccount.id, req.toAmount)

        return transfer
    }

    fun delete(id: Long) {
        val transfer = findById(id)

        // Reverse balances
        accountService.adjustBalance(transfer.fromAccount.id, transfer.fromAmount)
        accountService.adjustBalance(transfer.toAccount.id, transfer.toAmount.negate())

        transferRepo.delete(transfer)
    }
}
