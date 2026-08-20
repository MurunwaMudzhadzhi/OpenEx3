package com.openex.ledger

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

data class BalanceResponse(
    val asset: String,
    val balance: String,
)

/**
 * Read-only endpoint for the logged-in user's own balances. Like /orders,
 * the userId comes from the authenticated JWT principal â€” there's no way
 * to query anyone else's balances by asking for a different id, because
 * there's no id parameter to ask with.
 */
data class DepositRequest(
    val asset: String,
    val amount: BigDecimal,
)

@RestController
class AccountController(
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
) {

    @GetMapping("/accounts")
    fun listMyAccounts(): List<BalanceResponse> {
        val userId = SecurityContextHolder.getContext().authentication.principal as UUID

        return accountRepository.findByUserId(userId)
            .map { BalanceResponse(asset = it.asset, balance = it.balance.toPlainString()) }
    }

    @PostMapping("/accounts/deposit")
    fun deposit(@RequestBody request: DepositRequest): BalanceResponse {
        val userId = SecurityContextHolder.getContext().authentication.principal as UUID

        val entry = ledgerService.deposit(userId, request.asset, request.amount)
        val account = accountRepository.findById(entry.accountId).orElseThrow()

        return BalanceResponse(asset = account.asset, balance = account.balance.toPlainString())
    }
}
