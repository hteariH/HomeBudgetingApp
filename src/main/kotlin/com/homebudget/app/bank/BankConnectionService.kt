package com.homebudget.app.bank

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.*
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.BankConnectionRepository
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.security.SecurityUtils
import com.homebudget.app.service.FxService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Bank connections and the import that runs off them.
 *
 * Everything here is provider-agnostic: it looks a [BankProvider] up by id and never
 * mentions a specific bank. That is what keeps adding a bank down to one class.
 */
@Service
@Transactional
class BankConnectionService(
    private val providers: List<BankProvider>,
    private val connectionRepo: BankConnectionRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionRepo: TransactionRepository,
    private val securityUtils: SecurityUtils,
    private val fxService: FxService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val byId: Map<String, BankProvider> by lazy { providers.associateBy { it.id } }

    fun availableProviders(): List<BankProvider> = providers.sortedBy { it.displayName }

    fun provider(id: String): BankProvider =
        byId[id] ?: throw NoSuchElementException("Unknown bank provider: $id")

    /**
     * The bank connections, which is not quite all of them.
     *
     * `bank_connections` also stores the HomeMoney importer's OAuth tokens - same secret,
     * same encryption, same status model, no second table. But HomeMoney has no
     * [BankProvider] bean, so a row for it reaching [BankConnectionResponse] would blow up
     * in [provider] and take the whole bank screen down with it. It is filtered here
     * rather than at the controller so every caller gets the same answer.
     */
    fun findAll(): List<BankConnection> = connectionRepo.findAllByUser(securityUtils.getCurrentUser())
        .filter { byId.containsKey(it.provider) }

    fun findById(id: Long): BankConnection =
        connectionRepo.findByIdAndUser(id, securityUtils.getCurrentUser())
            ?: throw NoSuchElementException("Bank connection not found: $id")

    /**
     * Verifies the credentials against the bank *before* storing them.
     *
     * This is the whole point of the endpoint: a mistyped token fails in front of the
     * user, rather than being saved as a connection that quietly never works.
     */
    fun create(providerId: String, credentials: Map<String, String>): BankConnection {
        val provider = provider(providerId)
        requireRequiredFields(provider, credentials)

        val identity = provider.verify(credentials)
        return connectionRepo.save(
            BankConnection(
                user = securityUtils.getCurrentUser(),
                provider = provider.id,
                credentials = objectMapper.writeValueAsString(credentials),
                label = identity.displayName,
                status = BankConnectionStatus.ACTIVE,
                lastVerifiedAt = LocalDateTime.now(clock)
            )
        )
    }

    /** Re-checks a stored connection, e.g. after the bank starts rejecting it. */
    fun reverify(id: Long): BankConnection {
        val connection = findById(id)
        val provider = provider(connection.provider)
        return try {
            val identity = provider.verify(credentialsOf(connection))
            connection.label = identity.displayName
            connection.status = BankConnectionStatus.ACTIVE
            connection.lastVerifiedAt = LocalDateTime.now(clock)
            connectionRepo.save(connection)
        } catch (e: BankAuthException) {
            connection.status = BankConnectionStatus.INVALID
            connectionRepo.save(connection)
            throw e
        }
    }

    fun replaceCredentials(id: Long, credentials: Map<String, String>): BankConnection {
        val connection = findById(id)
        val provider = provider(connection.provider)
        requireRequiredFields(provider, credentials)

        val identity = provider.verify(credentials)
        connection.credentials = objectMapper.writeValueAsString(credentials)
        connection.label = identity.displayName
        connection.status = BankConnectionStatus.ACTIVE
        connection.lastVerifiedAt = LocalDateTime.now(clock)
        connection.updatedAt = LocalDateTime.now(clock)
        return connectionRepo.save(connection)
    }

    fun delete(id: Long) {
        val connection = findById(id)
        // Unlink first: the accounts and their history stay, they simply stop being fed.
        accountRepo.unlinkConnection(connection.id)
        connectionRepo.delete(connection)
    }

    fun remoteAccounts(id: Long): List<RemoteAccount> {
        val connection = findById(id)
        return runProviderCall(connection) { provider(connection.provider).listAccounts(credentialsOf(connection)) }
    }

    /** Points a local account at one of the bank's accounts. */
    fun linkAccount(connectionId: Long, accountId: Long, externalAccountId: String): Account {
        val connection = findById(connectionId)
        val account = accountRepo.findByIdAndUser(accountId, securityUtils.getCurrentUser())
            ?: throw NoSuchElementException("Account not found: $accountId")
        account.bankConnection = connection
        account.externalAccountId = externalAccountId
        account.updatedAt = Instant.now(clock)
        return accountRepo.save(account)
    }

    fun unlinkAccount(accountId: Long): Account {
        val account = accountRepo.findByIdAndUser(accountId, securityUtils.getCurrentUser())
            ?: throw NoSuchElementException("Account not found: $accountId")
        account.bankConnection = null
        account.externalAccountId = null
        account.updatedAt = Instant.now(clock)
        return accountRepo.save(account)
    }

    // ── Import ──────────────────────────────────────────────────────────────

    data class PendingImport(
        val externalId: String,
        val occurredAt: Instant,
        val description: String,
        val amountMinor: Long,
        val type: TransactionType,
        val mcc: Int,
        val alreadyImported: Boolean,
        val suggestedCategoryId: Long?
    )

    /**
     * What an import would bring in, without writing anything.
     *
     * Kept as a deliberate two-step - preview then confirm - because the user assigns
     * categories in between, and because a statement request is rate limited enough
     * that doing it twice by accident is painful.
     */
    fun preview(accountId: Long, from: LocalDate, to: LocalDate): List<PendingImport> {
        val user = securityUtils.getCurrentUser()
        val account = linkedAccount(accountId)
        val connection = account.bankConnection!!

        val remote = runProviderCall(connection) {
            provider(connection.provider).fetchStatement(
                credentialsOf(connection), account.externalAccountId!!, from, to
            )
        }

        return remote.map { tx ->
            val categoryType =
                if (tx.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
            PendingImport(
                externalId = tx.externalId,
                occurredAt = tx.occurredAt,
                description = tx.description,
                amountMinor = tx.amountMinor,
                type = tx.type,
                mcc = tx.mcc,
                alreadyImported = transactionRepo.existsByAccountAndExternalId(account, tx.externalId),
                suggestedCategoryId = if (tx.mcc != 0) {
                    categoryRepo.findByUserAndMccCodeAndType(user, tx.mcc, categoryType).firstOrNull()?.id
                } else null
            )
        }
    }

    data class ImportResult(val imported: Int, val skipped: Int)

    /** One normalised operation, whatever produced it. */
    data class ImportableRow(
        val externalId: String,
        val occurredAt: Instant,
        val description: String,
        /** Positive magnitude, in hundredths, as banks and CSV exports both report it. */
        val amountMinor: Long,
        val type: TransactionType,
        val mcc: Int = 0
    )

    /**
     * Writes normalised rows into an account, skipping anything already imported.
     *
     * Shared by the bank APIs and the CSV importer so the rules that matter - dedup by
     * external id, category-type agreement, currency rescaling, MCC learning - exist
     * once rather than once per source.
     *
     * [transferAccountByExternalId] names, per row, the *other* account the money moved
     * to or from. Such a row becomes a transfer pair instead of a categorised income or
     * expense: a statement's "moved to savings" line is not spending, and counting it as
     * spending is what makes an imported month look wrong. A row named there is imported
     * as a transfer whatever [categoryByExternalId] says about it.
     */
    fun importRows(
        accountId: Long,
        rows: List<ImportableRow>,
        categoryByExternalId: Map<String, Long?>,
        transferAccountByExternalId: Map<String, Long?> = emptyMap()
    ): ImportResult {
        val user = securityUtils.getCurrentUser()
        val account = accountRepo.findByIdAndUser(accountId, user)
            ?: throw NoSuchElementException("Account not found: $accountId")
        val decimals = account.currency.decimals

        var imported = 0
        var skipped = 0

        for (row in rows) {
            val counterpartId = transferAccountByExternalId[row.externalId]
            // The maps are the selection as well as the choice: a row named by neither was
            // never in front of the user, so it stays out of the ledger.
            if (counterpartId == null && !categoryByExternalId.containsKey(row.externalId)) continue
            if (transactionRepo.existsByAccountAndExternalId(account, row.externalId)) { skipped++; continue }

            if (counterpartId != null) {
                val counterpart = accountRepo.findByIdAndUser(counterpartId, user)
                // A counterpart that is gone, or is the account itself, would make a
                // transfer with nowhere to go rather than a ledger row worth keeping.
                if (counterpart == null || counterpart.id == account.id) { skipped++; continue }
                saveTransferPair(user, account, counterpart, row)
                imported++
                continue
            }

            val category = categoryByExternalId[row.externalId]?.let { categoryRepo.findByIdAndUser(it, user) }
            val expectedType =
                if (row.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
            if (category != null && category.type != expectedType) { skipped++; continue }

            val amountMinor = rescale(row.amountMinor, decimals)
            transactionRepo.save(
                Transaction(
                    user = user,
                    account = account,
                    category = category,
                    amountMinor = amountMinor,
                    currencyCode = account.currency.code,
                    accountAmountMinor = amountMinor,
                    type = row.type,
                    occurredAt = row.occurredAt,
                    description = row.description,
                    externalId = row.externalId
                )
            )
            if (row.mcc != 0 && category != null && !category.mccCodes.contains(row.mcc)) {
                category.mccCodes.add(row.mcc)
                categoryRepo.save(category)
            }
            imported++
        }
        return ImportResult(imported, skipped)
    }

    fun confirm(
        accountId: Long,
        from: LocalDate,
        to: LocalDate,
        categoryByExternalId: Map<String, Long?>,
        transferAccountByExternalId: Map<String, Long?> = emptyMap()
    ): ImportResult {
        if (categoryByExternalId.isEmpty() && transferAccountByExternalId.isEmpty()) return ImportResult(0, 0)

        val account = linkedAccount(accountId)
        val connection = account.bankConnection!!

        // Re-fetched rather than trusting rows echoed by the client: the bank is the
        // authority on what actually happened.
        val remote = runProviderCall(connection) {
            provider(connection.provider).fetchStatement(
                credentialsOf(connection), account.externalAccountId!!, from, to
            )
        }

        val result = importRows(
            accountId,
            remote.map {
                ImportableRow(it.externalId, it.occurredAt, it.description, it.amountMinor, it.type, it.mcc)
            },
            categoryByExternalId,
            transferAccountByExternalId
        )
        log.info("Imported {} and skipped {} from {}", result.imported, result.skipped, connection.provider)
        return result
    }

    // ── Internals ───────────────────────────────────────────────────────────

    /**
     * Writes an imported row as the two rows a transfer is made of - see [Transaction].
     *
     * The direction comes from the row itself: money leaving the statement's account went
     * *to* the counterpart, money arriving came *from* it. Across currencies the far leg
     * is converted at the stored rate, the same one the clients convert with, so an
     * imported cross-currency transfer lands with what the other account received rather
     * than the source figure repeated.
     */
    private fun saveTransferPair(
        user: User,
        account: Account,
        counterpart: Account,
        row: ImportableRow
    ) {
        val nearDecimals = account.currency.decimals
        val farDecimals = counterpart.currency.decimals
        val nearMinor = rescale(row.amountMinor, nearDecimals)
        val rate = fxService.rate(user, account.currency.code, counterpart.currency.code) ?: BigDecimal.ONE
        val farMinor = MinorUnits.toMinor(
            MinorUnits.toDecimal(nearMinor, nearDecimals).multiply(rate),
            farDecimals
        )

        val incoming = row.type == TransactionType.INCOME
        val groupId = UUID.randomUUID().toString()

        // The credit leg carries the from-to rate, which is where a TransferResponse reads
        // the exchange rate it shows.
        val debitMajor = MinorUnits.toDecimal(
            if (incoming) farMinor else nearMinor,
            if (incoming) farDecimals else nearDecimals
        )
        val creditMajor = MinorUnits.toDecimal(
            if (incoming) nearMinor else farMinor,
            if (incoming) nearDecimals else farDecimals
        )
        val creditRate = if (debitMajor.signum() != 0) {
            creditMajor.divide(debitMajor, 10, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ONE
        }

        val near = Transaction(
            user = user,
            account = account,
            category = null,
            // Signed: the balance query adds TRANSFER rows as-is.
            amountMinor = if (incoming) nearMinor else -nearMinor,
            currencyCode = account.currency.code,
            accountAmountMinor = if (incoming) nearMinor else -nearMinor,
            fxRateToAccount = if (incoming) creditRate else BigDecimal.ONE,
            type = TransactionType.TRANSFER,
            occurredAt = row.occurredAt,
            description = row.description,
            transferGroupId = groupId,
            transferCounterpartAccountId = counterpart.id,
            // Only the imported side carries it: it is that statement's id, and the far
            // account has a statement of its own where this movement has another.
            externalId = row.externalId
        )
        val far = Transaction(
            user = user,
            account = counterpart,
            category = null,
            amountMinor = if (incoming) -farMinor else farMinor,
            currencyCode = counterpart.currency.code,
            accountAmountMinor = if (incoming) -farMinor else farMinor,
            fxRateToAccount = if (incoming) BigDecimal.ONE else creditRate,
            type = TransactionType.TRANSFER,
            occurredAt = row.occurredAt,
            description = row.description,
            transferGroupId = groupId,
            transferCounterpartAccountId = account.id
        )

        // Both legs in one save: a half-written transfer is a corrupted balance.
        transactionRepo.saveAll(listOf(near, far))
    }

    private fun linkedAccount(accountId: Long): Account {
        val account = accountRepo.findByIdAndUser(accountId, securityUtils.getCurrentUser())
            ?: throw NoSuchElementException("Account not found: $accountId")
        require(account.bankConnection != null && account.externalAccountId != null) {
            "Account '${account.name}' is not linked to a bank"
        }
        return account
    }

    @Suppress("UNCHECKED_CAST")
    private fun credentialsOf(connection: BankConnection): Map<String, String> =
        objectMapper.readValue(connection.credentials, Map::class.java) as Map<String, String>

    /** Marks the connection invalid when the bank rejects it, so the UI can say so. */
    private fun <T> runProviderCall(connection: BankConnection, block: () -> T): T = try {
        block()
    } catch (e: BankAuthException) {
        connection.status = BankConnectionStatus.INVALID
        connectionRepo.save(connection)
        throw e
    }

    private fun requireRequiredFields(provider: BankProvider, credentials: Map<String, String>) {
        val missing = provider.credentialFields
            .filter { it.required && credentials[it.name].isNullOrBlank() }
            .map { it.label }
        require(missing.isEmpty()) { "Missing: ${missing.joinToString(", ")}" }
    }

    private fun rescale(amountInHundredths: Long, decimals: Int): Long = when {
        decimals == 2 -> amountInHundredths
        decimals > 2 -> amountInHundredths * pow10(decimals - 2)
        else -> Math.round(amountInHundredths.toDouble() / pow10(2 - decimals))
    }

    private fun pow10(n: Int): Long {
        var result = 1L
        repeat(n) { result *= 10 }
        return result
    }
}
