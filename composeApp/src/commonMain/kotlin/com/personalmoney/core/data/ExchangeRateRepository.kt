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
}
