package com.personalmoney.core.domain.model

/**
 * A currency the user can hold money in. See [Account] for why this is not the Room entity.
 *
 * [decimals] is the reason this type matters rather than a bare code string: it decides how
 * every amount in that currency is scaled and rendered, and it is not always two - JPY is
 * zero, BHD is three, and a user can add a crypto with eight.
 */
data class Currency(
    val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int = 2,
    val active: Boolean = false,
    val sortOrder: Int = 0,
    /** Server id, where there is one. Room keys on [code]; the REST API keys on this. */
    val serverId: Long? = null,
)
