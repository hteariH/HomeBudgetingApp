package com.personalmoney.feature.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.billing.Entitlement
import com.personalmoney.core.billing.EntitlementRepository
import com.personalmoney.core.billing.EntitlementSource
import com.personalmoney.core.billing.PurchaseOutcome
import com.personalmoney.core.billing.StoreBilling
import com.personalmoney.core.billing.StoreProduct
import com.personalmoney.core.network.serverMessage
import com.personalmoney.resources.Res
import com.personalmoney.resources.common_request_failed
import com.personalmoney.resources.premium_purchase_pending
import com.personalmoney.resources.premium_purchase_problem
import com.personalmoney.resources.premium_restore_none
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.getString

data class PremiumUiState(
    val loading: Boolean = true,
    val premium: Boolean = false,
    val expiresAt: Instant? = null,
    /** Premium that has been cancelled but is still paid up - worth saying out loud. */
    val lapsing: Boolean = false,
    /** True when the entitlement was handed out rather than bought. */
    val granted: Boolean = false,
    /**
     * Whether this device has a store at all. False in a browser.
     *
     * Kept apart from [canBuyHere] because the two failures need opposite sentences: a
     * browser should be told to buy on the phone, while a phone that cannot buy is
     * looking at a server with no Play account behind it, and telling *that* user to go
     * to their phone would be sending them in a circle.
     */
    val storeHere: Boolean = false,
    /** Whether this device and this server can, between them, complete a purchase. */
    val canBuyHere: Boolean = false,
    val offers: List<StoreProduct> = emptyList(),
    /** The product currently in the Play dialog, so only its button shows a spinner. */
    val buying: String? = null,
    val restoring: Boolean = false,
    val message: String? = null
)

/**
 * The paywall.
 *
 * Everything it knows about the entitlement comes from the server, and everything it
 * knows about prices comes from the store - neither is invented here. What is left is the
 * screen's own state: which button is spinning, and what to say when something did not
 * work.
 */
class PremiumViewModel(
    private val entitlements: EntitlementRepository,
    private val store: StoreBilling
) : ViewModel() {

    private val internalState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = internalState.asStateFlow()

    init {
        viewModelScope.launch {
            entitlements.entitlement.collect { adopt(it) }
        }
        load()
    }

    /**
     * Reads the entitlement, then asks the store to price what the server offers.
     *
     * In that order, and not in parallel: the product ids come from the server, so there
     * is nothing to ask the store about until the first call has answered.
     */
    fun load() {
        viewModelScope.launch {
            internalState.update { it.copy(loading = true) }
            val entitlement = entitlements.refresh()
            if (entitlement == null) {
                internalState.update {
                    it.copy(loading = false, message = getString(Res.string.common_request_failed))
                }
                return@launch
            }
            val offers = if (store.available && entitlement.purchasesAccepted) {
                runCatching { store.products(entitlement.products) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            internalState.update { it.copy(loading = false, offers = offers) }
        }
    }

    fun buy(productId: String) {
        if (internalState.value.buying != null) return
        viewModelScope.launch {
            internalState.update { it.copy(buying = productId, message = null) }
            val outcome = runCatching { store.buy(productId) }
                .getOrElse { PurchaseOutcome.Failed(it.message) }
            val message = when (outcome) {
                is PurchaseOutcome.Bought -> submit(outcome)
                // Closing the dialog is a decision, not a mishap. Saying anything about it
                // would be the app arguing with it.
                PurchaseOutcome.Cancelled -> null
                PurchaseOutcome.Pending -> getString(Res.string.premium_purchase_pending)
                is PurchaseOutcome.Failed ->
                    outcome.message ?: getString(Res.string.premium_purchase_problem)
            }
            internalState.update { it.copy(buying = null, message = message) }
        }
    }

    /**
     * "I have already paid."
     *
     * Two things at once, because the user cannot tell which one they need: hand over any
     * purchase the store holds for this device, and ask the server to re-check what it
     * already has with Google.
     */
    fun restore() {
        viewModelScope.launch {
            internalState.update { it.copy(restoring = true, message = null) }
            entitlements.syncOwnedPurchases()
            val entitlement = runCatching { entitlements.forceRecheck() }.getOrNull()
            val message = when {
                entitlement?.premium == true -> null
                else -> getString(Res.string.premium_restore_none)
            }
            internalState.update { it.copy(restoring = false, message = message) }
        }
    }

    fun dismissMessage() = internalState.update { it.copy(message = null) }

    /**
     * Hands the token to the server. The server's own sentence is preferred to anything
     * this app could write: it is the one that knows *why* a receipt was refused.
     */
    private suspend fun submit(bought: PurchaseOutcome.Bought): String? = try {
        entitlements.submit(bought)
        null
    } catch (e: Exception) {
        e.serverMessage() ?: getString(Res.string.premium_purchase_problem)
    }

    private fun adopt(entitlement: Entitlement) {
        internalState.update {
            it.copy(
                premium = entitlement.premium,
                expiresAt = entitlement.expiresAt,
                lapsing = entitlement.lapsing,
                granted = entitlement.source == EntitlementSource.MANUAL,
                storeHere = store.available,
                canBuyHere = store.available && entitlement.purchasesAccepted
            )
        }
    }
}
