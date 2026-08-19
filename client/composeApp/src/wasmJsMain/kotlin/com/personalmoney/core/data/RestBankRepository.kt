package com.personalmoney.core.data

import com.personalmoney.core.domain.model.BankConnection
import com.personalmoney.core.domain.model.BankConnectionStatus
import com.personalmoney.core.domain.model.BankProvider
import com.personalmoney.core.domain.model.CredentialField
import com.personalmoney.core.domain.model.ImportResult
import com.personalmoney.core.domain.model.PendingImport
import com.personalmoney.core.domain.model.RemoteAccount
import com.personalmoney.core.network.CreateConnectionRequest
import com.personalmoney.core.network.ImportConfirmRequest
import com.personalmoney.core.network.ImportPreviewRequest
import com.personalmoney.core.network.LinkAccountRequest
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.RestBankConnection
import com.personalmoney.core.network.RestBankProvider
import com.personalmoney.core.network.RestCredentialField
import com.personalmoney.core.network.RestPendingImport
import com.personalmoney.core.network.RestRemoteAccount
import com.personalmoney.core.network.UpdateCredentialsRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/**
 * The shared bank contract, backed by REST. See [RestAccountRepository].
 *
 * The only implementation there is. Bank credentials live on the server and are never sent
 * to a device, so there is nothing for a Room-backed one to hold - see [BankRepository].
 *
 * Every write refreshes the connection list, because most of them change something the
 * list shows: connecting adds one, verifying clears a rejected status, linking moves an
 * account into [BankConnection.linkedAccountIds] and so into the import picker.
 */
class RestBankRepository(private val api: RestApi) : BankRepository {

    private val cache = MutableStateFlow<List<BankConnection>>(emptyList())

    /**
     * See [LazyRefresh]. The screens are written against Room's guarantee - collect and
     * rows arrive - and a REST cache is empty until something asks the server.
     */
    private val lazyLoad = LazyRefresh { refresh() }

    override suspend fun providers(): List<BankProvider> =
        api.bankProviders().map { it.toProvider() }

    override fun observeConnections(): Flow<List<BankConnection>> = cache.loadingOnce(lazyLoad)

    override suspend fun connect(providerId: String, credentials: Map<String, String>) {
        api.connectBank(CreateConnectionRequest(provider = providerId, credentials = credentials))
        refresh()
    }

    override suspend fun verify(connectionId: Long) {
        api.verifyBank(connectionId)
        refresh()
    }

    override suspend fun updateCredentials(connectionId: Long, credentials: Map<String, String>) {
        api.updateBankCredentials(connectionId, UpdateCredentialsRequest(credentials))
        refresh()
    }

    override suspend fun disconnect(connectionId: Long) {
        api.disconnectBank(connectionId)
        refresh()
    }

    override suspend fun remoteAccounts(connectionId: Long): List<RemoteAccount> =
        api.remoteAccounts(connectionId).map { it.toRemoteAccount() }

    override suspend fun link(connectionId: Long, accountId: Long, externalAccountId: String) {
        api.linkAccount(connectionId, accountId, LinkAccountRequest(externalAccountId))
        refresh()
    }

    override suspend fun unlink(accountId: Long) {
        api.unlinkAccount(accountId)
        refresh()
    }

    override suspend fun previewImport(
        accountId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<PendingImport> = api
        .previewBankImport(ImportPreviewRequest(accountId, from.toString(), to.toString()))
        .map { it.toPendingImport() }

    override suspend fun confirmImport(
        accountId: Long,
        from: LocalDate,
        to: LocalDate,
        categories: Map<String, Long?>,
    ): ImportResult {
        val result = api.confirmBankImport(
            ImportConfirmRequest(accountId, from.toString(), to.toString(), categories)
        )
        // Not refresh(): the connections are unchanged. What moved is the ledger, and the
        // transaction and account repositories own that.
        return ImportResult(imported = result.imported, skipped = result.skipped)
    }

    override suspend fun refresh() {
        cache.value = api.bankConnections().map { it.toConnection() }
    }
}

private fun RestBankProvider.toProvider() = BankProvider(
    id = id,
    displayName = displayName,
    helpUrl = helpUrl,
    credentialFields = credentialFields.map { it.toField() },
)

private fun RestCredentialField.toField() = CredentialField(
    name = name,
    label = label,
    // The server's `FieldType.SECRET`, decided here rather than in the form: the screen
    // should be asking "do I mask this", not comparing an enum name it does not own.
    secret = type.equals("SECRET", ignoreCase = true),
    placeholder = placeholder,
    helpText = helpText,
    required = required,
)

private fun RestBankConnection.toConnection() = BankConnection(
    id = id,
    provider = provider,
    providerDisplayName = providerDisplayName,
    label = label,
    // Anything the server grows later reads as ACTIVE; see BankConnectionStatus.
    status = if (status.equals("INVALID", ignoreCase = true)) {
        BankConnectionStatus.INVALID
    } else {
        BankConnectionStatus.ACTIVE
    },
    linkedAccountIds = linkedAccountIds,
)

private fun RestRemoteAccount.toRemoteAccount() = RemoteAccount(
    externalId = externalId,
    displayName = displayName,
    currencyCode = currencyCode,
    balanceMinor = balanceMinor,
    linkedAccountId = linkedAccountId,
)

private fun RestPendingImport.toPendingImport() = PendingImport(
    externalId = externalId,
    date = LocalDate.parse(date),
    description = description,
    amountMinor = amountMinor,
    type = type,
    alreadyImported = alreadyImported,
    suggestedCategoryId = suggestedCategoryId,
)
