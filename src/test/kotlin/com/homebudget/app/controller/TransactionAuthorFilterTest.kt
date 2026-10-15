package com.homebudget.app.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.SyncAccount
import com.homebudget.app.dto.SyncPayload
import com.homebudget.app.dto.SyncPushRequest
import com.homebudget.app.dto.SyncTransaction
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.SyncService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
class TransactionAuthorFilterTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var syncService: SyncService
    @Autowired private lateinit var transactionRepository: TransactionRepository

    private lateinit var anna: UserPrincipal
    private lateinit var boris: UserPrincipal
    private val accountUuid = UUID.randomUUID().toString()
    private val annaTx = UUID.randomUUID().toString()
    private val borisTx = UUID.randomUUID().toString()

    private fun asUser(p: UserPrincipal) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

    private fun tx(uuid: String, amount: String) = SyncTransaction(
        uuid = uuid, accountUuid = accountUuid, amount = BigDecimal(amount), currencyCode = "USD",
        accountAmount = BigDecimal(amount), type = TransactionType.EXPENSE,
        occurredAt = Instant.parse("2026-10-01T10:00:00Z")
    )

    @BeforeEach
    fun setUp() {
        anna = identity.newMember("anna")
        boris = identity.joinAs("boris", anna.household)
        identity.signIn(anna)
        syncService.push(
            SyncPushRequest(
                SyncPayload(
                    accounts = listOf(SyncAccount(uuid = accountUuid, name = "Joint", currencyCode = "USD")),
                    transactions = listOf(tx(annaTx, "10.00"))
                )
            )
        )
        identity.signIn(boris)
        syncService.push(SyncPushRequest(SyncPayload(transactions = listOf(tx(borisTx, "20.00")))))
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `filtering by author returns only that member's transactions, each naming its author`() {
        mvc.perform(get("/api/transactions").param("createdBy", boris.user.id.toString()).with(asUser(anna)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].uuid").value(borisTx))
            .andExpect(jsonPath("$.content[0].createdByUserId").value(boris.user.id))
    }

    @Test
    fun `without the filter every member's transactions are listed with their authors`() {
        mvc.perform(get("/api/transactions").with(asUser(anna)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[?(@.uuid=='$annaTx')].createdByUserId").value(anna.user.id.toInt()))
            .andExpect(jsonPath("$.content[?(@.uuid=='$borisTx')].createdByUserId").value(boris.user.id.toInt()))
    }

    @Test
    fun `a transaction with no author still appears in an unfiltered list`() {
        // Subscription charges are written by the scheduler, not by a person.
        val row = transactionRepository.findByUuidAndHousehold(annaTx, anna.household)!!
        row.createdBy = null
        transactionRepository.save(row)

        val body = mvc.perform(get("/api/transactions").with(asUser(anna)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andReturn().response.contentAsString
        val authorless = objectMapper.readTree(body)["content"].single { it["uuid"].asText() == annaTx }
        assertTrue(authorless["createdByUserId"] == null || authorless["createdByUserId"].isNull)

        mvc.perform(get("/api/transactions").param("createdBy", anna.user.id.toString()).with(asUser(boris)))
            .andExpect(jsonPath("$.totalElements").value(0))
    }
}
