package com.homebudget.app.controller

import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.dto.JwtResponse
import com.homebudget.app.dto.LoginRequest
import com.homebudget.app.dto.RegisterRequest
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.JwtUtils
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.CategoryService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val currencyRepository: CurrencyRepository,
    private val categoryService: CategoryService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtils: JwtUtils
) {

    @PostMapping("/login")
    fun authenticateUser(@Valid @RequestBody loginRequest: LoginRequest): ResponseEntity<*> {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(loginRequest.email, loginRequest.password)
        )

        SecurityContextHolder.getContext().authentication = authentication
        val jwt = jwtUtils.generateToken(authentication.principal as UserPrincipal)
        val userPrincipal = authentication.principal as UserPrincipal

        return ResponseEntity.ok(
            JwtResponse(
                token = jwt,
                email = userPrincipal.user.email,
                firstName = userPrincipal.user.firstName,
                lastName = userPrincipal.user.lastName,
                role = userPrincipal.user.role.name
            )
        )
    }

    @PostMapping("/register")
    fun registerUser(@Valid @RequestBody registerRequest: RegisterRequest): ResponseEntity<*> {
        if (userRepository.existsByEmail(registerRequest.email)) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!")
        }

        val baseCurrency = registerRequest.baseCurrencyId?.let {
            currencyRepository.findById(it).orElse(null)
        }

        val userRole = if (registerRequest.email.equals("aberkunsky@gmail.com", ignoreCase = true)) {
            Role.ADMIN
        } else {
            Role.USER
        }

        val user = User(
            email = registerRequest.email,
            passwordHash = passwordEncoder.encode(registerRequest.password),
            firstName = registerRequest.firstName,
            lastName = registerRequest.lastName,
            role = userRole,
            baseCurrency = baseCurrency
        )

        val savedUser = userRepository.save(user)
        categoryService.seedDefaultCategories(savedUser)

        return ResponseEntity.ok(mapOf("message" to "User registered successfully!"))
    }
}
