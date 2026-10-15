package com.homebudget.app.security

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * The signed-in login and the household whose ledger it works on, resolved once per
 * request when the token is checked.
 */
class UserPrincipal(val user: User, val household: Household) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
    }

    override fun getPassword(): String = user.passwordHash
    override fun getUsername(): String = user.email

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true

    fun getId(): Long = user.id
}
