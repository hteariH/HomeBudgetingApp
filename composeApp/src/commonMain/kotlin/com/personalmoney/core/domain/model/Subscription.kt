package com.personalmoney.core.domain.model

import com.personalmoney.core.domain.SubscriptionFrequency
import kotlinx.datetime.LocalDate

/**
 * A recurring charge the user expects. See [Account] for why this is not the Room entity.
 *
 * [nextChargeDate] is stored rather than derived from [startDate] and [frequency], because
 * the user can push a single charge without shifting the whole schedule, and because a
 * charge that has already been posted must not be posted again after a clock change.
 *
 * The amount is in [currencyCode], which is the account's currency at the time the
 * subscription was set up - not necessarily the base currency, and not necessarily the
 * account's currency today.
 *
 * `id = 0` with a blank uuid means "not persisted yet". A screen building one for
 * [com.personalmoney.core.data.SubscriptionRepository.create] cannot invent an identity: on Android
 * the id comes from Room's autoincrement and the uuid is minted by the repository, and on
 * the web the server assigns both.
 */
data class Subscription(
    val id: Long = 0L,
    val uuid: String = "",
    val name: String,
    val accountId: Long,
    val categoryId: Long?,
    val amountMinor: Long,
    val currencyCode: String,
    val frequency: SubscriptionFrequency,
    /** How many [frequency] periods between charges - 2 with WEEKLY is fortnightly. */
    val interval: Int = 1,
    val nextChargeDate: LocalDate,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val active: Boolean = true,
    val reminderMinutesBefore: Int? = 24 * 60,
    val icon: String = "subscriptions",
    val color: Long = 0xFF008080L,
    val note: String? = null,
)
