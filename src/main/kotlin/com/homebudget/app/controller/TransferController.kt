package com.homebudget.app.controller

import com.homebudget.app.dto.CreateTransferRequest
import com.homebudget.app.dto.PagedResponse
import com.homebudget.app.dto.TransferResponse
import com.homebudget.app.service.AccountService
import com.homebudget.app.service.TransferService
import com.homebudget.app.service.toTransferResponse
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * Transfers are stored as paired transaction rows; this endpoint presents them as one
 * object, which is how both clients think about them.
 */
@RestController
@RequestMapping("/api/transfers")
class TransferController(
    private val transferService: TransferService,
    private val accountService: AccountService
) {

    @GetMapping
    fun getAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<TransferResponse> {
        val paged = transferService.findAll(PageRequest.of(page, size))
        val balances = accountService.balances()
        return PagedResponse(
            content = paged.content.map { debit ->
                val credit = transferService.creditLegOf(debit)
                debit.toTransferResponse(
                    credit = credit,
                    debitBalanceMinor = balances[debit.account.id] ?: 0L,
                    creditBalanceMinor = balances[credit.account.id] ?: 0L
                )
            },
            page = paged.number,
            size = paged.size,
            totalElements = paged.totalElements,
            totalPages = paged.totalPages
        )
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): TransferResponse = respond(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateTransferRequest): TransferResponse {
        val debit = transferService.create(req)
        return respond(debit.id)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = transferService.delete(id)

    private fun respond(debitLegId: Long): TransferResponse {
        val debit = transferService.findById(debitLegId)
        val credit = transferService.creditLegOf(debit)
        val balances = accountService.balances()
        return debit.toTransferResponse(
            credit = credit,
            debitBalanceMinor = balances[debit.account.id] ?: 0L,
            creditBalanceMinor = balances[credit.account.id] ?: 0L
        )
    }
}
