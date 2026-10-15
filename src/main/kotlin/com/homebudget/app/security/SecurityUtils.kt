package com.homebudget.app.security

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.User
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class SecurityUtils {
    fun getCurrentUser(): User = principal().user

    /** The household whose ledger this request reads and writes. */
    fun getCurrentHousehold(): Household = principal().household

    private fun principal(): UserPrincipal =
        SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
            ?: throw IllegalStateException("No authenticated user found")
}
