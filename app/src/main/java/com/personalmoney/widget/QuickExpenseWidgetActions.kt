package com.personalmoney.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.personalmoney.core.calc.Calculator
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

// ── Preference keys ───────────────────────────────────────────────────────────

val KEY_EXPRESSION   = stringPreferencesKey("w_expr")
val KEY_TYPE         = stringPreferencesKey("w_type")        // "EXPENSE" | "INCOME"
val KEY_CAT_ID       = longPreferencesKey("w_cat")
val KEY_CAT_NAME     = stringPreferencesKey("w_cat_name")
val KEY_JUST_SAVED   = booleanPreferencesKey("w_saved")
val KEY_CATEGORIES   = stringPreferencesKey("w_cats")        // "id|name,..."
val KEY_ACCOUNTS     = stringPreferencesKey("w_accs")        // "id|name|currency,..."
val KEY_ACCOUNT_ID   = longPreferencesKey("w_acc_id")
val KEY_ACCOUNT_NAME = stringPreferencesKey("w_acc_name")
val KEY_CURRENCY     = stringPreferencesKey("w_currency")

// ── Action parameter keys ─────────────────────────────────────────────────────

val PARAM_DIGIT        = ActionParameters.Key<String>("digit")
val PARAM_CAT_ID       = ActionParameters.Key<Long>("cat_id")
val PARAM_CAT_NAME     = ActionParameters.Key<String>("cat_name")
val PARAM_TYPE         = ActionParameters.Key<String>("type")

// ── Actions ───────────────────────────────────────────────────────────────────

class AppendDigitAction : ActionCallback {
    override suspend fun onAction(
        context: Context, glanceId: GlanceId, parameters: ActionParameters
    ) {
        val digit = parameters[PARAM_DIGIT] ?: return
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val current = prefs[KEY_EXPRESSION] ?: ""
            prefs.toMutablePreferences().apply {
                this[KEY_EXPRESSION] = appendToExpression(current, digit)
                this[KEY_JUST_SAVED] = false
            }
        }
        QuickExpenseWidget().update(context, glanceId)
    }
}

class BackspaceAction : ActionCallback {
    override suspend fun onAction(
        context: Context, glanceId: GlanceId, parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_EXPRESSION] = (prefs[KEY_EXPRESSION] ?: "").dropLast(1)
                this[KEY_JUST_SAVED] = false
            }
        }
        QuickExpenseWidget().update(context, glanceId)
    }
}

class SelectTypeAction : ActionCallback {
    override suspend fun onAction(
        context: Context, glanceId: GlanceId, parameters: ActionParameters
    ) {
        val type = parameters[PARAM_TYPE] ?: return
        val entryPoint = WidgetDependencies
        val categoryType = if (type == "INCOME") CategoryType.INCOME else CategoryType.EXPENSE
        val categories = entryPoint.categoryRepository
            .observeByType(categoryType).first().filter { !it.archived }.take(4)

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_TYPE] = type
                this[KEY_CATEGORIES] = categories.joinToString(",") { "${it.id}|${it.name}" }
                remove(KEY_CAT_ID)
                remove(KEY_CAT_NAME)
            }
        }
        QuickExpenseWidget().update(context, glanceId)
    }
}

class SelectAccountAction : ActionCallback {
    override suspend fun onAction(
        context: Context, glanceId: GlanceId, parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val accounts = parseAccountList(prefs[KEY_ACCOUNTS] ?: "")
            if (accounts.size <= 1) return@updateAppWidgetState prefs.toMutablePreferences()
            val currentId = prefs[KEY_ACCOUNT_ID]
            val currentIdx = accounts.indexOfFirst { it.first == currentId }.takeIf { it >= 0 } ?: 0
            val next = accounts[(currentIdx + 1) % accounts.size]
            prefs.toMutablePreferences().apply {
                this[KEY_ACCOUNT_ID]   = next.first
                this[KEY_ACCOUNT_NAME] = next.second
                this[KEY_CURRENCY]     = next.third
            }
        }
        QuickExpenseWidget().update(context, glanceId)
    }
}

class SelectCategoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context, glanceId: GlanceId, parameters: ActionParameters
    ) {
        val catId   = parameters[PARAM_CAT_ID]   ?: return
        val catName = parameters[PARAM_CAT_NAME] ?: return
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                if (prefs[KEY_CAT_ID] == catId) {
                    remove(KEY_CAT_ID)
                    remove(KEY_CAT_NAME)
                } else {
                    this[KEY_CAT_ID]   = catId
                    this[KEY_CAT_NAME] = catName
                }
            }
        }
        QuickExpenseWidget().update(context, glanceId)
    }
}

class SaveWidgetExpenseAction : ActionCallback {
    override suspend fun onAction(
        context: Context, glanceId: GlanceId, parameters: ActionParameters
    ) {
        val entryPoint = WidgetDependencies
        val state      = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val expr       = state[KEY_EXPRESSION]?.takeIf { it.isNotEmpty() } ?: return
        val catId      = state[KEY_CAT_ID]
        val isIncome   = state[KEY_TYPE] == "INCOME"
        val txType     = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

        val accountRepo = entryPoint.accountRepository
        val selectedId  = state[KEY_ACCOUNT_ID]
        val accountCandidate: AccountEntity? =
            (if (selectedId != null) accountRepo.findById(selectedId) else null)
            ?: accountRepo.observeActive().first().firstOrNull()
        val account = accountCandidate ?: return

        val decimals = runCatching {
            java.util.Currency.getInstance(account.currencyCode).defaultFractionDigits
        }.getOrDefault(2)
        val amount = Calculator.evaluate(expr, decimals) ?: return
        if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) return
        val amountMinor = amount.movePointRight(decimals).toLong()
        val now = Clock.System.now()

        runCatching {
            entryPoint.transactionRepository.insert(
                TransactionEntity(
                    type               = txType,
                    accountId          = account.id,
                    categoryId         = catId,
                    amountMinor        = amountMinor,
                    currencyCode       = account.currencyCode,
                    accountAmountMinor = amountMinor,
                    occurredAt         = now,
                    createdAt          = now
                )
            )
        }.onSuccess {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[KEY_EXPRESSION] = ""
                    this[KEY_JUST_SAVED] = true
                }
            }
            QuickExpenseWidget().update(context, glanceId)

            delay(2_000)

            val categoryType = if (isIncome) CategoryType.INCOME else CategoryType.EXPENSE
            val categories   = entryPoint.categoryRepository
                .observeByType(categoryType).first().filter { !it.archived }.take(4)
            val accounts     = accountRepo.observeActive().first()
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[KEY_JUST_SAVED] = false
                    this[KEY_CATEGORIES] = categories.joinToString(",") { "${it.id}|${it.name}" }
                    this[KEY_ACCOUNTS]   = accounts.joinToString(",") { "${it.id}|${it.name}|${it.currencyCode}" }
                }
            }
            QuickExpenseWidget().update(context, glanceId)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

internal fun appendToExpression(current: String, digit: String): String = when {
    digit == "." -> when {
        "." in current -> current
        current.isEmpty() -> "0."
        else -> "$current."
    }
    digit == "0" && current == "0" -> current
    current == "0" -> digit
    else -> current + digit
}

internal fun parseCategoryList(input: String): List<Pair<Long, String>> {
    if (input.isEmpty()) return emptyList()
    return input.split(",").mapNotNull { entry ->
        val parts = entry.split("|", limit = 2)
        if (parts.size == 2) Pair(parts[0].toLongOrNull() ?: return@mapNotNull null, parts[1])
        else null
    }
}

// Returns list of (id, name, currencyCode)
internal fun parseAccountList(input: String): List<Triple<Long, String, String>> {
    if (input.isEmpty()) return emptyList()
    return input.split(",").mapNotNull { entry ->
        val parts = entry.split("|", limit = 3)
        if (parts.size == 3) Triple(parts[0].toLongOrNull() ?: return@mapNotNull null, parts[1], parts[2])
        else null
    }
}
