package com.personalmoney.core.data

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.domain.model.Subscription
import com.personalmoney.core.domain.toDecimalString
import com.personalmoney.core.domain.toMinorUnits
import com.personalmoney.core.network.CreateSubscriptionRequest
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.RestSubscription
import com.personalmoney.core.network.UpdateSubscriptionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * The shared subscription contract, backed by REST. See [RestAccountRepository].
 *
 * `/api/subscriptions` has no client at all today - the React app never grew the screen -
 * so this is the first thing to read it from a browser. The endpoints themselves are long
 * finished on the server.
 */
class RestSubscriptionRepository(private val api: RestApi) : SubscriptionRepository {

    private val cache = MutableStateFlow<List<Subscription>>(emptyList())

    /**
     * See [LazyRefresh]. The screens are written against Room's guarantee - collect and
     * rows arrive - and a REST cache is empty until something asks the server.
     */
    private val lazyLoad = LazyRefresh { refresh() }

    /** The cache, but collecting it fetches first if nothing has yet. */
    private val loaded get() = cache.loadingOnce(lazyLoad)

    override fun observeAll(): Flow<List<Subscription>> = loaded

    override fun observeActive(): Flow<List<Subscription>> =
        loaded.map { all -> all.filter { it.active } }

    override suspend fun findById(id: Long): Subscription? =
        cache.value.firstOrNull { it.id == id } ?: api.subscription(id).toSubscription()

    /**
     * Always empty. Charges are posted by the server's own nightly job, so a browser tab
     * asking what is due would be asking to do work that has already been done - and
     * answering would let two clients post the same charge twice.
     */
    override suspend fun findDueAsOf(date: LocalDate): List<Subscription> = emptyList()

    override suspend fun create(subscription: Subscription): Long {
        val created = api.createSubscription(
            CreateSubscriptionRequest(
                name = subscription.name,
                accountId = subscription.accountId,
                categoryId = subscription.categoryId,
                amount = subscription.amountMajor(),
                frequency = subscription.frequency,
                intervalCount = subscription.interval,
                startDate = subscription.startDate.toString(),
                nextChargeDate = subscription.nextChargeDate.toString(),
                endDate = subscription.endDate?.toString(),
                reminderMinutesBefore = subscription.reminderMinutesBefore,
                icon = subscription.icon,
                color = subscription.color.toHexColor(),
                note = subscription.note,
            )
        )
        refresh()
        return created.id
    }

    override suspend fun update(subscription: Subscription) {
        api.updateSubscription(
            subscription.id,
            UpdateSubscriptionRequest(
                name = subscription.name,
                categoryId = subscription.categoryId,
                amount = subscription.amountMajor(),
                frequency = subscription.frequency,
                intervalCount = subscription.interval,
                nextChargeDate = subscription.nextChargeDate.toString(),
                endDate = subscription.endDate?.toString(),
                active = subscription.active,
                reminderMinutesBefore = subscription.reminderMinutesBefore,
                icon = subscription.icon,
                color = subscription.color.toHexColor(),
                note = subscription.note,
            ),
        )
        refresh()
    }

    override suspend fun setActive(id: Long, active: Boolean) {
        api.updateSubscription(id, UpdateSubscriptionRequest(active = active))
        refresh()
    }

    override suspend fun setNextChargeDate(id: Long, date: LocalDate) {
        api.updateSubscription(id, UpdateSubscriptionRequest(nextChargeDate = date.toString()))
        refresh()
    }

    override suspend fun delete(id: Long) {
        api.deleteSubscription(id)
        refresh()
    }

    override suspend fun refresh() {
        cache.value = api.subscriptions().map { it.toSubscription() }
    }
}

private fun Subscription.amountMajor(): String =
    amountMinor.toDecimalString(CurrencyRegistry.decimalsOrFallback(currencyCode))

private fun RestSubscription.toSubscription(): Subscription {
    // The account's own decimals, not a registry lookup: this is the exact scale the server
    // used to format `amount` (SubscriptionController passes account.currency.decimals), and
    // taking it from the payload removes any dependence on the accounts having refreshed
    // first. The two agree because the server pins a subscription's currency to its
    // account's at creation.
    val decimals = account.currency.decimals
    return Subscription(
        id = id,
        uuid = uuid,
        name = name,
        accountId = account.id,
        categoryId = category?.id,
        amountMinor = BigDecimal.parseString(amount).toMinorUnits(decimals),
        currencyCode = currencyCode,
        frequency = frequency,
        interval = intervalCount,
        nextChargeDate = LocalDate.parse(nextChargeDate),
        startDate = LocalDate.parse(startDate),
        endDate = endDate?.let { LocalDate.parse(it) },
        active = active,
        reminderMinutesBefore = reminderMinutesBefore,
        icon = icon,
        color = color.parseColorOrDefault(DEFAULT_SUBSCRIPTION_COLOR),
        note = note,
    )
}

/** Matches the Room entity's default, so the two clients agree on an unset colour. */
private const val DEFAULT_SUBSCRIPTION_COLOR = 0xFF008080L
