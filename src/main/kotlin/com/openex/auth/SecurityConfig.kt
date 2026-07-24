package com.openex.auth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.web.SecurityFilterChain

/**
 * TEMPORARY dev-only security config.
 *
 * Spring Security is on the classpath (needed for JWT auth later), but with
 * no config it locks every endpoint behind an auto-generated password that
 * rotates on every restart — annoying while testing the ledger/matching
 * engine with curl/Postman.
 *
 * This permits everything for now. Replace with real JWT auth
 * (validating tokens, securing /orders, /accounts, etc.) once the `auth`
 * package is built out later in Week 1.
 */
@Configuration
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }

        return http.build()
    }
}
