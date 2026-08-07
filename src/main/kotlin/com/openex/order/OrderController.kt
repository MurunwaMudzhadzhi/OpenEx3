package com.openex.order

import com.fasterxml.jackson.databind.ObjectMapper
import com.openex.ledger.InsufficientBalanceException
import com.openex.matching.MatchingEngine
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.MethodArgumentNotValidException
import java.util.UUID

/**
 * Order submission API.
 *
 * Authentication: every request must carry a valid `Authorization: Bearer`
 * JWT (enforced by SecurityConfig). The userId comes from that token's
 * principal, not from the request body — a client can only ever place
 * orders as the account it's logged in as, and can't forge a different
 * userId by editing the request JSON.
 *
 * Idempotency: every request must include an `Idempotency-Key` header. The
 * key + a hash of the (validated, parsed) request body are checked against
 * the `idempotency_keys` table before any matching happens. A retried
 * request with the same key and same body replays the stored response
 * instead of resubmitting the order — so a client that mashes "Buy" 47
 * times in a panic gets exactly one order.
 *
 * Note on the hash: it's computed from the parsed+validated OrderRequest,
 * not the raw request bytes. This keeps the implementation simple (no
 * request-body-caching filter needed) at the cost of treating any two
 * requests that deserialize to the same OrderRequest as identical, even if
 * their raw JSON differed cosmetically (whitespace, key order). That's the
 * right tradeoff for this use case.
 */
@RestController
class OrderController(
    private val matchingEngine: MatchingEngine,
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping("/orders")
    fun submitOrder(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: OrderRequest,
    ): ResponseEntity<Any> {
        // SecurityConfig requires authentication for this endpoint, so the
        // principal is always present here — JwtAuthenticationFilter stores
        // it as a UUID.
        val userId = SecurityContextHolder.getContext().authentication.principal as UUID

        val requestHash = idempotencyService.hashOf(userId.toString() + objectMapper.writeValueAsString(request))

        when (val outcome = idempotencyService.check(idempotencyKey, requestHash)) {
            is IdempotencyOutcome.Replay -> {
                return ResponseEntity
                    .status(outcome.statusCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(outcome.responseBody)
            }
            IdempotencyOutcome.Fresh -> {
                // fall through to process below
            }
        }

        return try {
            validateOrderShape(request)

            val result = matchingEngine.submit(
                userId = userId,
                symbol = request.symbol!!,
                side = request.side!!,
                type = request.type!!,
                price = request.price,
                quantity = request.quantity!!,
            )

            val response = OrderResponse(
                orderId = result.order.id,
                symbol = result.order.symbol,
                side = result.order.side,
                type = result.order.type,
                price = result.order.price,
                quantity = result.order.quantity,
                filledQuantity = result.order.filledQuantity,
                status = result.order.status,
                trades = result.trades.map {
                    TradeSummary(
                        tradeId = it.id,
                        price = it.price,
                        quantity = it.quantity,
                        executedAt = it.executedAt,
                    )
                },
            )

            idempotencyService.complete(idempotencyKey, 200, objectMapper.writeValueAsString(response))
            ResponseEntity.ok(response)
        } catch (e: InsufficientBalanceException) {
            val error = ErrorResponse("insufficient_balance", e.message)
            idempotencyService.complete(idempotencyKey, 409, objectMapper.writeValueAsString(error))
            ResponseEntity.status(HttpStatus.CONFLICT).body(error)
        } catch (e: IllegalArgumentException) {
            val error = ErrorResponse("invalid_request", e.message)
            idempotencyService.complete(idempotencyKey, 400, objectMapper.writeValueAsString(error))
            ResponseEntity.badRequest().body(error)
        } catch (e: Exception) {
            // Catch-all: without this, any exception we didn't anticipate
            // (e.g. an internal invariant failure inside the matching
            // engine) leaves the idempotency key reserved with no response
            // ever recorded. IdempotencyService.check() treats that as
            // "still in flight" — so every future retry with the same key
            // would re-trigger order submission instead of ever replaying,
            // defeating the exact guarantee idempotency keys exist for.
            val error = ErrorResponse("internal_error", "The order could not be processed.")
            idempotencyService.complete(idempotencyKey, 500, objectMapper.writeValueAsString(error))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
        }
    }

    private fun validateOrderShape(request: OrderRequest) {
        when (request.type) {
            OrderType.LIMIT -> require(request.price != null) { "LIMIT orders require a price" }
            OrderType.MARKET -> require(request.price == null) { "MARKET orders must not include a price" }
            null -> {} // caught by @NotNull validation already
        }
    }

    @ExceptionHandler(IdempotencyKeyConflictException::class)
    fun handleIdempotencyConflict(e: IdempotencyKeyConflictException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("idempotency_key_conflict", e.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(ErrorResponse("validation_error", message))
    }
}
