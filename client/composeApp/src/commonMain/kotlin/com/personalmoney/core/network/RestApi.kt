package com.personalmoney.core.network

import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/**
 * Per-entity REST access, for the client that cannot sync.
 *
 * The phone syncs and reads from Room; a browser tab has neither, so it reads and writes
 * these endpoints directly. Both sit on the same [HttpClient], so bearer auth and token
 * refresh behave identically.
 *
 * Mirrors the surface the React client uses today
 * (HomeBudgetingApp/src/main/frontend/src/api/index.ts), which is the contract the server
 * is already known to satisfy.
 */
class RestApi(private val client: HttpClient) {

    // ── accounts ─────────────────────────────────────────────────────────────
    suspend fun accounts(): List<RestAccount> = client.get("api/accounts").body()

    suspend fun account(id: Long): RestAccount = client.get("api/accounts/$id").body()

    suspend fun createAccount(body: CreateAccountRequest): RestAccount =
        client.post("api/accounts") { jsonBody(body) }.body()

    suspend fun updateAccount(id: Long, body: UpdateAccountRequest): RestAccount =
        client.put("api/accounts/$id") { jsonBody(body) }.body()

    suspend fun deleteAccount(id: Long) { client.delete("api/accounts/$id") }

    // ── categories ───────────────────────────────────────────────────────────
    suspend fun categories(type: CategoryType? = null): List<RestCategory> =
        client.get("api/categories") { if (type != null) parameter("type", type.name) }.body()

    suspend fun createCategory(body: CreateCategoryRequest): RestCategory =
        client.post("api/categories") { jsonBody(body) }.body()

    suspend fun updateCategory(id: Long, body: CreateCategoryRequest): RestCategory =
        client.put("api/categories/$id") { jsonBody(body) }.body()

    suspend fun deleteCategory(id: Long) { client.delete("api/categories/$id") }

    // ── transactions ─────────────────────────────────────────────────────────
    suspend fun transactions(
        accountId: Long? = null,
        categoryId: Long? = null,
        type: TransactionType? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        page: Int = 0,
        size: Int = 50,
    ): PagedResponse<RestTransaction> = client.get("api/transactions") {
        accountId?.let { parameter("accountId", it) }
        categoryId?.let { parameter("categoryId", it) }
        type?.let { parameter("type", it.name) }
        dateFrom?.let { parameter("dateFrom", it) }
        dateTo?.let { parameter("dateTo", it) }
        parameter("page", page)
        parameter("size", size)
    }.body()

    suspend fun createTransaction(body: CreateTransactionRequest): RestTransaction =
        client.post("api/transactions") { jsonBody(body) }.body()

    suspend fun updateTransaction(id: Long, body: CreateTransactionRequest): RestTransaction =
        client.put("api/transactions/$id") { jsonBody(body) }.body()

    suspend fun deleteTransaction(id: Long) { client.delete("api/transactions/$id") }

    // ── transfers ────────────────────────────────────────────────────────────
    suspend fun transfers(page: Int = 0, size: Int = 20): PagedResponse<RestTransfer> =
        client.get("api/transfers") {
            parameter("page", page)
            parameter("size", size)
        }.body()

    suspend fun createTransfer(body: CreateTransferRequest): RestTransfer =
        client.post("api/transfers") { jsonBody(body) }.body()

    suspend fun deleteTransfer(id: Long) { client.delete("api/transfers/$id") }

    // ── subscriptions ────────────────────────────────────────────────────────
    suspend fun subscriptions(): List<RestSubscription> =
        client.get("api/subscriptions").body()

    suspend fun subscription(id: Long): RestSubscription =
        client.get("api/subscriptions/$id").body()

    suspend fun createSubscription(body: CreateSubscriptionRequest): RestSubscription =
        client.post("api/subscriptions") { jsonBody(body) }.body()

    suspend fun updateSubscription(id: Long, body: UpdateSubscriptionRequest): RestSubscription =
        client.put("api/subscriptions/$id") { jsonBody(body) }.body()

    suspend fun deleteSubscription(id: Long) { client.delete("api/subscriptions/$id") }

