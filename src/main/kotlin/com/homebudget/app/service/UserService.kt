package com.homebudget.app.service

import com.homebudget.app.domain.User
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.SecurityUtils
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val currencyService: CurrencyService,
    private val securityUtils: SecurityUtils,
    private val em: EntityManager
) {
    fun getCurrentUser(): User = securityUtils.getCurrentUser()

    fun updateBaseCurrency(currencyId: Long): User {
        val user = getCurrentUser()
        val currency = currencyService.findById(currencyId)
        user.baseCurrency = currency
        return userRepository.save(user)
    }

    /**
     * Erases the account outright.
     *
     * Native statements in child-before-parent order: soft deletes are updates, so the
     * FK cascades that would clean up after a real DELETE never fire for tombstoned
     * rows, and JPQL would not see those rows at all thanks to @SQLRestriction.
     */
    fun deleteCurrentUser() {
        val userId = getCurrentUser().id
        listOf(
            "DELETE FROM category_mcc_codes WHERE category_id IN (SELECT id FROM categories WHERE user_id = :uid)",
            "DELETE FROM transactions WHERE user_id = :uid",
            "DELETE FROM subscriptions WHERE user_id = :uid",
            // categories.parent_id points at categories: MySQL enforces FKs row by row,
            // so a single DELETE can hit a parent before its child. Break the links first.
            "UPDATE categories SET parent_id = NULL WHERE user_id = :uid",
            "DELETE FROM categories WHERE user_id = :uid",
            "DELETE FROM accounts WHERE user_id = :uid",
            "DELETE FROM user_currency_settings WHERE user_id = :uid",
            "DELETE FROM exchange_rates WHERE user_id = :uid",
            "DELETE FROM refresh_tokens WHERE user_id = :uid",
            "DELETE FROM users WHERE id = :uid"
        ).forEach { sql ->
            em.createNativeQuery(sql).setParameter("uid", userId).executeUpdate()
        }
    }
}
