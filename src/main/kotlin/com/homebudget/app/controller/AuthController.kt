package com.homebudget.app.controller

import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.dto.*
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.JwtUtils
import com.homebudget.app.security.SecurityUtils
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.CategoryService
import com.homebudget.app.service.AuthFlowService
import com.homebudget.app.service.HouseholdService
import com.homebudget.app.service.RefreshTokenService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val currencyRepository: CurrencyRepository,
    private val categoryService: CategoryService,
    private val householdService: HouseholdService,
    private val refreshTokenService: RefreshTokenService,
    private val authFlowService: AuthFlowService,
    private val passwordEncoder: PasswordEncoder,
    private val securityUtils: SecurityUtils,
    private val jwtUtils: JwtUtils
) {

    @PostMapping("/login")
    fun authenticateUser(@Valid @RequestBody loginRequest: LoginRequest): ResponseEntity<*> {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(loginRequest.email, loginRequest.password)
        )

        SecurityContextHolder.getContext().authentication = authentication
        val userPrincipal = authentication.principal as UserPrincipal
        userRepository.save(userPrincipal.user.apply { lastSignInAt = Instant.now() })
        val jwt = jwtUtils.generateToken(userPrincipal)

        return ResponseEntity.ok(
            JwtResponse(
                token = jwt,
                refreshToken = refreshTokenService.issue(userPrincipal.user),
                email = userPrincipal.user.email,
                firstName = userPrincipal.user.firstName,
                lastName = userPrincipal.user.lastName,
                role = userPrincipal.user.role.name
            )
        )
    }

    /**
     * Trades a refresh token for a fresh access token, rotating the refresh token.
     *
     * Without this the access token has to live long enough that a phone is not logged
     * out daily, which is exactly the trade-off short-lived tokens exist to avoid.
     */
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<*> {
        val (user, newRefresh) = refreshTokenService.rotate(request.refreshToken)
        return ResponseEntity.ok(
            JwtResponse(
                token = jwtUtils.generateToken(UserPrincipal(user, householdService.householdOf(user))),
                refreshToken = newRefresh,
                email = user.email,
                firstName = user.firstName,
                lastName = user.lastName,
                role = user.role.name
            )
        )
    }

    /** Signs the caller out of every device by revoking all of their refresh tokens. */
    @PostMapping("/logout")
    fun logout(): ResponseEntity<*> {
        refreshTokenService.revokeAll(securityUtils.getCurrentUser())
        return ResponseEntity.ok(mapOf("message" to "Signed out"))
    }

    @PostMapping("/verify-email")
    fun verifyEmail(@Valid @RequestBody req: VerifyEmailRequest): ResponseEntity<*> {
        authFlowService.verifyEmail(req.token)
        return ResponseEntity.ok(mapOf("message" to "Address confirmed"))
    }

    /** Re-sends the confirmation link to the signed-in user. */
    @PostMapping("/resend-verification")
    fun resendVerification(): ResponseEntity<*> {
        authFlowService.sendVerification(securityUtils.getCurrentUser())
        return ResponseEntity.ok(mapOf("message" to "Confirmation sent"))
    }

    /**
     * Always reports success.
     *
     * Saying "no such account" would turn this into a way to test which addresses are
     * registered, which on a public site is a list worth harvesting.
     */
    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody req: ForgotPasswordRequest): ResponseEntity<*> {
        authFlowService.requestPasswordReset(req.email)
        return ResponseEntity.ok(
            mapOf("message" to "If that address has an account, a reset link is on its way.")
        )
    }

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody req: ResetPasswordRequest): ResponseEntity<*> {
        authFlowService.resetPassword(req.token, req.newPassword)
        return ResponseEntity.ok(mapOf("message" to "Password changed. Sign in with the new one."))
    }

    @PostMapping("/register")
    fun registerUser(@Valid @RequestBody registerRequest: RegisterRequest): ResponseEntity<*> {
        if (userRepository.existsByEmail(registerRequest.email)) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!")
        }

        val baseCurrency = registerRequest.baseCurrencyId?.let {
            currencyRepository.findById(it).orElse(null)
        } ?: currencyRepository.findByCode("USD")

        // Every registration is a plain USER. The role used to be derived from the
        // email address, which on a public site hands administrator rights to whoever
        // types that address first - there is no proof of ownership at registration.
        // Promotion is a deliberate act against the database.
        val user = User(
            email = registerRequest.email,
            passwordHash = passwordEncoder.encode(registerRequest.password),
            firstName = registerRequest.firstName,
            lastName = registerRequest.lastName,
            role = Role.USER
        )

        val savedUser = userRepository.save(user)
        val household = householdService.createFor(savedUser, baseCurrency)
        categoryService.seedDefaultCategories(household)
        // Sent asynchronously; a mail failure must not fail the registration, and the
        // user can ask for another link.
        authFlowService.sendVerification(savedUser)

        return ResponseEntity.ok(mapOf("message" to "User registered successfully!"))
    }
}
