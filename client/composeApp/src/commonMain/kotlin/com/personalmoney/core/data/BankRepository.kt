package com.personalmoney.core.data

import com.personalmoney.core.domain.model.BankConnection
import com.personalmoney.core.domain.model.BankProvider
import com.personalmoney.core.domain.model.ConsentChallenge
import com.personalmoney.core.domain.model.ImportResult
import com.personalmoney.core.domain.model.PendingImport
import com.personalmoney.core.domain.model.RemoteAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Bank connections and statement import.
 *
 * The odd one out among the repositories here. The others front a store the app owns -
 * Room on the phone, the server's tables on the web - and are read as flows because the
 * store can change under a screen. This one fronts somebody else's bank, and most of what
 * it returns is a snapshot of a conversation: the accounts a token can see right now, the
 * statement lines in a date range. Those are suspend functions returning values, because
 * pretending they were observable would promise updates nobody is going to send.
 *
 * [observeConnections] is the exception, and is a flow for the usual reason: it is a list
 * the user edits, and every screen showing it should see an edit immediately.
 *
 * **Credentials only travel downstream to the server.** Nothing returned from here can
 * carry one - see `core.network.BankDtos`.
 *
 * Only the web graph binds an implementation today. The phone reads its ledger out of
 * Room and would need a sync pass before an import appeared, so it has no bank screen and
 * no binding; see `navigation.bankConnectionsAvailable`.
 */
interface BankRepository {

    /** The banks this server build can talk to. Fixed for the life of the page. */
    suspend fun providers(): List<BankProvider>

    fun observeConnections(): Flow<List<BankConnection>>

    /**
     * Checks the credentials with the bank and stores them only if they work, so a
     * mistyped token fails in front of the user instead of becoming a dead connection.
     */
    suspend fun connect(providerId: String, credentials: Map<String, String>)

    /**
     * Asks a consent bank for something the user can approve in its app.
     *
     * Nothing is connected yet when this returns - see [pollConsent].
     */
    suspend fun beginConsent(providerId: String): ConsentChallenge

    /**
     * True once the user has approved [reference], at which point the connection exists.
     *
     * Meant to be called on a timer: false is the ordinary answer while they are still
     * looking at their phone, not a failure.
     */
    suspend fun pollConsent(providerId: String, reference: String): Boolean

    /** Re-checks stored credentials, which is how a rejected connection gets its status back. */
    suspend fun verify(connectionId: Long)

    suspend fun updateCredentials(connectionId: Long, credentials: Map<String, String>)

    /** Imported transactions stay; only the credentials and the links go. */
    suspend fun disconnect(connectionId: Long)

    /** What the connection's credentials can see at the bank right now. */
    suspend fun remoteAccounts(connectionId: Long): List<RemoteAccount>

    suspend fun link(connectionId: Long, accountId: Long, externalAccountId: String)

    suspend fun unlink(accountId: Long)

    /**
     * What an import would bring in, without bringing it in.
     *
     * The two-step exists because a statement is somebody else's data: the user gets to
     * see every line, and categorise it, before any of it lands in their ledger.
     */
    suspend fun previewImport(accountId: Long, from: LocalDate, to: LocalDate): List<PendingImport>

    /**
     * Imports the range. [categories] maps [PendingImport.externalId] to a category, or to
     * null to import a line uncategorised.
     *
     * Takes the range rather than the previewed rows, because the server re-fetches the
     * statement from the bank rather than trusting amounts a client sent it. [categories]
     * is therefore the selection as well as the categorisation: a line the map does not
     * mention is left alone, which is how a row that arrived between the preview and the
     * confirm stays out of a ledger the user never saw it in.
     */
    suspend fun confirmImport(
        accountId: Long,
        from: LocalDate,
        to: LocalDate,
        categories: Map<String, Long?>,
        transfers: Map<String, Long?> = emptyMap(),
    ): ImportResult

    suspend fun refresh()
}
