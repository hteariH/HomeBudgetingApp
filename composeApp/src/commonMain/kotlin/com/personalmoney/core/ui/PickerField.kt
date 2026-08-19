package com.personalmoney.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** One choice in a [PickerField]. A null [id] is the "nothing chosen" row. */
data class PickerOption(val id: Long?, val label: String)

/**
 * A read-only field that opens a menu - the wide-window equivalent of the phone's picker
 * sheet.
 *
 * A sheet is right for a thumb and wrong for a mouse: on a desktop the list belongs under
 * the field that opened it, and getting back out should cost a click somewhere else rather
 * than a deliberate dismiss. Chosen by window width rather than by platform, like every
 * other adaptive decision here.
 */
@Composable
fun PickerField(
    label: String,
    value: String?,
    options: List<PickerOption>,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // An invisible button over the field: a read-only OutlinedTextField still takes the
        // click itself, so the menu would never open.
        if (enabled) {
            TextButton(onClick = { expanded = true }, modifier = Modifier.matchParentSize()) {}
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
