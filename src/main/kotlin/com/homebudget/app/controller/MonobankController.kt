package com.homebudget.app.controller

import com.homebudget.app.dto.*
import com.homebudget.app.service.MonobankService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/monobank")
class MonobankController(private val service: MonobankService) {

    @GetMapping("/client-info")
    fun getClientInfo(@RequestParam token: String) = service.getClientInfo(token)

    @PostMapping("/accounts/{accountId}/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun linkAccount(@PathVariable accountId: Long, @Valid @RequestBody req: LinkMonobankRequest) =
        service.linkAccount(accountId, req)

    @DeleteMapping("/accounts/{accountId}/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlinkAccount(@PathVariable accountId: Long) = service.unlinkAccount(accountId)

    @PostMapping("/preview")
    fun previewTransactions(@RequestBody req: MonobankPreviewRequest) =
        service.previewTransactions(req)

    @PostMapping("/confirm")
    fun confirmImport(@RequestBody req: MonobankConfirmRequest) =
        service.confirmImport(req)
}
