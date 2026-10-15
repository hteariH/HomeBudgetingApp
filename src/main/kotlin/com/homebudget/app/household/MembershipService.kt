package com.homebudget.app.household

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.HouseholdMember
import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.domain.User
import com.homebudget.app.repository.BankConnectionRepository
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.HouseholdRepository
import com.homebudget.app.security.SecurityUtils
import com.homebudget.app.service.CategoryService
import com.homebudget.app.service.HouseholdService
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Changes to who belongs to the signed-in user's household and who owns it. Each takes the
 * household's lock first - the one joins and sync pushes take - so a leave or removal
 * cannot interleave with a join into the same household.
 */
@Service
@Transactional
class MembershipService(
    private val members: HouseholdMemberRepository,
    private val households: HouseholdRepository,
    private val bankConnections: BankConnectionRepository,
    private val householdService: HouseholdService,
    private val categories: CategoryService,
    private val securityUtils: SecurityUtils,
    private val em: EntityManager
) {
    /**
     * The caller's membership, read again once their household is locked: it may have
     * changed (a hand-over, a removal, a join elsewhere) while this call waited.
     */
    private fun mine(): HouseholdMember {
        val membership = members.findFirstByUserId(securityUtils.getCurrentUser().id)
            ?: throw IllegalStateException("No household")
        val locked = membership.household.id
        households.lockById(locked)
        try {
            em.refresh(membership)
        } catch (e: EntityNotFoundException) {
            throw IllegalArgumentException("Your household changed. Try again.")
        }
        require(membership.household.id == locked) { "Your household changed. Try again." }
        return membership
    }

    fun leave() {
        val me = mine()
        val count = members.countByHouseholdId(me.household.id)
        require(count > 1) { "You are the only member of this household" }
        require(me.role != HouseholdRole.OWNER) { "Hand ownership to another member before leaving" }
        detach(me)
    }

    fun remove(userId: Long) {
        val me = mine()
        requireOwner(me)
        require(userId != me.user.id) { "Use leave, or hand ownership over first" }
        detach(memberOf(me, userId))
    }

    fun transferOwnership(userId: Long) {
        val me = mine()
        requireOwner(me)
        val target = memberOf(me, userId)
        require(target.user.id != me.user.id) { "You already own this household" }
        members.save(target.apply { role = HouseholdRole.OWNER })
        members.save(me.apply { role = HouseholdRole.MEMBER })
    }

    fun rename(name: String): Household {
        val me = mine()
        requireOwner(me)
        require(name.isNotBlank()) { "A household needs a name" }
        val household = households.findById(me.household.id).orElseThrow()
        household.name = name.trim()
        household.updatedAt = Instant.now()
        return households.save(household)
    }

    private fun memberOf(me: HouseholdMember, userId: Long): HouseholdMember =
        members.findAllByHouseholdId(me.household.id).firstOrNull { it.user.id == userId }
            ?: throw NoSuchElementException("Not a member of this household: $userId")

    /**
     * Gives [membership]'s user a household of their own, as at registration. Their
     * transactions stay where they were written, with their name as the author. The bank
     * connections they made go: they hold the person's own credentials. The family's
     * accounts linked to them are unlinked and keep their history.
     */
    private fun detach(membership: HouseholdMember) {
        val user: User = membership.user
        val baseCurrency = membership.household.baseCurrency
        bankConnections.deleteMadeBy(membership.household.id, user.id)
        members.delete(membership)
        members.flush()
        categories.seedDefaultCategories(householdService.createFor(user, baseCurrency))
    }
}
