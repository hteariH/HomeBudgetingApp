package com.personalmoney.navigation

import androidx.compose.runtime.Composable

/**
 * Hands the browser to the site's sign-in page, carrying the screen it was asked for.
 *
 * Two things this has to get right, both of which the plain page navigator got wrong when
 * the signed-out branch borrowed it:
 *
 * - It **replaces** rather than pushes. Pushing put `/app/` behind `/login` in the
 *   browser's history, and `/app/` signed out only sends you to `/login` again - so Back
 *   from the sign-in page spent a seven-megabyte bundle load to arrive back where it
 *   started, and there was no way out of the sign-in page except forward. The visitor came
 *   from somewhere; leave that somewhere reachable.
 * - It carries the fragment as `?next=`. A bookmark on `/app/#accounts` whose session has
 *   expired should come back to the accounts screen, not to the overview. The site writes
 *   the tokens and hands the tab back (see the React `session.ts`), so it is the one that
 *   has to know where the visitor was going.
 *
 * A composable of its own rather than a call to `rememberPageNavigator`, because that one
 * is the OAuth hand-off - consent has to be able to come back, so it pushes on purpose.
 *
 * On Android there is no address bar and no site to hand the tab to; the signed-out branch
 * draws the app's own form instead and never calls this.
 */
@Composable
internal expect fun LeaveForSignIn()
