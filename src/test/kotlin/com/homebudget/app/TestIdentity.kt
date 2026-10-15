package com.homebudget.app

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.HouseholdMember
import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.HouseholdService
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * Users the way the application makes them: each with a household of their own.
 *
 * Registered in a test with `@Import(TestIdentity::class)`. [joinAs] stands in for the
 * invitation flow that sub-project 2 adds, so tests can already put two logins in one
 * household.
 */
class TestIdentity(
    private val users: UserRepository,
    private val currencies: CurrencyRepository,
    private val households: HouseholdService,
    private val members: HouseholdMemberRepository
) {
    fun newMember(prefix: String, baseCurrency: String = "USD"): UserPrincipal {
        val user = newUser(prefix)
        return UserPrincipal(user, households.createFor(user, currencies.findByCode(baseCurrency)))
    }

    fun joinAs(prefix: String, household: Household): UserPrincipal {
        val user = newUser(prefix)
        members.save(HouseholdMember(household = household, user = user, role = HouseholdRole.MEMBER))
        return UserPrincipal(user, household)
    }

    fun signIn(principal: UserPrincipal) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
    }

    private fun newUser(prefix: String): User = users.save(
        User(
            email = "$prefix-${UUID.randomUUID()}@example.com",
            passwordHash = "x",
            firstName = prefix,
            lastName = "Test",
            role = Role.USER
        )
    )
}
