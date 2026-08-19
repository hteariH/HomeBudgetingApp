package com.personalmoney.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How wide content is allowed to get.
 *
 * A phone layout stretched across a 1600px window is not a wide layout, it is a broken one:
 * a row whose label sits at the left edge and whose amount sits 1500px away is harder to
 * read than the same row on a phone. The React client solved this with page padding and
 * grids that reflow; the Compose equivalent is to cap the column and centre it, which is
 * what these do.
 *
 * Two caps rather than one, because the two shapes want different things. A list or a
 * dashboard reads well over a wide column. A form does not - a text field a thousand pixels
 * wide invites nothing - so forms stay near the 480px the React client used for its modals.
 */
object PageWidth {
    /**
     * Lists, dashboards, anything tabular.
     *
     * Wide enough for the dashboard's two columns to each be worth having - below about
     * this the balance column stops fitting account names and the split is worse than no
     * split. The React client is full-bleed, so this is if anything the more conservative
     * of the two.
     */
    val Content: Dp = 1400.dp

    /** Forms, sign-in, single-column editors. Matches the React `.modal` cap. */
    val Form: Dp = 480.dp

    /**
     * Where a transaction form fits inline, beside everything else on the page.
     *
     * Below this a full screen is the better shape and a floating button is the right way
     * to reach it; above it, sending someone to another screen to type four fields is a
     * navigation step that buys nothing. One number, shared, so the overview and the
     * ledger never disagree about which layout they are in.
     */
    val InlineEntry: Dp = 900.dp
}

/**
 * Centres [content] and stops it growing past [maxWidth].
 *
 * A no-op on a phone, where the window is narrower than the cap anyway - so screens keep
 * exactly the layout they have on Android.
 */
@Composable
fun PageContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = PageWidth.Content,
    horizontalPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            content = content,
        )
    }
}
