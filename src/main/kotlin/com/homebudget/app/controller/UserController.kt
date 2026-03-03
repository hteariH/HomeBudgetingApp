package com.homebudget.app.controller

import com.homebudget.app.service.UserService
import com.homebudget.app.service.toResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

    @GetMapping("/me")
    fun getCurrentUser() = userService.getCurrentUser().toResponse()

    @PutMapping("/me/base-currency")
    fun updateBaseCurrency(@RequestParam currencyId: Long) = 
        userService.updateBaseCurrency(currencyId).toResponse()
}
