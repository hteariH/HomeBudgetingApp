package com.homebudget.app.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
class SecurityConfig(
    private val userDetailsService: CustomUserDetailsService,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val authRateLimitFilter: AuthRateLimitFilter
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider {
        val authProvider = DaoAuthenticationProvider()
        authProvider.setUserDetailsService(userDetailsService)
        authProvider.setPasswordEncoder(passwordEncoder())
        return authProvider
    }

    @Bean
    fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager {
        return authConfig.authenticationManager
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // These two act on the caller, so they need a session. They sit
                    // under /api/auth/** which is otherwise open, and without this an
                    // anonymous call reaches the controller and fails as a 500.
                    .requestMatchers("/api/auth/logout", "/api/auth/resend-verification").authenticated()
                    // The rest is reached without a session by definition: signing in,
                    // registering, and following a link out of an email.
                    .requestMatchers("/api/auth/**").permitAll()
                    // HomeMoney redirects a browser here after the user consents, and a
                    // browser carries no bearer token. The caller is identified by the
                    // signed state minted when the flow started, not by this filter -
                    // see HomeMoneyConnectionService.
                    .requestMatchers("/api/import/homemoney/callback").permitAll()
                    // Monobank webhook pushes updates directly from bank servers without bearer auth.
                    .requestMatchers("/api/bank/monobank/webhook/**").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
            }
            .authenticationProvider(authenticationProvider())
            // Rate limiting sits ahead of authentication: a blocked request must not
            // reach the password check at all, or it is not a limit on guessing.
            .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            // Allow H2 console frames
            .headers { it.frameOptions { frame -> frame.sameOrigin() } }

        return http.build()
    }
}
