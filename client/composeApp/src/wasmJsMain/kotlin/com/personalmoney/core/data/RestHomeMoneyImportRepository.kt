package com.personalmoney.core.data

import com.personalmoney.core.domain.model.HomeMoneyAccountMapping
import com.personalmoney.core.domain.model.HomeMoneyAuthorization
import com.personalmoney.core.domain.model.HomeMoneyCategoryMapping
import com.personalmoney.core.domain.model.HomeMoneyConnection
import com.personalmoney.core.domain.model.HomeMoneyImportPlan
import com.personalmoney.core.domain.model.HomeMoneyImportProgress
import com.personalmoney.core.network.HomeMoneyApplyRequest
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.RestHomeMoneyConnection
import com.personalmoney.core.network.RestHomeMoneyPlan
import com.personalmoney.core.network.RestHomeMoneyStatus
import com.personalmoney.core.network.RestPlannedAccount
import com.personalmoney.core.network.RestPlannedCategory
import kotlinx.datetime.LocalDate

/**
 * The shared HomeMoney import contract, backed by REST. See [RestAccountRepository].
 *
 * No cache and no [LazyRefresh]: there is no list to keep warm here, only calls that each
 * answer once - and a progress reading that is meant to be stale the moment it arrives.
 */
class RestHomeMoneyImportRepository(private val api: RestApi) : HomeMoneyImportRepository {

    override suspend fun connection(): HomeMoneyConnection = api.homeMoneyConnection().toConnection()

    override suspend fun authorizationUrl(): HomeMoneyAuthorization =
        api.authorizeHomeMoney().let { HomeMoneyAuthorization(url = it.authorizationUrl, state = it.state) }

    override suspend fun completeAuthorization(code: String, state: String): HomeMoneyConnection =
        api.connectHomeMoneyWithCode(code, state).toConnection()

    override suspend fun disconnect() = api.disconnectHomeMoney()

    override suspend fun plan(): HomeMoneyImportPlan = api.homeMoneyPlan().toPlan()

    override suspend fun start(
        accounts: List<HomeMoneyAccountMapping>,
        categories: List<HomeMoneyCategoryMapping>,
    ): HomeMoneyImportProgress = api.applyHomeMoney(
        HomeMoneyApplyRequest(
            accounts = accounts.map { it.toDto() },
            categories = categories.map { it.toDto() },
        )
    ).toProgress()

    override suspend fun progress(): HomeMoneyImportProgress = api.homeMoneyStatus().toProgress()
}

private fun RestHomeMoneyConnection.toConnection() = HomeMoneyConnection(
    connected = connected,
    label = label,
    invalid = status == "INVALID",
)

private fun RestHomeMoneyPlan.toPlan() = HomeMoneyImportPlan(
    accounts = accounts.map { it.toMapping() },
    categories = categories.map { it.toMapping() },
    transactionCount = transactionCount,
    // A malformed date is not worth failing the whole screen over: the range is a nicety
    // beside the counts, and a plan nobody can see is worse than one missing its dates.
    firstDate = firstDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    lastDate = lastDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    blockedReason = blockedReason,
)

private fun RestPlannedAccount.toMapping() = HomeMoneyAccountMapping(
    externalId = externalId,
    name = name,
    currencyLabel = currencyLabel,
    currencyCode = currencyCode,
    balance = balance,
    targetAccountId = targetAccountId,
    included = included,
)

private fun RestPlannedCategory.toMapping() = HomeMoneyCategoryMapping(
    externalId = externalId,
    name = name,
    fullName = fullName,
    type = type,
    parentExternalId = parentExternalId,
    archived = archived,
    targetCategoryId = targetCategoryId,
)

/**
 * Back into the shape the server sent, with the user's edits on top.
 *
 * `balance` goes back as the same text it arrived as. It is HomeMoney's figure and the
 * server reconciles the opening balance against it, so parsing it here only to print it
 * again is a chance to hand back something subtly different.
 */
private fun HomeMoneyAccountMapping.toDto() = RestPlannedAccount(
    externalId = externalId,
    name = name,
    currencyLabel = currencyLabel,
    currencyCode = currencyCode,
    balance = balance,
    targetAccountId = targetAccountId,
    included = included,
)

private fun HomeMoneyCategoryMapping.toDto() = RestPlannedCategory(
    externalId = externalId,
    name = name,
    fullName = fullName,
    type = type,
    parentExternalId = parentExternalId,
    archived = archived,
    targetCategoryId = targetCategoryId,
)

private fun RestHomeMoneyStatus.toProgress() = HomeMoneyImportProgress(
    state = when (state) {
        "RUNNING" -> HomeMoneyImportProgress.State.RUNNING
        "DONE" -> HomeMoneyImportProgress.State.DONE
        "FAILED" -> HomeMoneyImportProgress.State.FAILED
        else -> HomeMoneyImportProgress.State.IDLE
    },
    accountsCreated = accountsCreated,
    categoriesCreated = categoriesCreated,
    imported = imported,
    skipped = skipped,
    processedChunks = processedChunks,
    error = error,
)
