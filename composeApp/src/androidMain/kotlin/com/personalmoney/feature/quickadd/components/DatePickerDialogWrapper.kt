package com.personalmoney.feature.quickadd.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.personalmoney.R
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogWrapper(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMs = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMs)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis ?: initialMs
                val date = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                onConfirm(date)
            }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}
