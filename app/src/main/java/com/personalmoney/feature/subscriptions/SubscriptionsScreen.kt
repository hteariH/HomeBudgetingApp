package com.personalmoney.feature.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.R
import com.personalmoney.core.database.entity.SubscriptionEntity
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.SubscriptionFrequency
import com.personalmoney.core.ui.components.EmptyState
import com.personalmoney.core.ui.components.IconCircle
import com.personalmoney.core.ui.components.MoneyText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscriptions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.subscriptions_new))
            }
        }
    ) { padding ->
        if (state.subscriptions.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.subscriptions_new),
                actionLabel = stringResource(R.string.action_add),
                onAction = onAdd,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.subscriptions_total_month),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            MoneyText(
                                money = state.perMonth,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.subscriptions_total_year),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            MoneyText(
                                money = state.perYear,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            items(state.subscriptions, key = { it.id }) { sub ->
                SubscriptionRow(
                    sub = sub,
                    onClick = { onEdit(sub.id) },
                    onToggle = { viewModel.setActive(sub.id, !sub.active) }
                )
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    sub: SubscriptionEntity,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconCircle(iconKey = sub.icon, color = Color(sub.color), size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sub.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    "${frequencyLabel(sub.frequency, sub.interval)} · ${sub.nextChargeDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                MoneyText(
                    money = Money(sub.amountMinor, CurrencyCode(sub.currencyCode)),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Switch(checked = sub.active, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun frequencyLabel(frequency: SubscriptionFrequency, interval: Int): String {
    val every = if (interval > 1) stringResource(R.string.freq_interval_prefix, interval) else ""
    return when (frequency) {
        SubscriptionFrequency.DAILY -> every + stringResource(R.string.freq_daily)
        SubscriptionFrequency.WEEKLY -> every + stringResource(R.string.freq_weekly)
        SubscriptionFrequency.MONTHLY -> every + stringResource(R.string.freq_monthly)
        SubscriptionFrequency.YEARLY -> every + stringResource(R.string.freq_yearly)
    }
}
