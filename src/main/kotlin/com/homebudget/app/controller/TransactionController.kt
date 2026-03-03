package com.homebudget.app.controller

import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.*
import com.homebudget.app.service.TransactionService
import com.homebudget.app.service.toResponse
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/transactions")
class TransactionController(private val service: TransactionService) {

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
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date", "createdAt"))
        val result = service.findFiltered(accountId, categoryId, type, dateFrom, dateTo, pageable)
        return PagedResponse(
            content = result.content.map { it.toResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long) = service.findById(id).toResponse()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateTransactionRequest) = service.create(req).toResponse()

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: UpdateTransactionRequest) =
        service.update(id, req).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)
}
