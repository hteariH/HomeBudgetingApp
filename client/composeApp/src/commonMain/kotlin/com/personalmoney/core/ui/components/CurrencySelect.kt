package com.personalmoney.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.personalmoney.core.domain.model.Currency
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * A currency picker that filters as you type. A plain menu is fine for the handful of
 * currencies on an account, but the base-currency picker draws from every active one -
 * with a hundred of them switched on, scanning a wall of codes is the slow way to find
 * the one you want.
 *
 * The field doubles as the search box: opening it clears the text so the first keystroke
 * starts a fresh query rather than editing the code that is already selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySelect(
    selected: String,
    currencies: List<Currency>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    // Null while the menu is closed: the field then shows the selection, not a query.
    var query by remember { mutableStateOf<String?>(null) }

    val selectedLabel = currencies.firstOrNull { it.code == selected }
        ?.let { "${it.code} · ${it.name}" }
        ?: selected
    val matches = remember(currencies, query) {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) currencies
        else currencies.filter { it.code.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open ->
            expanded = open
            query = if (open) "" else null
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = query ?: selectedLabel,
            onValueChange = {
                query = it
                expanded = true
            },
            singleLine = true,
            label = label?.let { { Text(it) } },
            placeholder = { Text(stringResource(Res.string.currencies_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                    modifier = Modifier.menuAnchor(MenuAnchorType.SecondaryEditable, true)
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = null
            },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            if (matches.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.currencies_no_matches)) },
                    onClick = {},
                    enabled = false
                )
            }
            matches.forEach { currency ->
                DropdownMenuItem(
                    text = { Text("${currency.code} · ${currency.name}") },
                    trailingIcon = if (currency.code == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        onSelect(currency.code)
                        expanded = false
                        query = null
                    }
                )
            }
        }
    }
}
