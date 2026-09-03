package com.personalmoney.core.data

import com.personalmoney.core.domain.model.HomeMoneyAccountMapping
import com.personalmoney.core.domain.model.HomeMoneyCategoryMapping
import com.personalmoney.core.domain.model.HomeMoneyConnection
import com.personalmoney.core.domain.model.HomeMoneyImportPlan
import com.personalmoney.core.domain.model.HomeMoneyImportProgress

/**
 * Importing a ledger from homemoney.ua.
 *
 * No flow anywhere, like [CsvImportRepository] and for a related reason: none of this is a
 * store that changes under a screen. A plan is computed once and looked at once, and the
 * progress is a background job that has to be *asked*, because a browser tab has nothing
 * pushing to it.
 *
 * Bound only in the web graph. The import writes to the server's ledger and the phone
 * reads its own out of Room, so an Android user would see nothing until the next sync -
 * see `navigation.homeMoneyImportAvailable`.
 *
 * **No credential passes through here.** The consent happens between the browser,
 * homemoney.ua and our server; [authorizationUrl] is the only part the app touches, and it
 * is a URL to navigate to rather than anything secret.
 */
interface HomeMoneyImportRepository {

    /** Whether HomeMoney is linked, and to whom. */
    suspend fun connection(): HomeMoneyConnection

    /**
     * Where to send the browser to ask HomeMoney for consent.
     *
     * Fetched rather than built here: the URL carries a signed state naming this user, and
     * only the server can mint one.
     */
    suspend fun authorizationUrl(): String

    /** Drops the stored token. Everything already imported stays. */
    suspend fun disconnect()

    /**
     * What an import would bring in, without bringing any of it in.
     *
     * Slow - it reads the whole HomeMoney history to report a count and a date range - and
     * deliberately so: a summary that guessed would be worse than one that took a moment.
     */
    suspend fun plan(): HomeMoneyImportPlan

    /**
     * Starts the import from the plan as the user left it, and returns immediately.
     *
     * The work outlives the request, so the answer is the first progress reading rather
     * than a result. Watch [progress] for the rest.
     */
    suspend fun start(
        accounts: List<HomeMoneyAccountMapping>,
        categories: List<HomeMoneyCategoryMapping>,
    ): HomeMoneyImportProgress

    suspend fun progress(): HomeMoneyImportProgress
}
