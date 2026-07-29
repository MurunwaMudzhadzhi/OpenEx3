package com.openex.ledger

import java.util.UUID

class InsufficientBalanceException(
    accountId: UUID,
    requested: java.math.BigDecimal,
    available: java.math.BigDecimal,
) : RuntimeException(
    "Account $accountId has insufficient balance: requested $requested, available $available"
)
