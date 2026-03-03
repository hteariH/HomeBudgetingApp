package com.homebudget.app.service

import com.homebudget.app.domain.Transfer
import com.homebudget.app.dto.CreateTransferRequest
import com.homebudget.app.repository.TransferRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TransferService(
    private val transferRepo: TransferRepository,
    private val accountService: AccountService
) {
    fun findAll(pageable: Pageable): Page<Transfer> =
        transferRepo.findAllByOrderByDateDescCreatedAtDesc(pageable)

    fun findById(id: Long): Transfer = transferRepo.findByIdOrNull(id)
        ?: throw NoSuchElementException("Transfer not found: $id")

    fun findByAccount(accountId: Long): List<Transfer> =
        transferRepo.findByFromAccountIdOrToAccountIdOrderByDateDescCreatedAtDesc(accountId, accountId)

    fun create(req: CreateTransferRequest): Transfer {
        if (req.fromAccountId == req.toAccountId) {
            throw IllegalArgumentException("Cannot transfer to the same account")
        }
        val fromAccount = accountService.findById(req.fromAccountId)
        val toAccount = accountService.findById(req.toAccountId)

        val transfer = transferRepo.save(
            Transfer(
                fromAccount = fromAccount,
                toAccount = toAccount,
                fromAmount = req.fromAmount,
                toAmount = req.toAmount,
                exchangeRate = req.exchangeRate,
                date = req.date,
                description = req.description
            )
        )

        // Debit source, credit destination
        accountService.adjustBalance(req.fromAccountId, req.fromAmount.negate())
        accountService.adjustBalance(req.toAccountId, req.toAmount)

        return transfer
    }
}
