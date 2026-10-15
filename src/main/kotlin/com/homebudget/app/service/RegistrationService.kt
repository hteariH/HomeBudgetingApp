package com.homebudget.app.service

import com.homebudget.app.domain.InstanceSetting
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.dto.RegisterRequest
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.InstanceSettingRepository
import com.homebudget.app.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Registration on this instance is closed. */
class RegistrationClosedException : RuntimeException(
    "Registration on this instance is closed. Ask its administrator for an account."
)

/**
 * Who may register, in order of authority: the `REGISTRATION` environment variable, then
 * the administrator's toggle, then the default - open only while the instance has nobody,
 * so the person who installed it can create the first account and nobody else can.
 */
object RegistrationPolicy {
    private val EXPLICIT = setOf("open", "closed")

    fun isOpen(environment: String, stored: String?, userCount: Long): Boolean {
        val env = environment.trim().lowercase()
        if (env in EXPLICIT) return env == "open"
        if (stored != null) return stored == "open"
        return userCount == 0L
    }

    fun lockedByEnvironment(environment: String): Boolean = environment.trim().lowercase() in EXPLICIT
}

@Service
class RegistrationService(
    private val users: UserRepository,
    private val settings: InstanceSettingRepository,
    private val currencies: CurrencyRepository,
    private val households: HouseholdService,
    private val categories: CategoryService,
    private val authFlow: AuthFlowService,
    private val mail: MailAvailability,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.instance.registration:}") private val registrationEnv: String
) {
    companion object {
        const val REGISTRATION_KEY = "registration"
    }

    fun setupRequired(): Boolean = users.count() == 0L

    fun registrationOpen(): Boolean = RegistrationPolicy.isOpen(
        environment = registrationEnv,
        stored = settings.findById(REGISTRATION_KEY).map { it.value }.orElse(null),
        userCount = users.count()
    )

    fun registrationLockedByEnvironment(): Boolean = RegistrationPolicy.lockedByEnvironment(registrationEnv)

    @Transactional
    fun setRegistrationOpen(open: Boolean) {
        check(!registrationLockedByEnvironment()) {
            "Registration is set by the REGISTRATION environment variable"
        }
        settings.save(InstanceSetting(REGISTRATION_KEY, if (open) "open" else "closed"))
    }

    /**
     * Creates the login, its household and the default categories in one transaction.
     *
     * The very first account is the instance's administrator: whoever installed it is the
     * only person who can reach a fresh instance before the door closes behind them.
     */
    @Transactional
    fun register(req: RegisterRequest): User {
        if (!registrationOpen()) throw RegistrationClosedException()
        require(!users.existsByEmail(req.email)) { "Email is already in use" }
        val first = setupRequired()
        val user = users.save(
            User(
                email = req.email,
                passwordHash = passwordEncoder.encode(req.password),
                firstName = req.firstName,
                lastName = req.lastName,
                role = if (first) Role.ADMIN else Role.USER,
                // Nobody can be asked to prove an address the instance cannot mail.
                emailVerified = first || !mail.enabled
            )
        )
        val baseCurrency = req.baseCurrencyId?.let { currencies.findById(it).orElse(null) }
            ?: currencies.findByCode("USD")
        categories.seedDefaultCategories(households.createFor(user, baseCurrency))
        if (mail.enabled && !user.emailVerified) authFlow.sendVerification(user)
        return user
    }
}
