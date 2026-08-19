package com.personalmoney.core.domain.model

/**
 * An account, as the rest of the app thinks about one.
 *
 * Deliberately not the Room entity. Room has no WebAssembly support, so anything the
 * shared UI touches cannot carry `@Entity` on it - the web client would fail to compile.
 * Android maps its Room rows onto this, the web client maps REST responses onto it, and
 * screens above see one type either way.
 *
 * [balanceMinor] is derived, never stored: on Android it is summed from the ledger, on the
 * web the server has already done that sum. Storing it would let the two drift.
 *
 * `id = 0` with a blank uuid means "not persisted yet". A screen building one for
 * [com.personalmoney.core.data.AccountRepository.create] cannot invent an identity: on Android
 * the id comes from Room's autoincrement and the uuid is minted by the repository, and on
 * the web the server assigns both.
 */
data class Account(
    val id: Long = 0L,
    val uuid: String = "",
    val name: String,
    val currencyCode: String,
    /** The opening balance the user typed. Zero unless they said otherwise. */
    val initialBalanceMinor: Long = 0L,
    /** Derived, and zero on a model a screen just built - only a store can know it. */
    val balanceMinor: Long = 0L,
    val icon: String = "wallet",
    val color: Long = 0xFF3D4499L,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val includeInTotal: Boolean = true,
    val note: String? = null,
)
