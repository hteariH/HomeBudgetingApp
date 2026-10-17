package com.homebudget.app.service

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.household.requireOwner
import com.homebudget.app.receipt.ReceiptImages
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.HouseholdRepository
import com.homebudget.app.repository.UserRepository
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
    private val users: UserRepository,
    private val currencyService: CurrencyService,
    private val securityUtils: SecurityUtils,
    private val em: EntityManager,
    private val receiptImages: ReceiptImages
) {
    fun getCurrentUser(): User = securityUtils.getCurrentUser()

    fun updateBaseCurrency(currencyId: Long): Household {
        requireOwner(memberRepository.findFirstByUserId(getCurrentUser().id)!!)
        val household = householdRepository.findById(securityUtils.getCurrentHousehold().id).orElseThrow()
        household.baseCurrency = currencyService.findById(currencyId)
        household.updatedAt = Instant.now()
        return householdRepository.save(household)
    }

    /**
     * Refused for the only administrator while anyone else still uses the instance: nobody
     * would be left who can issue reset links, delete accounts or open registration.
     */
    fun deleteCurrentUser() {
        val user = getCurrentUser()
        require(user.role != Role.ADMIN || users.countByRole(Role.ADMIN) > 1 || users.count() == 1L) {
            "You are this instance's only administrator. Delete the other accounts first, " +
                "or leave yours in place."
        }
        deleteUser(user)
    }

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
        val membership = memberRepository.findFirstByUserId(user.id)?.also {
            // The household's lock, as membership changes and joins take it; then the row
            // as it is now, since a hand-over may have landed while this call waited.
            householdRepository.lockById(it.household.id)
            em.refresh(it)
        }
        if (membership != null && membership.role == HouseholdRole.OWNER &&
            memberRepository.countByHouseholdId(membership.household.id) > 1
        ) {
            // Never leave a household without an owner: the longest-standing member takes over.
            memberRepository.findAllByHouseholdIdOrderByJoinedAtAscIdAsc(membership.household.id)
                .first { it.user.id != user.id }
                .let { memberRepository.save(it.apply { role = HouseholdRole.OWNER }) }
        }
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
        // Receipt photos are files, which no cascade reaches. Their own paths, not the
        // household's directory: a receipt merged in from another household keeps its old path.
        // A receipt an assistant sent as text has no photo.
        @Suppress("UNCHECKED_CAST")
        val photos = em.createNativeQuery("SELECT image_path FROM receipts WHERE household_id = :id AND image_path IS NOT NULL")
            .setParameter("id", householdId).resultList as List<String>
        listOf(
            "DELETE FROM category_mcc_codes WHERE category_id IN (SELECT id FROM categories WHERE household_id = :id)",
            "DELETE FROM transactions WHERE household_id = :id",
            "DELETE FROM receipts WHERE household_id = :id",
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
        photos.forEach(receiptImages::deleteAfterCommit)
    }
}
