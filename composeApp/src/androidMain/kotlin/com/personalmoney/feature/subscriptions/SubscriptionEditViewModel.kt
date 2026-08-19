package com.personalmoney.feature.subscriptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.SubscriptionRepository
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.Subscription
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.SubscriptionFrequency
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import java.util.Currency
import com.personalmoney.core.domain.toMinorUnits
import com.personalmoney.core.domain.toMajorUnits

data class SubscriptionEditUiState(
    val id: Long? = null,
    val name: String = "",
    val amount: String = "",
    val currencyCode: String = "USD",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val frequency: SubscriptionFrequency = SubscriptionFrequency.MONTHLY,
    val interval: Int = 1,
    val nextChargeDate: LocalDate = today(),
    val active: Boolean = true,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
) {
    val isEditing: Boolean get() = id != null
    val canSave: Boolean
        get() = name.isNotBlank() && amount.isNotBlank() && accountId != null && !saving
}

private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class SubscriptionEditViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val subscriptionRepository: SubscriptionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val subscriptionId: Long? = savedStateHandle.get<String>("subscriptionId")?.toLongOrNull()
    private val internal = MutableStateFlow(SubscriptionEditUiState(id = subscriptionId))

    val uiState: StateFlow<SubscriptionEditUiState> = combine(
        internal,
        accountRepository.observeActive(),
        categoryRepository.observeByType(CategoryType.EXPENSE)
    ) { local, accounts, categories ->
        local.copy(
            accounts = accounts,
            categories = categories,
            accountId = local.accountId ?: accounts.firstOrNull()?.id,
            currencyCode = local.currencyCode.ifEmpty { accounts.firstOrNull()?.currencyCode ?: "USD" }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionEditUiState())

    init {
        if (subscriptionId != null) {
            viewModelScope.launch {
                subscriptionRepository.findById(subscriptionId)?.let { s ->
                    val decimals = currencyDecimals(s.currencyCode)
                    internal.value = SubscriptionEditUiState(
                        id = s.id,
                        name = s.name,
                        amount = s.amountMinor.toMajorUnits(decimals).toStringExpanded(),
                        currencyCode = s.currencyCode,
                        accountId = s.accountId,
                        categoryId = s.categoryId,
                        frequency = s.frequency,
                        interval = s.interval,
                        nextChargeDate = s.nextChargeDate,
                        active = s.active
                    )
                }
            }
        }
    }

    fun setName(value: String) = internal.update { it.copy(name = value) }
    fun setAmount(value: String) = internal.update { it.copy(amount = value) }
    fun setAccount(id: Long) = internal.update {
        val acc = it.accounts.firstOrNull { a -> a.id == id }
        it.copy(accountId = id, currencyCode = acc?.currencyCode ?: it.currencyCode)
    }
    fun setCategory(id: Long?) = internal.update { it.copy(categoryId = id) }
    fun setFrequency(f: SubscriptionFrequency) = internal.update { it.copy(frequency = f) }
    fun setInterval(value: Int) = internal.update { it.copy(interval = value.coerceAtLeast(1)) }
    fun setNextChargeDate(date: LocalDate) = internal.update { it.copy(nextChargeDate = date) }
    fun setActive(value: Boolean) = internal.update { it.copy(active = value) }
    fun clearError() = internal.update { it.copy(error = null) }

    fun save() {
        val s = uiState.value
        if (!s.canSave) return
        val decimals = currencyDecimals(s.currencyCode)
        val amountMinor = runCatching {
            BigDecimal.parseString(s.amount).toMinorUnits(decimals)
        }.getOrElse {
            viewModelScope.launch {
                // The multiplatform resource reader is suspend, so the message is resolved
                // here rather than off an Android Context.
                val message = getString(Res.string.subscriptions_invalid_amount)
                internal.update { it.copy(error = message) }
            }
            return
        }
        internal.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (s.id == null) {
                    subscriptionRepository.create(
                        Subscription(
                            name = s.name.trim(),
                            accountId = s.accountId!!,
                            categoryId = s.categoryId,
                            amountMinor = amountMinor,
                            currencyCode = s.currencyCode,
                            frequency = s.frequency,
                            interval = s.interval,
                            nextChargeDate = s.nextChargeDate,
                            startDate = today(),
                            active = s.active
                        )
                    )
                } else {
                    subscriptionRepository.findById(s.id)?.let { existing ->
                        subscriptionRepository.update(
                            existing.copy(
                                name = s.name.trim(),
                                accountId = s.accountId!!,
                                categoryId = s.categoryId,
                                amountMinor = amountMinor,
                                currencyCode = s.currencyCode,
                                frequency = s.frequency,
                                interval = s.interval,
                                nextChargeDate = s.nextChargeDate,
                                active = s.active
                            )
                        )
                    }
                }
            }.onSuccess { internal.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> internal.update { it.copy(saving = false, error = e.message) } }
        }
    }

    fun delete() {
        val id = internal.value.id ?: return
        viewModelScope.launch {
            runCatching { subscriptionRepository.delete(id) }
                .onSuccess { internal.update { it.copy(saved = true) } }
                .onFailure { e -> internal.update { it.copy(error = e.message) } }
        }
    }

    private fun currencyDecimals(code: String): Int =
        runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2)
}
