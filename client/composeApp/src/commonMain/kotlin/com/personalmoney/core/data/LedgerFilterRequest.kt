package com.personalmoney.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Open the ledger, showing this account."
 *
 * One screen asks and another answers, so the request has to live somewhere both can see.
 * The obvious alternative was a navigation argument, and it was tried first: it made the
 * filtered ledger a destination pushed onto whichever tab asked for it, which meant the
 * overview's saved back stack now contained the ledger - and the next tap on Overview
 * dutifully restored it, looking for all the world like a dead navigation item.
 *
 * Routing is the wrong tool because this is not a place; it is a *filter* on a place that
 * already exists as a tab. Expressed as state, the navigation stays an ordinary tab switch
 * and cannot interact with saved stacks at all.
 *
 * Read once and cleared: it is a request, not a setting. Leaving it set would re-apply the
 * filter every time the ledger came back into view, overriding whatever the user had since
 * chosen there.
 */
class LedgerFilterRequest {

    private val _accountId = MutableStateFlow<Long?>(null)

    /** Null when nothing has been requested, or when the ledger has already taken it. */
    val accountId: StateFlow<Long?> = _accountId.asStateFlow()

    fun requestAccount(id: Long) { _accountId.value = id }

    fun consume() { _accountId.value = null }
}
