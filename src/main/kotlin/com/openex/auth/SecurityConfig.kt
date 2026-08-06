package com.openex.auth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Dev-stage security config.
 *
 * Real register/login + JWT issuance now exist (/auth/register,
 * /auth/login), and JwtAuthenticationFilter will populate the security
 * context for any request bearing a valid token. Endpoints are still
 * permitAll for now — /orders still trusts the userId in its request body
 * rather than requiring the JWT — since tightening that also means updating
 * the frontend to attach the token and the existing OrderControllerTest
 * suite to send one. That's the natural next step once the frontend has a
 * login flow wired up end-to-end.
 */
@Configuration
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
