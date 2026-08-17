package com.homebudget.app.controller

import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.*
import com.homebudget.app.service.AccountService
import com.homebudget.app.service.CategoryService
import com.homebudget.app.service.TransactionService
import com.homebudget.app.service.toResponse
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/transactions")
class TransactionController(
    private val service: TransactionService,
    private val accountService: AccountService,
    private val categoryService: CategoryService
) {

    @GetMapping
    fun getAll(
        @RequestParam accountId: Long?,
        @RequestParam categoryId: Long?,
        @RequestParam type: TransactionType?,
        @RequestParam dateFrom: LocalDate?,
        @RequestParam dateTo: LocalDate?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PagedResponse<TransactionResponse> {
        // Ordering is baked into the query - occurred_at carries a time now, so it
        // alone is enough to keep same-day entries stable.
        val pageable = PageRequest.of(page, size)
        val result = service.findFiltered(accountId, categoryId, type, dateFrom, dateTo, pageable)
        val balances = accountService.balances()
        val decimals = categoryService.baseDecimals()
        return PagedResponse(
            content = result.content.map { it.toResponse(balances[it.account.id] ?: 0L, decimals) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): TransactionResponse {
        val tx = service.findById(id)
        return tx.toResponse(accountService.balanceOf(tx.account.id), categoryService.baseDecimals())
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateTransactionRequest): TransactionResponse {
        val tx = service.create(req)
        return tx.toResponse(accountService.balanceOf(tx.account.id), categoryService.baseDecimals())
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: UpdateTransactionRequest): TransactionResponse {
        val tx = service.update(id, req)
        return tx.toResponse(accountService.balanceOf(tx.account.id), categoryService.baseDecimals())
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)
}
