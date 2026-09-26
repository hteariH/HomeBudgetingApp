package com.homebudget.app.bank

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.BankConnectionRepository
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/bank/monobank/webhook")
class MonobankWebhookController(
    private val connectionRepo: BankConnectionRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionRepo: TransactionRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{token}")
    @Transactional
    fun handleWebhook(
        @PathVariable token: String,
        @RequestBody(required = false) body: String?
    ): ResponseEntity<Map<String, Any>> {
        if (body.isNullOrBlank()) {
            return ResponseEntity.ok(mapOf("status" to "ok"))
        }

        return try {
            val root: JsonNode = objectMapper.readTree(body)

            // Monobank ping/registration verification
            val type = root.path("type").asText("")
            if (type == "Webhook" || !root.has("data") && !root.has("statementItem")) {
                return ResponseEntity.ok(mapOf("status" to "ok"))
            }

            val dataNode = if (root.has("data")) root.path("data") else root
            val itemNode = if (dataNode.has("statementItem")) dataNode.path("statementItem") else dataNode
            val stmtId = itemNode.path("id").asText("")
            if (stmtId.isBlank()) {
                return ResponseEntity.ok(mapOf("status" to "ok", "message" to "No statement item"))
            }

            // Find bank connection with this token
            val connections = connectionRepo.findAll().filter { it.provider == "MONOBANK" }
            val connection = connections.firstOrNull { conn ->
                try {
                    val creds = objectMapper.readTree(conn.credentials)
                    creds.path("token").asText("").trim() == token.trim()
                } catch (e: Exception) {
                    conn.credentials.contains(token.trim())
                }
            } ?: run {
                log.warn("Monobank webhook called with unknown token: {}", token)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Invalid token"))
            }

            val rawAccountId = dataNode.path("account").asText("")
            val userAccounts = accountRepo.findAllByUser(connection.user)
                .filter { it.bankConnection?.id == connection.id }

            val targetAccount = (if (rawAccountId.isNotBlank()) {
                userAccounts.firstOrNull { it.externalAccountId == rawAccountId }
            } else null) ?: userAccounts.firstOrNull()

            if (targetAccount == null) {
                log.warn("No linked account found for Monobank connection {}", connection.id)
                return ResponseEntity.ok(mapOf("status" to "ok", "skipped" to "no_linked_account"))
            }

            val externalId = "monobank:$stmtId"
            if (transactionRepo.existsByAccountAndExternalId(targetAccount, externalId)) {
                log.info("Duplicate Monobank transaction {} skipped", externalId)
                return ResponseEntity.ok(mapOf("status" to "ok", "skipped" to "duplicate"))
            }

            val rawAmount = itemNode.path("amount").asLong(0)
            val time = itemNode.path("time").asLong(Instant.now().epochSecond)
            val description = itemNode.path("description").asText("")
            val mcc = itemNode.path("mcc").asInt(0)

            val decimals = targetAccount.currency.decimals
            val amountMinor = MinorUnits.rescale(Math.abs(rawAmount), decimals)
            val txType = if (rawAmount < 0) TransactionType.EXPENSE else TransactionType.INCOME

            // Match category by learned MCC codes
            val category = if (mcc != 0) {
                categoryRepo.findAllByUser(connection.user).firstOrNull { it.mccCodes.contains(mcc) }
            } else null

            val tx = transactionRepo.save(
                Transaction(
                    user = connection.user,
                    account = targetAccount,
                    category = category,
                    amountMinor = amountMinor,
                    currencyCode = targetAccount.currency.code,
                    accountAmountMinor = amountMinor,
                    type = txType,
                    occurredAt = Instant.ofEpochSecond(time),
                    description = description,
                    externalId = externalId,
                    updatedAt = Instant.now()
                )
            )

            log.info("Monobank webhook successfully recorded transaction {} for account {}", tx.id, targetAccount.id)
            ResponseEntity.ok(mapOf("status" to "ok", "imported" to 1, "transactionId" to tx.id))
        } catch (e: Exception) {
            log.error("Error processing Monobank webhook payload", e)
            // Return 200 OK so webhook endpoint remains healthy on Monobank's side
            ResponseEntity.ok(mapOf("status" to "error", "message" to (e.message ?: "error")))
        }
    }
}
