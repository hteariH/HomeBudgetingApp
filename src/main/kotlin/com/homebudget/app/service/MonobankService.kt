package com.homebudget.app.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.*
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private const val MONO_API = "https://api.monobank.ua"

@JsonIgnoreProperties(ignoreUnknown = true)
data class MonoAccount(
    val id: String = "",
    val currencyCode: Int = 980,
    val balance: Long = 0,
    val creditLimit: Long = 0,
    val type: String = "black",
    val maskedPan: List<String> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MonoClientInfo(
    val clientId: String = "",
    val name: String = "",
    val accounts: List<MonoAccount> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MonoStatement(
    val id: String = "",
    val time: Long = 0,
    val description: String = "",
    val mcc: Int = 0,
    val amount: Long = 0,
    val operationAmount: Long = 0,
    val currencyCode: Int = 980,
    val balance: Long = 0
)

@Service
class MonobankService(
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionRepo: TransactionRepository,
    private val securityUtils: SecurityUtils,
    private val objectMapper: ObjectMapper
) {
    private val httpClient = HttpClient.newHttpClient()

    fun getClientInfo(token: String): MonobankClientInfo {
        val body = monoGet("/personal/client-info", token)
        val info = objectMapper.readValue(body, MonoClientInfo::class.java)
        return MonobankClientInfo(
            clientId = info.clientId,
            name = info.name,
            accounts = info.accounts.map {
                MonobankAccountInfo(
                    id = it.id,
                    currencyCode = it.currencyCode,
                    balance = it.balance,
                    creditLimit = it.creditLimit,
                    type = it.type,
                    maskedPan = it.maskedPan
                )
            }
        )
    }

    @Transactional
    fun linkAccount(accountId: Long, req: LinkMonobankRequest) {
        val user = securityUtils.getCurrentUser()
        val account = accountRepo.findByIdAndUser(accountId, user)
            ?: throw NoSuchElementException("Account not found: $accountId")
        account.monobankToken = req.token
        account.monobankAccountId = req.monobankAccountId
        accountRepo.save(account)
    }

    @Transactional
    fun unlinkAccount(accountId: Long) {
        val user = securityUtils.getCurrentUser()
        val account = accountRepo.findByIdAndUser(accountId, user)
            ?: throw NoSuchElementException("Account not found: $accountId")
        account.monobankToken = null
        account.monobankAccountId = null
        accountRepo.save(account)
    }

    fun previewTransactions(req: MonobankPreviewRequest): MonobankPreviewResponse {
        val user = securityUtils.getCurrentUser()
        val account = accountRepo.findByIdAndUser(req.accountId, user)
            ?: throw NoSuchElementException("Account not found: ${req.accountId}")
        val token = account.monobankToken
            ?: throw IllegalStateException("Account is not linked to Monobank")
        val monobankAccountId = account.monobankAccountId
            ?: throw IllegalStateException("Account is not linked to Monobank")

        val body = monoGet("/personal/statement/$monobankAccountId/${req.dateFrom}/${req.dateTo}", token)
        val statements = objectMapper.readValue(
            body,
            objectMapper.typeFactory.constructCollectionType(List::class.java, MonoStatement::class.java)
        ) as List<MonoStatement>

        val pending = statements.map { stmt ->
            val amount = BigDecimal.valueOf(Math.abs(stmt.amount)).divide(BigDecimal("100"))
            val type = if (stmt.amount < 0) TransactionType.EXPENSE else TransactionType.INCOME
            val date = Instant.ofEpochSecond(stmt.time).atZone(ZoneId.systemDefault()).toLocalDate()
            val existing = transactionRepo.findByAccountAndMonobankId(account, stmt.id)
            val suggestedCategoryId = if (stmt.mcc != 0)
                categoryRepo.findByUserAndMccCode(user, stmt.mcc).firstOrNull()?.id
            else null
            MonobankPendingTransaction(
                monobankId = stmt.id,
                date = date,
                description = stmt.description.ifBlank { "—" },
                amount = amount,
                type = type,
                mcc = stmt.mcc,
                alreadyExists = existing != null,
                existingTransactionId = existing?.id,
                suggestedCategoryId = suggestedCategoryId
            )
        }

        return MonobankPreviewResponse(transactions = pending)
    }

    @Transactional
    fun confirmImport(req: MonobankConfirmRequest): MonobankImportResult {
        val user = securityUtils.getCurrentUser()
        val account = accountRepo.findByIdAndUser(req.accountId, user)
            ?: throw NoSuchElementException("Account not found: ${req.accountId}")
        val token = account.monobankToken
            ?: throw IllegalStateException("Account is not linked to Monobank")
        val monobankAccountId = account.monobankAccountId
            ?: throw IllegalStateException("Account is not linked to Monobank")

        // Build a map of monobankId -> categoryId from the confirmed items
        val confirmMap = req.items.associate { it.monobankId to it.categoryId }
        if (confirmMap.isEmpty()) return MonobankImportResult(imported = 0, skipped = 0)

        // Re-fetch the same statement to get raw transaction data
        val body = monoGet("/personal/statement/$monobankAccountId/${req.dateFrom}/${req.dateTo}", token)
        val statements = objectMapper.readValue(
            body,
            objectMapper.typeFactory.constructCollectionType(List::class.java, MonoStatement::class.java)
        ) as List<MonoStatement>

        var imported = 0
        var skipped = 0

        for (stmt in statements) {
            val categoryId = confirmMap[stmt.id] ?: continue   // not in confirmed list → skip
            if (transactionRepo.existsByAccountAndMonobankId(account, stmt.id)) { skipped++; continue }

            val category = categoryRepo.findById(categoryId).orElseThrow {
                NoSuchElementException("Category not found: $categoryId")
            }
            val amount = BigDecimal.valueOf(Math.abs(stmt.amount)).divide(BigDecimal("100"))
            val type = if (stmt.amount < 0) TransactionType.EXPENSE else TransactionType.INCOME
            val date = Instant.ofEpochSecond(stmt.time).atZone(ZoneId.systemDefault()).toLocalDate()

            transactionRepo.save(
                Transaction(
                    user = user, account = account, category = category,
                    amount = amount, type = type, date = date,
                    description = stmt.description.ifBlank { null },
                    monobankId = stmt.id
                )
            )

            // Learn MCC → category mapping
            if (stmt.mcc != 0 && !category.mccCodes.contains(stmt.mcc)) {
                category.mccCodes.add(stmt.mcc)
                categoryRepo.save(category)
            }

            val delta = if (type == TransactionType.INCOME) amount else amount.negate()
            account.balance = account.balance.add(delta)
            imported++
        }

        account.updatedAt = LocalDateTime.now()
        accountRepo.save(account)

        return MonobankImportResult(imported = imported, skipped = skipped)
    }

    private fun monoGet(path: String, token: String): String {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$MONO_API$path"))
            .header("X-Token", token)
            .GET()
            .build()
        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            throw IllegalStateException("Monobank API error ${resp.statusCode()}: ${resp.body()}")
        }
        return resp.body()
    }
}
