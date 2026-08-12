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
 * Register/login (/auth/register, /auth/login) stay permitAll — you need
 * to reach them before you have a token. WebSocket handshakes under /ws
 * also stay permitAll for now; the order book/trade feed (including the
 * on-demand /orderbook/{symbol} snapshot, Day 5) are read-only public
 * market data, so authenticating that connection isn't needed yet.
 * /orders and /accounts require a valid JWT — the userId comes from the
 * authenticated principal, not the request body/params, so a client can
 * only ever act as or view the account it's logged in as.
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
            .authorizeHttpRequests {
                it.requestMatchers("/orders", "/accounts").authenticated()
                    .anyRequest().permitAll()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
