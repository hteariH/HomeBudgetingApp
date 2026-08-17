package com.homebudget.app.controller

import com.homebudget.app.dto.CreateSubscriptionRequest
import com.homebudget.app.dto.SubscriptionResponse
import com.homebudget.app.dto.UpdateSubscriptionRequest
import com.homebudget.app.service.AccountService
import com.homebudget.app.service.CategoryService
import com.homebudget.app.service.SubscriptionService
import com.homebudget.app.service.toResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/subscriptions")
class SubscriptionController(
    private val service: SubscriptionService,
    private val accountService: AccountService,
    private val categoryService: CategoryService
) {

    @GetMapping
    fun getAll(): List<SubscriptionResponse> {
        val balances = accountService.balances()
        val baseDecimals = categoryService.baseDecimals()
        return service.findAll()
            .sortedWith(compareBy({ !it.active }, { it.nextChargeDate }, { it.id }))
            .map { it.toResponse(balances[it.account.id] ?: 0L, baseDecimals, it.account.currency.decimals) }
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): SubscriptionResponse = respond(service.findById(id).id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateSubscriptionRequest): SubscriptionResponse =
        respond(service.create(req).id)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: UpdateSubscriptionRequest): SubscriptionResponse =
        respond(service.update(id, req).id)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)

    private fun respond(id: Long): SubscriptionResponse {
        val sub = service.findById(id)
        return sub.toResponse(
            balanceMinor = accountService.balanceOf(sub.account.id),
            baseDecimals = categoryService.baseDecimals(),
            decimals = sub.account.currency.decimals
        )
    }
}
