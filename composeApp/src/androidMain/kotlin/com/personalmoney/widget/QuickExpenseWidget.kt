package com.personalmoney.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.first

class QuickExpenseWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = WidgetDependencies

        // Read existing prefs to preserve user's type/account selection
        val currentPrefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val isIncome = currentPrefs[KEY_TYPE] == "INCOME"
        val categoryType = if (isIncome) CategoryType.INCOME else CategoryType.EXPENSE

        val accounts   = entryPoint.accountRepository.observeActive().first()
        val categories = entryPoint.categoryRepository
            .observeByType(categoryType).first().filter { !it.archived }.take(4)

        // Preserve selected account if still active, otherwise fallback to first
        val currentAccId = currentPrefs[KEY_ACCOUNT_ID]
        val selectedAccount = accounts.firstOrNull { it.id == currentAccId } ?: accounts.firstOrNull()

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_CATEGORIES] = categories.joinToString(",") { "${it.id}|${it.name}" }
                this[KEY_ACCOUNTS]   = accounts.joinToString(",") { "${it.id}|${it.name}|${it.currencyCode}" }
                if (selectedAccount != null) {
                    this[KEY_ACCOUNT_ID]   = selectedAccount.id
                    this[KEY_ACCOUNT_NAME] = selectedAccount.name
                    this[KEY_CURRENCY]     = selectedAccount.currencyCode
                }
                if (prefs[KEY_TYPE] == null) this[KEY_TYPE] = "EXPENSE"
            }
        }

        // Glance runs its own composition, so the Compose Multiplatform `stringResource`
        // composable is not available inside it. provideGlance is suspend, which is exactly
        // what the shared resource reader wants - so the labels are read here and passed in.
        val labels = WidgetLabels(
            expense = getString(Res.string.widget_expense),
            income = getString(Res.string.widget_income),
            saved = getString(Res.string.widget_saved),
        )

        provideContent {
            GlanceTheme {
                WidgetBody(currentState(), labels)
            }
        }
    }
}

/** Widget text, resolved before composition — see the note in `provideGlance`. */
private data class WidgetLabels(val expense: String, val income: String, val saved: String)

@Composable
private fun WidgetBody(prefs: Preferences, labels: WidgetLabels) {
    val expression   = prefs[KEY_EXPRESSION] ?: ""
    val selectedCatId = prefs[KEY_CAT_ID]
    val justSaved    = prefs[KEY_JUST_SAVED] ?: false
    val currencyCode = prefs[KEY_CURRENCY] ?: ""
    val categories   = parseCategoryList(prefs[KEY_CATEGORIES] ?: "")
    val isExpense    = (prefs[KEY_TYPE] ?: "EXPENSE") == "EXPENSE"
    val accountName  = prefs[KEY_ACCOUNT_NAME] ?: ""
    val multiAccounts = (prefs[KEY_ACCOUNTS] ?: "").count { it == ',' } >= 1

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(8.dp)
    ) {
        // Row 1: Type toggle + Account selector
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(30.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TypeButton(
                label = labels.expense,
                isSelected = isExpense,
                type = "EXPENSE",
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            )
            Spacer(GlanceModifier.width(4.dp))
            TypeButton(
                label = labels.income,
                isSelected = !isExpense,
                type = "INCOME",
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            )
            if (accountName.isNotEmpty()) {
                Spacer(GlanceModifier.width(4.dp))
                AccountChip(
                    name = accountName,
                    canCycle = multiAccounts,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
            }
        }

        Spacer(GlanceModifier.height(4.dp))

        // Row 2: Amount display + backspace
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(38.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(10.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = amountText(currencyCode, expression),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1
            )
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .cornerRadius(14.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .clickable(actionRunCallback<BackspaceAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "⌫",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontSize = 12.sp
                    )
                )
            }
        }

        // Row 3: Category chips (only if available)
        if (categories.isNotEmpty()) {
            Spacer(GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                categories.forEachIndexed { i, (catId, catName) ->
                    if (i > 0) Spacer(GlanceModifier.width(4.dp))
                    CategoryChip(catId, catName, catId == selectedCatId)
                }
            }
        }

        Spacer(GlanceModifier.height(4.dp))

        if (justSaved) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    labels.saved,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        } else {
            // defaultWeight() is valid here — we're inside WidgetBody's Column (ColumnScope)
            Numpad(
                hasExpression = expression.isNotEmpty(),
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }
    }
}