    // ── banks ────────────────────────────────────────────────────────────────
    /**
     * Bank connections. The only part of the API that talks to a third party on the user's
     * behalf - see BankDtos for why credentials only ever travel upwards.
     */
    suspend fun bankProviders(): List<RestBankProvider> =
        client.get("api/bank/providers").body()

    suspend fun bankConnections(): List<RestBankConnection> =
        client.get("api/bank/connections").body()

    suspend fun connectBank(body: CreateConnectionRequest): RestBankConnection =
        client.post("api/bank/connections") { jsonBody(body) }.body()

    /** Re-checks stored credentials against the bank, and refreshes the connection status. */
    suspend fun verifyBank(id: Long): RestBankConnection =
        client.post("api/bank/connections/$id/verify").body()

    suspend fun updateBankCredentials(id: Long, body: UpdateCredentialsRequest): RestBankConnection =
        client.put("api/bank/connections/$id/credentials") { jsonBody(body) }.body()

    suspend fun disconnectBank(id: Long) { client.delete("api/bank/connections/$id") }

    suspend fun remoteAccounts(connectionId: Long): List<RestRemoteAccount> =
        client.get("api/bank/connections/$connectionId/accounts").body()

    suspend fun linkAccount(connectionId: Long, accountId: Long, body: LinkAccountRequest) {
        client.post("api/bank/connections/$connectionId/accounts/$accountId/link") { jsonBody(body) }
    }

    suspend fun unlinkAccount(accountId: Long) { client.delete("api/bank/accounts/$accountId/link") }

    /** Reads the statement without writing anything - the user confirms afterwards. */
    suspend fun previewBankImport(body: ImportPreviewRequest): List<RestPendingImport> =
        client.post("api/bank/import/preview") { jsonBody(body) }.body()

    suspend fun confirmBankImport(body: ImportConfirmRequest): RestImportResult =
        client.post("api/bank/import/confirm") { jsonBody(body) }.body()

    // ── csv import ───────────────────────────────────────────────────────────
    /**
     * Reads a statement file without importing it.
     *
     * The mapping travels as query parameters rather than in the body because the body is
     * the file - that is the server's shape, not a choice made here. Sending only some of
     * the columns is pointless: the server honours a mapping only when both `date` and
     * `description` are present, and otherwise re-detects and returns its own guess.
     */
    suspend fun previewCsv(
        accountId: Long,
        fileName: String,
        bytes: ByteArray,
        mapping: RestCsvMapping? = null,
    ): RestCsvPreview = client.post("api/import/csv/preview") {
        parameter("accountId", accountId)
        mapping?.let {
            parameter("date", it.date)
            parameter("description", it.description)
            it.amount?.let { v -> parameter("amount", v) }
            it.debit?.let { v -> parameter("debit", v) }
            it.credit?.let { v -> parameter("credit", v) }
            it.currency?.let { v -> parameter("currency", v) }
            it.dateFormat?.let { v -> parameter("dateFormat", v) }
        }
        setBody(
            MultiPartFormDataContent(
                formData {
                    append(
                        "file",
                        bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, "text/csv")
                            // The server reads the part by name, but a filename is what
                            // makes Spring treat this as an uploaded file at all.
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        },
                    )
                }
            )
        )
    }.body()

    suspend fun confirmCsv(body: CsvConfirmRequest): RestImportResult =
        client.post("api/import/csv/confirm") { jsonBody(body) }.body()

    // ── fx ───────────────────────────────────────────────────────────────────
    /**
     * The rates the server refreshed overnight. Read-only by design: rates are per-install,
     * not per-client, so nothing here writes one.
     */
    suspend fun fxRates(): List<RestFxRate> = client.get("api/fx/rates").body()

    // ── currencies ───────────────────────────────────────────────────────────
    suspend fun currencies(): List<RestCurrency> = client.get("api/currencies").body()

    suspend fun setBaseCurrency(currencyId: Long) {
        client.put("api/users/me/base-currency") { parameter("currencyId", currencyId) }
    }
}

private fun io.ktor.client.request.HttpRequestBuilder.jsonBody(body: Any) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
