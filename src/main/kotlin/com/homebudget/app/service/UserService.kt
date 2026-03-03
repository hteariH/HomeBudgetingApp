package com.homebudget.app.service

import com.homebudget.app.domain.User
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val currencyService: CurrencyService,
    private val securityUtils: SecurityUtils
) {
    fun getCurrentUser(): User = securityUtils.getCurrentUser()

    fun updateBaseCurrency(currencyId: Long): User {
        val user = getCurrentUser()
        val currency = currencyService.findById(currencyId)
        user.baseCurrency = currency
        return userRepository.save(user)
    }
}
