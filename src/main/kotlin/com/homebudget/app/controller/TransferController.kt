package com.homebudget.app.controller

import com.homebudget.app.dto.*
import com.homebudget.app.service.TransferService
import com.homebudget.app.service.toResponse
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/transfers")
class TransferController(private val service: TransferService) {

    @GetMapping
    fun getAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PagedResponse<TransferResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date", "createdAt"))
        val result = service.findAll(pageable)
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

    @GetMapping("/account/{accountId}")
    fun getByAccount(@PathVariable accountId: Long) =
        service.findByAccount(accountId).map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateTransferRequest) = service.create(req).toResponse()
}
