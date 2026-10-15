package com.homebudget.app.service

import com.homebudget.app.domain.Currency
import com.homebudget.app.domain.Household
import com.homebudget.app.domain.HouseholdMember
import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.domain.User
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.HouseholdRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class HouseholdService(
    private val households: HouseholdRepository,
    private val members: HouseholdMemberRepository
) {
    /** Every user owns a household from the moment they exist, named after them. */
    fun createFor(owner: User, baseCurrency: Currency?): Household {
        val household = households.save(Household(name = owner.firstName, baseCurrency = baseCurrency))
        members.save(HouseholdMember(household = household, user = owner, role = HouseholdRole.OWNER))
        return household
    }

    @Transactional(readOnly = true)
    fun householdOf(user: User): Household =
        members.findFirstByUserId(user.id)?.household
            ?: throw IllegalStateException("User ${user.id} belongs to no household")
}
