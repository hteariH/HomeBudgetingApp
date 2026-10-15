package com.personalmoney.feature.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.network.MemberDto
import com.personalmoney.core.ui.components.ConfirmDialog
import com.personalmoney.core.ui.pageBarColors
import com.personalmoney.core.ui.rememberTextSharer
import com.personalmoney.core.ui.verticalScrollbar
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The household: who is in it, an invitation link for the owner to send, and a way out for
 * everyone else. Renaming, removing people and handing over ownership stay on the web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    onBack: (() -> Unit)? = null,
    viewModel: FamilyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val share = rememberTextSharer()
    var confirmingLeave by remember { mutableStateOf(false) }

    // The link is made on demand and goes straight to the share sheet; keeping it around
    // would only invite a second, stale copy of a single-use token.
    LaunchedEffect(state.inviteLink) {
        val link = state.inviteLink ?: return@LaunchedEffect
        share(getString(Res.string.family_invite_share_text, link))
        viewModel.inviteShared()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pageBarColors(),
                title = { Text(stringResource(Res.string.family_title)) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Column {
                state.members.forEachIndexed { index, member ->
                    if (index > 0) HorizontalDivider()
                    MemberRow(member)
                }
            }

            if (state.isOwner) {
                Button(
                    onClick = viewModel::invite,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Text(stringResource(Res.string.family_invite), Modifier.padding(start = 8.dp))
                }
                // An owner cannot leave while anyone else is in; the way out is handing the
                // household over first. Alone, there is nothing to hand it to.
                if (state.members.size > 1) {
                    Text(
                        stringResource(Res.string.family_leave_owner_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!state.busy) {
                OutlinedButton(
                    onClick = { confirmingLeave = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(Res.string.family_leave)) }
            }

            if (state.busy) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }

    if (confirmingLeave) {
        ConfirmDialog(
            title = stringResource(Res.string.family_leave),
            message = stringResource(Res.string.family_leave_confirm),
            confirmLabel = stringResource(Res.string.family_leave),
            dismissLabel = stringResource(Res.string.action_cancel),
            destructive = true,
            onConfirm = viewModel::leave,
            onDismiss = { confirmingLeave = false },
        )
    }
}

@Composable
private fun MemberRow(member: MemberDto) {
    val name = listOf(member.firstName, member.lastName).filter { it.isNotBlank() }
        .joinToString(" ").ifEmpty { member.email }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (member.email.isNotBlank() && member.email != name) {
                Text(
                    member.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(
                if (member.role == "OWNER") Res.string.family_owner else Res.string.family_member
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