@Composable
private fun TypeButton(label: String, isSelected: Boolean, type: String, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) GlanceTheme.colors.primaryContainer
                else GlanceTheme.colors.surfaceVariant
            )
            .cornerRadius(15.dp)
            .clickable(
                actionRunCallback<SelectTypeAction>(
                    actionParametersOf(PARAM_TYPE to type)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (isSelected) GlanceTheme.colors.onPrimaryContainer
                else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun AccountChip(name: String, canCycle: Boolean, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .background(GlanceTheme.colors.tertiaryContainer)
            .cornerRadius(15.dp)
            .padding(horizontal = 6.dp)
            .clickable(actionRunCallback<SelectAccountAction>()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (canCycle) "$name >" else name,
            style = TextStyle(
                color = GlanceTheme.colors.onTertiaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun CategoryChip(catId: Long, catName: String, isSelected: Boolean) {
    Box(
        modifier = GlanceModifier
            .height(26.dp)
            .background(
                if (isSelected) GlanceTheme.colors.primaryContainer
                else GlanceTheme.colors.surfaceVariant
            )
            .cornerRadius(13.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable(
                actionRunCallback<SelectCategoryAction>(
                    actionParametersOf(PARAM_CAT_ID to catId, PARAM_CAT_NAME to catName)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = catName,
            style = TextStyle(
                color = if (isSelected) GlanceTheme.colors.onPrimaryContainer
                else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun Numpad(hasExpression: Boolean, modifier: GlanceModifier = GlanceModifier.fillMaxWidth()) {
    Column(modifier = modifier) {
        NumpadRow(listOf("7", "8", "9"), GlanceModifier.fillMaxWidth().defaultWeight())
        Spacer(GlanceModifier.height(4.dp))
        NumpadRow(listOf("4", "5", "6"), GlanceModifier.fillMaxWidth().defaultWeight())
        Spacer(GlanceModifier.height(4.dp))
        NumpadRow(listOf("1", "2", "3"), GlanceModifier.fillMaxWidth().defaultWeight())
        Spacer(GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            DigitButton(".", GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(4.dp))
            DigitButton("0", GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(4.dp))
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .background(
                        if (hasExpression) GlanceTheme.colors.primary
                        else GlanceTheme.colors.surfaceVariant
                    )
                    .cornerRadius(8.dp)
                    .clickable(actionRunCallback<SaveWidgetExpenseAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✓",
                    style = TextStyle(
                        color = if (hasExpression) GlanceTheme.colors.onPrimary
                        else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun NumpadRow(digits: List<String>, modifier: GlanceModifier) {
    Row(modifier = modifier) {
        digits.forEachIndexed { i, digit ->
            if (i > 0) Spacer(GlanceModifier.width(4.dp))
            DigitButton(digit, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

@Composable
private fun DigitButton(label: String, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(8.dp)
            .clickable(
                actionRunCallback<AppendDigitAction>(
                    actionParametersOf(PARAM_DIGIT to label)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

private fun amountText(currencyCode: String, expression: String): String {
    val symbol = if (currencyCode.isNotEmpty()) {
        runCatching { java.util.Currency.getInstance(currencyCode).symbol }.getOrDefault(currencyCode)
    } else ""
    val amount = expression.ifEmpty { "0" }
    return if (symbol.isNotEmpty()) "$symbol $amount" else amount
}
