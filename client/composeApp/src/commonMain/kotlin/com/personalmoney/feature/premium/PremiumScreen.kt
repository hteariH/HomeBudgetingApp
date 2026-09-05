package com.personalmoney.feature.premium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.billing.StoreProduct
import com.personalmoney.core.domain.formatMediumDate
import com.personalmoney.core.ui.pageBarColors
import com.personalmoney.feature.settings.rememberUrlOpener
import com.personalmoney.resources.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * What premium is, what this account has, and - on Android - how to buy it.
 *
 * One screen for both platforms rather than two, because the difference between them is
 * one card: the browser cannot buy anything and says where to instead. Splitting it would
 * have duplicated the entitlement half, which is the half that has to agree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    onBack: () -> Unit,
    viewModel: PremiumViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val openUrl = rememberUrlOpener()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pageBarColors(),
                title = { Text(stringResource(Res.string.premium_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (state.premium) StatusCard(state) else Pitch()

            Benefits()

            when {
                state.loading -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }

                // A browser: there is no store here and never will be, but there is
                // somewhere else to go, and saying so beats a button that cannot work.
                !state.storeHere -> ElsewhereCard()

                // A phone whose server has no Play service account. Nothing the user can
                // do, so it says that rather than sending them to the phone they are on.
                !state.canBuyHere -> Text(
                    text = stringResource(Res.string.premium_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                state.offers.isEmpty() -> Text(
                    text = stringResource(Res.string.premium_no_offers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.offers.forEach { offer ->
                        OfferButton(
                            offer = offer,
                            busy = state.buying == offer.id,
                            enabled = state.buying == null,
                            onClick = { viewModel.buy(offer.id) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Offered whatever the current state says: somebody who paid on another
                // device sees "free" here, and this is the button they are looking for.
                TextButton(
                    onClick = viewModel::restore,
                    enabled = !state.restoring
                ) { Text(stringResource(Res.string.premium_restore)) }

                if (state.premium && !state.granted) {
                    TextButton(onClick = { openUrl(PLAY_SUBSCRIPTIONS_URL) }) {
                        Text(stringResource(Res.string.premium_manage))
                        Spacer(Modifier.size(6.dp))
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatusCard(state: PremiumUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(Res.string.premium_active),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = state.statusLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * The line under "Premium is on".
 *
 * Four states, and they are genuinely different things to say: a grant that never ends,
 * a subscription that renews, one that has been cancelled and is running out, and a grant
 * with a date on it. Collapsing them into "active until X" would tell a subscriber their
 * subscription is about to end every month.
 */
@Composable
private fun PremiumUiState.statusLine(): String {
    val date = expiresAt?.asLocalDate()
    return when {
        date == null -> stringResource(Res.string.premium_active_forever)
        lapsing -> stringResource(Res.string.premium_lapsing, date)
        granted -> stringResource(Res.string.premium_active_until, date)
        else -> stringResource(Res.string.premium_renews_on, date)
    }
}

@Composable
private fun Pitch() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Res.string.premium_pitch_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(Res.string.premium_pitch_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Benefits() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Benefit(stringResource(Res.string.premium_benefit_accounts))
        Benefit(stringResource(Res.string.premium_benefit_everywhere))
        Benefit(stringResource(Res.string.premium_benefit_support))
    }
}

@Composable
private fun Benefit(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ElsewhereCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(Res.string.premium_buy_elsewhere_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(Res.string.premium_buy_elsewhere_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OfferButton(
    offer: StoreProduct,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(offer.label())
        }
    }
}

/**
 * "€2.99 per month".
 *
 * The price is the store's own string - it is the only thing that knows the local price,
 * currency and how that currency is written where the user is. Only the period is
 * translated, and only for the two periods this actually sells; anything else falls back
 * to the product's own name rather than guessing at an ISO period.
 */
@Composable
private fun StoreProduct.label(): String = when (billingPeriod) {
    "P1M" -> "$formattedPrice ${stringResource(Res.string.premium_per_month)}"
    "P1Y" -> "$formattedPrice ${stringResource(Res.string.premium_per_year)}"
    else -> listOf(title, formattedPrice).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun Instant.asLocalDate(): String =
    formatMediumDate(toLocalDateTime(TimeZone.currentSystemDefault()).date)

/**
 * Play's own subscription list. Deliberately without `sku`/`package` parameters: the
 * debug build has a different application id, and a link that is wrong half the time is
 * worse than one that lands a tap away.
 */
private const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"
