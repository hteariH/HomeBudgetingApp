package com.homebudget.app.controller

import com.homebudget.app.bank.BankConnectionService
import com.homebudget.app.bank.BankProvider
import com.homebudget.app.domain.BankConnection
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.service.AccountService
import com.homebudget.app.service.toResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

// ── Wire types ──────────────────────────────────────────────────────────────

/**
 * A bank, plus a description of the form needed to connect to it.
 *
 * The fields are data rather than a per-bank screen: the client renders whatever it is
 * given, so adding a bank needs no front-end change at all.
 */
data class BankProviderResponse(
    val id: String,
    val displayName: String,
    val helpUrl: String?,
    val credentialFields: List<CredentialFieldResponse>
)

data class CredentialFieldResponse(
    val name: String,
    val label: String,
    val type: String,
    val placeholder: String?,
    val helpText: String?,
    val required: Boolean
)

data class BankConnectionResponse(
    val id: Long,
    val provider: String,
    val providerDisplayName: String,
    val label: String?,
    val status: String,
    val lastVerifiedAt: LocalDateTime?,
    val linkedAccountIds: List<Long>,
    val createdAt: LocalDateTime
)

data class CreateConnectionRequest(
    @field:NotBlank val provider: String,
    /** Keyed by [CredentialFieldResponse.name]. Never echoed back. */
    val credentials: Map<String, String> = emptyMap()
)

data class UpdateCredentialsRequest(
    val credentials: Map<String, String> = emptyMap()
)

data class RemoteAccountResponse(
    val externalId: String,
    val displayName: String,
    val currencyCode: String,
    val balanceMinor: Long,
    val type: String?,
    /** The local account already pointed at this one, if any. */
    val linkedAccountId: Long?
)

data class LinkAccountRequest(
    @field:NotBlank val externalAccountId: String
)

data class ImportPreviewRequest(
    val accountId: Long,
    val dateFrom: LocalDate,
    val dateTo: LocalDate
)

data class PendingImportResponse(
    val externalId: String,
    val occurredAt: Instant,
    val date: LocalDate,
    val description: String,
    val amountMinor: Long,
    val type: TransactionType,
    val mcc: Int,
    val alreadyImported: Boolean,
    val suggestedCategoryId: Long?
)

data class ImportConfirmRequest(
    val accountId: Long,
    val dateFrom: LocalDate,
    val dateTo: LocalDate,
    /** external transaction id → category id, or null to import uncategorised. */
    val categories: Map<String, Long?> = emptyMap(),
    /**
     * External transaction id to the *other* account the money moved to or from, for rows
     * the user marked as a transfer rather than as spending or income. Takes precedence
     * over [categories] for the same row.
     */
    val transfers: Map<String, Long?> = emptyMap()
)

data class ImportResultResponse(val imported: Int, val skipped: Int)

/**
 * Bank connections and statement import, for every provider.
 *
 * There is deliberately no per-bank endpoint: the provider is a path variable or a
 * field, so a new integration reuses all of this.
 */
@RestController
@RequestMapping("/api/bank")
class BankController(
    private val service: BankConnectionService,
    private val accountService: AccountService
) {

    @GetMapping("/providers")
    fun providers(): List<BankProviderResponse> = service.availableProviders().map { it.toResponse() }

    @GetMapping("/connections")
    fun connections(): List<BankConnectionResponse> = service.findAll().map { it.toResponse() }

    /** Credentials are checked against the bank before anything is stored. */
    @PostMapping("/connections")
    @ResponseStatus(HttpStatus.CREATED)
    fun connect(@Valid @RequestBody req: CreateConnectionRequest): BankConnectionResponse =
        service.create(req.provider, req.credentials).toResponse()

    @PostMapping("/connections/{id}/verify")
    fun verify(@PathVariable id: Long): BankConnectionResponse = service.reverify(id).toResponse()

    @PutMapping("/connections/{id}/credentials")
    fun updateCredentials(
        @PathVariable id: Long,
        @Valid @RequestBody req: UpdateCredentialsRequest
    ): BankConnectionResponse = service.replaceCredentials(id, req.credentials).toResponse()

    @DeleteMapping("/connections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun disconnect(@PathVariable id: Long) = service.delete(id)

    @GetMapping("/connections/{id}/accounts")
    fun remoteAccounts(@PathVariable id: Long): List<RemoteAccountResponse> {
        val linkedByExternalId = accountService.findAll()
            .filter { it.bankConnection?.id == id && it.externalAccountId != null }
            .associate { it.externalAccountId!! to it.id }
        return service.remoteAccounts(id).map {
            RemoteAccountResponse(
                externalId = it.externalId,
                displayName = it.displayName,
                currencyCode = it.currencyCode,
                balanceMinor = it.balanceMinor,
                type = it.type,
                linkedAccountId = linkedByExternalId[it.externalId]
            )
        }
    }

    @PostMapping("/connections/{id}/accounts/{accountId}/link")
    fun link(
        @PathVariable id: Long,
        @PathVariable accountId: Long,
        @Valid @RequestBody req: LinkAccountRequest
    ) = service.linkAccount(id, accountId, req.externalAccountId)
        .toResponse(accountService.balanceOf(accountId))

    @DeleteMapping("/accounts/{accountId}/link")
    fun unlink(@PathVariable accountId: Long) =
        service.unlinkAccount(accountId).toResponse(accountService.balanceOf(accountId))

    @PostMapping("/import/preview")
    fun preview(@RequestBody req: ImportPreviewRequest): List<PendingImportResponse> =
        service.preview(req.accountId, req.dateFrom, req.dateTo).map {
            PendingImportResponse(
                externalId = it.externalId,
                occurredAt = it.occurredAt,
                date = it.occurredAt.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                description = it.description,
                amountMinor = it.amountMinor,
                type = it.type,
                mcc = it.mcc,
                alreadyImported = it.alreadyImported,
                suggestedCategoryId = it.suggestedCategoryId
            )
        }

    @PostMapping("/import/confirm")
    fun confirm(@RequestBody req: ImportConfirmRequest): ImportResultResponse {
        val result = service.confirm(req.accountId, req.dateFrom, req.dateTo, req.categories, req.transfers)
        return ImportResultResponse(result.imported, result.skipped)
    }

    // ── Mapping ─────────────────────────────────────────────────────────────

    private fun BankProvider.toResponse() = BankProviderResponse(
        id = id,
        displayName = displayName,
        helpUrl = helpUrl,
        credentialFields = credentialFields.map {
            CredentialFieldResponse(it.name, it.label, it.type.name, it.placeholder, it.helpText, it.required)
        }
    )

    // Credentials are never included: they only ever travel towards the server.
    private fun BankConnection.toResponse() = BankConnectionResponse(
        id = id,
        provider = provider,
        providerDisplayName = service.provider(provider).displayName,
        label = label,
        status = status.name,
        lastVerifiedAt = lastVerifiedAt,
        linkedAccountIds = accountService.findAll()
            .filter { it.bankConnection?.id == id }
            .map { it.id },
        createdAt = createdAt
    )
}
