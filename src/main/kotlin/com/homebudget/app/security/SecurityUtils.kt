package com.homebudget.app.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import com.homebudget.app.domain.User

@Component
class SecurityUtils {
    fun getCurrentUser(): User {
        val principal = SecurityContextHolder.getContext().authentication.principal
        if (principal is UserPrincipal) {
            return principal.user
        }
        throw IllegalStateException("No authenticated user found")
    }
}
