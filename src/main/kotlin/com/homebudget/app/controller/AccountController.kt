package com.homebudget.app.controller

import com.homebudget.app.dto.*
import com.homebudget.app.service.AccountService
import com.homebudget.app.service.toResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/accounts")
class AccountController(private val service: AccountService) {

    @GetMapping
    fun getAll() = service.findAll().map { it.toResponse() }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long) = service.findById(id).toResponse()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateAccountRequest) = service.create(req).toResponse()

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: UpdateAccountRequest) =
        service.update(id, req).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)
}
