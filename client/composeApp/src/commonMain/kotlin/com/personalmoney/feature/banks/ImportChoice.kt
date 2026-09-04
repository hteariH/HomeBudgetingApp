package com.personalmoney.feature.banks

import androidx.compose.runtime.Composable
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.ui.PickerOption
import com.personalmoney.resources.Res
import com.personalmoney.resources.common_uncategorised
import com.personalmoney.resources.import_transfer_from
import com.personalmoney.resources.import_transfer_to
import org.jetbrains.compose.resources.stringResource

/**
 * What one statement line is being imported as: a category, or another of the user's own
 * accounts when the line only moved money between them.
 *
 * The two share a dropdown because they answer the same question - "what was this?" - and
 * a statement gives no way to tell them apart: a card payment and a top-up of the savings
 * account are both money leaving, and only the person reading knows which. Importing the
 * second as an expense is what makes a month's spending read too high.
 *
 * [PickerOption] speaks in `Long?` ids, so an account is carried as its negated id. Ids are
 * database identities and therefore positive, so the two spaces cannot collide - and the
 * encoding never leaves this screen: both view models decode it before anything is sent.
 */
object ImportChoice {

    fun encodeAccount(accountId: Long): Long = -accountId

    /** The account behind an option, or null when the option was a category or "none". */
    fun accountIdOf(optionId: Long?): Long? = optionId?.takeIf { it < 0L }?.let { -it }

    /** The category behind an option, or null when it was an account or "none". */
    fun categoryIdOf(optionId: Long?): Long? = optionId?.takeIf { it > 0L }
}

/**
 * The dropdown for one row: no category, then the categories that suit its direction, then
 * every other account as a transfer.
 *
 * Archived accounts are offered too - money did move there, and a statement being imported
 * now is usually older than the day the account was put away.
 */
@Composable
fun importChoiceOptions(
    type: TransactionType,
    categories: List<Category>,
    accounts: List<Account>,
    currentAccountId: Long?,
): List<PickerOption> {
    val transferLabel =
        if (type == TransactionType.INCOME) Res.string.import_transfer_from else Res.string.import_transfer_to
    return buildList {
        add(PickerOption(null, stringResource(Res.string.common_uncategorised)))
        categories.forEach { add(PickerOption(it.id, it.name)) }
        accounts.filter { it.id != currentAccountId }.forEach {
            add(PickerOption(ImportChoice.encodeAccount(it.id), stringResource(transferLabel, it.name)))
        }
    }
}

/** What the field shows for the row's current choice. */
@Composable
fun importChoiceLabel(
    optionId: Long?,
    type: TransactionType,
    categories: List<Category>,
    accounts: List<Account>,
): String {
    val accountId = ImportChoice.accountIdOf(optionId)
    if (accountId != null) {
        val account = accounts.firstOrNull { it.id == accountId }
        val label =
            if (type == TransactionType.INCOME) Res.string.import_transfer_from else Res.string.import_transfer_to
        // A counterpart that has since gone falls back to "uncategorised" rather than to a
        // blank field, which would read as a choice the user did not make.
        if (account != null) return stringResource(label, account.name)
    }
    val category = categories.firstOrNull { it.id == ImportChoice.categoryIdOf(optionId) }
    return category?.name ?: stringResource(Res.string.common_uncategorised)
}
