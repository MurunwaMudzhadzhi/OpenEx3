package com.openex.order

import com.fasterxml.jackson.databind.ObjectMapper
import com.openex.auth.JwtService
import com.openex.ledger.Account
import com.openex.ledger.AccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var accountRepository: AccountRepository
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var matchingEngine: com.openex.matching.MatchingEngine
    @Autowired lateinit var jwtService: JwtService

    private lateinit var userId: UUID
    private lateinit var authHeader: String

    @BeforeEach
    fun setUp() {
        matchingEngine.resetForTesting()
        userId = UUID.randomUUID()
        authHeader = "Bearer " + jwtService.issueToken(userId, "trader-${userId}@openex.test")
        accountRepository.save(Account(userId = userId, asset = "USD", balance = BigDecimal("100000.00000000")))
        accountRepository.save(Account(userId = userId, asset = "BTC", balance = BigDecimal("10.00000000")))
    }

    private fun requestJson(
        symbol: String = "BTC-USD",
        side: OrderSide = OrderSide.BUY,
        type: OrderType = OrderType.LIMIT,
        price: BigDecimal? = BigDecimal("100"),
        quantity: BigDecimal? = BigDecimal("1"),
    ): String = objectMapper.writeValueAsString(
        mapOf(
            "symbol" to symbol,
            "side" to side,
            "type" to type,
            "price" to price,
            "quantity" to quantity,
        )
    )

    @Test
    fun `submitting a valid order returns 200 with order details`() {
        mockMvc.perform(
            post("/orders")
                .header("Authorization", authHeader)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.symbol").value("BTC-USD"))
    }

    @Test
    fun `request without a valid token is rejected`() {
        mockMvc.perform(
            post("/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson())
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `retrying with the same idempotency key does not create a second order`() {
        val key = UUID.randomUUID().toString()
        val body = requestJson()

        mockMvc.perform(
            post("/orders").header("Authorization", authHeader).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isOk)

        val countAfterFirst = orderRepository.findByUserId(userId).size

        // Same key, same body — should replay, not reprocess
        mockMvc.perform(
            post("/orders").header("Authorization", authHeader).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isOk)

        val countAfterSecond = orderRepository.findByUserId(userId).size
        assertEquals(countAfterFirst, countAfterSecond)
    }

    @Test
    fun `reusing an idempotency key with a different body is rejected`() {
        val key = UUID.randomUUID().toString()

        mockMvc.perform(
            post("/orders").header("Authorization", authHeader).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(requestJson(quantity = BigDecimal("1")))
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/orders").header("Authorization", authHeader).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(requestJson(quantity = BigDecimal("2")))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `LIMIT order without a price is rejected`() {
        mockMvc.perform(
            post("/orders")
                .header("Authorization", authHeader)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(type = OrderType.LIMIT, price = null))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `missing quantity is rejected by validation`() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "symbol" to "BTC-USD",
                "side" to OrderSide.BUY,
                "type" to OrderType.LIMIT,
                "price" to BigDecimal("100"),
                // quantity omitted
            )
        )

        mockMvc.perform(
            post("/orders")
                .header("Authorization", authHeader)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `missing idempotency key header is rejected`() {
        mockMvc.perform(
            post("/orders")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson())
        ).andExpect(status().isBadRequest)
    }
}
