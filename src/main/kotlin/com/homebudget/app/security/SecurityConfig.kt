package com.homebudget.app.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
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
                    // Read before anyone signs in: whether to show setup, register and
                    // "forgot password", and whether this is a PersonalMoney server at all.
                    .requestMatchers("/api/instance").permitAll()
                    // The join page reads an invitation's household and inviter before
                    // anyone has signed in; accepting it (a POST) still needs a session.
                    .requestMatchers(HttpMethod.GET, "/api/invites/*").permitAll()
                    // Assistants. McpAuthFilter (registered for this path alone) checks their
                    // personal and OAuth tokens; this chain never accepts those, so they open
                    // nothing under /api.
                    .requestMatchers("/mcp").permitAll()
                    // The OAuth server that web connectors use to obtain those tokens: its
                    // clients are public and carry no session. The consent page's own calls,
                    // under /api/oauth/**, need the signed-in user like the rest of /api.
                    .requestMatchers("/oauth/**", "/.well-known/oauth-*", "/.well-known/oauth-*/**").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
            }
            // A request without an accepted credential is a 401, as HTTP defines it. Without
            // an entry point Spring answers 403, which reads as "signed in but not allowed" -
            // and an assistant's token tried on the REST API is exactly "not signed in".
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.setHeader("WWW-Authenticate", "Bearer")
                    response.status = HttpStatus.UNAUTHORIZED.value()
                }
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
