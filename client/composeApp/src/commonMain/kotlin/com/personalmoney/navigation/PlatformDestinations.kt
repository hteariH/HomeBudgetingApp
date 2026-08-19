package com.personalmoney.navigation

/**
 * Whether this platform can reach the bank screen.
 *
 * The screen and its ViewModel are ordinary shared code over the shared
 * `BankRepository`, and nothing about them is web-specific. What is web-specific is the
 * repository: bank credentials live on the server and are never sent to a device, and the
 * phone reads its ledger out of Room, so an import would not appear there until the next
 * sync. Only the web graph binds an implementation.
 *
 * Registering the route regardless would put a crash one tap away, so the platform says
 * what it can reach and the nav host adds only that. A boolean rather than a list of
 * destinations because there is exactly one such screen; when there are two, this becomes
 * a list and the type stops being a lie.
 */
internal expect val bankConnectionsAvailable: Boolean
