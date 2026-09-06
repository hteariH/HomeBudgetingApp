package com.personalmoney.core.data

import com.personalmoney.core.currency.FxRate
import kotlinx.coroutines.flow.Flow

/**
 * Stored exchange rates, wherever they come from. See [AccountRepository] for the
 * guarantees.
 *
 * Narrower than the other contracts on purpose: every caller does the same thing with
 * these - `FxSnapshot.from(rates)` and then synchronous conversion - so the interface says
 * only that. Reads are the whole surface; writes are the refresher's business, and the two
 * platforms refresh from different places. Android pulls Frankfurter itself in a
 * WorkManager job; the browser reads `/api/fx/rates`, which the server refreshes nightly
 * from the same source, so both end up converting with the same numbers.
 */
interface ExchangeRateRepository {

    fun observeAll(): Flow<List<FxRate>>

    /** Re-reads from the source. A no-op where reads are already live. */
    suspend fun refresh() {}

    /**
     * The rate the user set by hand for [code], read as "1 [code] is n USD", or null when
     * they have not set one.
     *
     * Against USD and nothing else, because USD is what [com.personalmoney.core.currency.FxSnapshot]
     * triangulates through: a rate stored against anything else would convert to that one
     * currency and to nothing else, which is no use to somebody who holds bitcoin and
     * thinks in hryvnia.
     */
    suspend fun handRate(code: String): Double?

    /**
     * Sets that rate, or clears it when [perUsd] is null.
     *
     * This is the only way a currency no feed carries can take part in a total at all -
     * bitcoin, a voucher currency somebody invented on the currencies screen. Dollar
     * stablecoins need no help; see [com.personalmoney.core.currency.FxPegs].
     */
    suspend fun setHandRate(code: String, perUsd: Double?)
}
