package com.openex.ledger

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class BalanceResponse(
    val asset: String,
    val balance: String,
)

/**
 * Read-only endpoint for the logged-in user's own balances. Like /orders,
 * the userId comes from the authenticated JWT principal — there's no way
 * to query anyone else's balances by asking for a different id, because
 * there's no id parameter to ask with.
 */
@RestController
class AccountController(
    private val accountRepository: AccountRepository,
) {

    @GetMapping("/accounts")
    fun listMyAccounts(): List<BalanceResponse> {
        val userId = SecurityContextHolder.getContext().authentication.principal as UUID

        return accountRepository.findByUserId(userId)
            .map { BalanceResponse(asset = it.asset, balance = it.balance.toPlainString()) }
    }
}
