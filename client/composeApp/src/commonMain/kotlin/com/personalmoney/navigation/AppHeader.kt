package com.personalmoney.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalmoney.core.ui.theme.Accent
import com.personalmoney.core.ui.theme.StatusCyan
import com.personalmoney.core.ui.theme.StatusPurple
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The site header: brand, sections, and the account.
 *
 * This is the piece that made the browser build stop reading as a phone app pinned to the
 * left of a desktop window. The screens never changed - what changed is that the frame
 * around them is now the one a website wears: the sections sit across the top rather than
 * in a rail, the theme switch and the account are where a site puts them, and below
 * [HEADER_NAV_BREAKPOINT] the sections fold into a drawer instead of a bottom bar.
 *
 * Signed out it still draws, reduced to the brand and the theme switch: the sign-in page is
 * a page of the site, and stripping the site's frame off it made it look like a modal that
 * had lost its app.
 */
@Composable
internal fun AppHeader(
    destinations: List<NavDestination>,
    currentRoute: String?,
    /** Sections in the bar. When false they live behind the menu button instead. */
    navInline: Boolean,
    /**
     * The name beside the mark. On a phone-width window it is the first thing to go: the
     * menu button, the mark, the entry button, the theme switch and the account together
     * come to more than 400dp with it, and the row ran off the right edge - the account
     * chip, the one control that cannot be reached any other way, being the piece that
     * fell off. The drawer still spells the name out in full.
     */
    showWordmark: Boolean,
    onNavigate: (String) -> Unit,
    onOpenMenu: () -> Unit,
    /** Null when signed out - there is no account to offer, only a page to sign in on. */
    email: String?,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    /**
     * Null where the page carries its own entry panel. A wide window has the form on the
     * overview and on the ledger; a narrow one has neither, and used to reach the form
     * through a floating button that went with the phone chrome - so the header takes it
     * over rather than leaving no way to record a transaction.
     */
    onAdd: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .height(60.dp)
                .padding(horizontal = if (navInline) 24.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!navInline && destinations.isNotEmpty()) {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Filled.Menu, stringResource(Res.string.nav_open_menu))
                }
            }

            BrandMark(
                showWordmark = showWordmark,
                onClick = if (currentRoute != Routes.DASHBOARD) { { onNavigate(Routes.DASHBOARD) } } else null,
            )

            if (navInline) {
                Spacer(Modifier.width(12.dp))
                // Scrolls rather than wraps or clips. Everything fits at this breakpoint in
                // English, but Ukrainian labels are longer, and a header that grows a second
                // row shifts the whole page down as the window is resized.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEach { item ->
                        HeaderNavLink(
                            label = stringResource(item.label),
                            selected = currentRoute == item.route,
                            onClick = { onNavigate(item.route) },
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (onAdd != null) {
                FilledIconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, stringResource(Res.string.action_add_expense_fab))
                }
            }

            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = stringResource(
                        if (dark) Res.string.header_to_light else Res.string.header_to_dark
                    ),
                )
            }

            if (email != null) {
                AccountChip(
                    email = email,
                    showName = navInline,
                    onOpenSettings = onOpenSettings,
                    onSignOut = onSignOut,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The sections, for a window too narrow to wear them across the top.
 *
 * Same list and same tap handling as the header - only the shape differs, which is what
 * keeps "which section am I in" answerable the same way at every width.
 */
@Composable
internal fun AppDrawerSheet(
    destinations: List<NavDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
) {
    ModalDrawerSheet {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(
                showWordmark = true,
                onClick = if (currentRoute != Routes.DASHBOARD) { { onNavigate(Routes.DASHBOARD); onClose() } } else null,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, stringResource(Res.string.nav_close_menu))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = stringResource(Res.string.nav_menu),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
        )

        destinations.forEach { item ->
            NavigationDrawerItem(
                label = { Text(stringResource(item.label)) },
                icon = { Icon(item.icon, contentDescription = null) },
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

/**
 * The wordmark, in the shape the React client drew it: a gradient tile with the product's
 * mark, then the name with the second half in the accent.
 *
 * White on the tile rather than a theme slot - the tile is a saturated gradient in both
 * themes, so the text on it does not follow the surface it sits over.
 */
@Composable
private fun BrandMark(
    showWordmark: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = if (onClick != null) {
            Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick)
        } else {
            Modifier
        },
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(Accent, StatusCyan))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.TrendingUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }

        if (showWordmark) Row {
            Text(
                text = "Personal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Money",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HeaderNavLink(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            // The React client's `--accent-glow`: the accent itself, faded, rather than a
            // separate token. `primary` is already the per-theme accent, so one alpha reads
            // correctly on both a near-black and a white header.
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * Who is signed in, and the two things one does about it.
 *
 * Settings is here as well as in the sections list because this is where a site keeps it -
 * and it is the reason the header's own row does not carry a Settings link, which would
 * have been the ninth item competing for width.
 */
@Composable
private fun AccountChip(
    email: String,
    showName: Boolean,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape,
            modifier = Modifier
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable { open = true },
        ) {
            Row(
                modifier = Modifier.padding(
                    start = 4.dp,
                    end = if (showName) 14.dp else 4.dp,
                    top = 4.dp,
                    bottom = 4.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent, StatusPurple))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = if (showName) null
                        else stringResource(Res.string.header_user),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }

                if (showName) {
                    Text(
                        // The local part, as the React header shows it. The whole address
                        // is a line away, in the menu.
                        text = email.substringBefore('@'),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                text = email,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            DropdownMenuItem(
                text = { Text(stringResource(Res.string.dashboard_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = {
                    open = false
                    onOpenSettings()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(Res.string.header_sign_out),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    open = false
                    onSignOut()
                },
            )
        }
    }
}
