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
    fun getAll(): List<AccountResponse> {
        // One balance query for the whole list rather than one per account.
        val balances = service.balances()
        return service.findAll()
            .sortedWith(compareBy({ it.archived }, { it.sortOrder }, { it.id }))
            .map { it.toResponse(balances[it.id] ?: 0L) }
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): AccountResponse {
        val account = service.findById(id)
        return account.toResponse(service.balanceOf(id))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateAccountRequest): AccountResponse {
        val account = service.create(req)
        return account.toResponse(service.balanceOf(account.id))
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: UpdateAccountRequest): AccountResponse {
        val account = service.update(id, req)
        return account.toResponse(service.balanceOf(account.id))
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)
}
