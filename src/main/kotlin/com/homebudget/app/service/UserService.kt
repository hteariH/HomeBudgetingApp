package com.homebudget.app.service

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.User
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.HouseholdRepository
import com.homebudget.app.security.SecurityUtils
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class UserService(
    private val householdRepository: HouseholdRepository,
    private val memberRepository: HouseholdMemberRepository,
    private val currencyService: CurrencyService,
    private val securityUtils: SecurityUtils,
    private val em: EntityManager
) {
    fun getCurrentUser(): User = securityUtils.getCurrentUser()

    fun updateBaseCurrency(currencyId: Long): Household {
        val household = householdRepository.findById(securityUtils.getCurrentHousehold().id).orElseThrow()
        household.baseCurrency = currencyService.findById(currencyId)
        household.updatedAt = Instant.now()
        return householdRepository.save(household)
    }

    fun deleteCurrentUser() = deleteUser(getCurrentUser())

    /**
     * Erases a login. The household and its whole ledger go with it when it was the
     * household's last member; otherwise the books stay with the others and the rows it
     * wrote lose their author (`ON DELETE SET NULL`).
     *
     * Native statements in child-before-parent order: soft deletes are updates, so the
     * FK cascades that would clean up after a real DELETE never fire for tombstoned
     * rows, and JPQL would not see those rows at all thanks to @SQLRestriction.
     */
    fun deleteUser(user: User) {
        val membership = memberRepository.findFirstByUserId(user.id)
        if (membership != null && memberRepository.countByHouseholdId(membership.household.id) == 1L) {
            deleteHousehold(membership.household.id)
        }
        listOf(
            "DELETE FROM household_members WHERE user_id = :id",
            "DELETE FROM refresh_tokens WHERE user_id = :id",
            "DELETE FROM auth_tokens WHERE user_id = :id",
            "DELETE FROM users WHERE id = :id"
        ).forEach { em.createNativeQuery(it).setParameter("id", user.id).executeUpdate() }
    }

    private fun deleteHousehold(householdId: Long) {
        listOf(
            "DELETE FROM category_mcc_codes WHERE category_id IN (SELECT id FROM categories WHERE household_id = :id)",
            "DELETE FROM transactions WHERE household_id = :id",
            "DELETE FROM subscriptions WHERE household_id = :id",
            // categories.parent_id points at categories; break the links before deleting.
            "UPDATE categories SET parent_id = NULL WHERE household_id = :id",
            "DELETE FROM categories WHERE household_id = :id",
            "DELETE FROM accounts WHERE household_id = :id",
            "DELETE FROM bank_connections WHERE household_id = :id",
            "DELETE FROM household_currency_settings WHERE household_id = :id",
            "DELETE FROM exchange_rates WHERE household_id = :id",
            "DELETE FROM household_members WHERE household_id = :id",
            "DELETE FROM households WHERE id = :id"
        ).forEach { em.createNativeQuery(it).setParameter("id", householdId).executeUpdate() }
    }
}
