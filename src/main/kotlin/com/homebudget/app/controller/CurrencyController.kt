package com.homebudget.app.controller

import com.homebudget.app.dto.*
import com.homebudget.app.service.CurrencyService
import com.homebudget.app.service.toResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/currencies")
class CurrencyController(private val service: CurrencyService) {

    @GetMapping
    fun getAll() = service.findAll().map { it.toResponse() }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long) = service.findById(id).toResponse()

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateCurrencyRequest) = service.create(req).toResponse()

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: UpdateCurrencyRequest) =
        service.update(id, req).toResponse()

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)
}
