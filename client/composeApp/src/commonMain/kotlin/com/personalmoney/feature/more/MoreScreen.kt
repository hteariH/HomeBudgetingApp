package com.personalmoney.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenTransfer: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.more_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            MoreItem(Icons.Filled.CompareArrows, Res.string.dashboard_transfer, onOpenTransfer)
            MoreItem(Icons.Filled.Wallet, Res.string.dashboard_accounts, onOpenAccounts)
            MoreItem(Icons.Filled.Category, Res.string.dashboard_categories, onOpenCategories)
            MoreItem(Icons.Filled.Subscriptions, Res.string.dashboard_subscriptions, onOpenSubscriptions)
            MoreItem(Icons.Filled.WorkspacePremium, Res.string.premium_menu, onOpenPremium)
            MoreItem(Icons.Filled.Settings, Res.string.dashboard_settings, onOpenSettings)
        }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, labelRes: StringResource, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
