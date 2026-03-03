package com.homebudget.app.controller

import com.homebudget.app.dto.CreateTransferRequest
import com.homebudget.app.dto.PagedResponse
import com.homebudget.app.service.TransferService
import com.homebudget.app.service.toResponse
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/transfers")
class TransferController(private val transferService: TransferService) {

    @GetMapping
    fun getAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<com.homebudget.app.dto.TransferResponse> {
        val pageable = PageRequest.of(page, size)
        val pagedTransfers = transferService.findAll(pageable)
        return PagedResponse(
            content = pagedTransfers.content.map { it.toResponse() },
            page = pagedTransfers.number,
            size = pagedTransfers.size,
            totalElements = pagedTransfers.totalElements,
            totalPages = pagedTransfers.totalPages
        )
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long) = transferService.findById(id).toResponse()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateTransferRequest) =
        transferService.create(req).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = transferService.delete(id)
}
