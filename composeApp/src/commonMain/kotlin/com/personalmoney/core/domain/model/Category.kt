package com.personalmoney.core.domain.model

import com.personalmoney.core.domain.CategoryType

/**
 * A spending or income category. See [Account] for why this is not the Room entity.
 *
 * [budgetLimitMinor] is in *base-currency* minor units, not the category's own - a budget
 * spans accounts that may be in different currencies, so it only means anything once
 * converted to the user's base.
 *
 * `id = 0` with a blank uuid means "not persisted yet". A screen building one for
 * [com.personalmoney.core.data.CategoryRepository.create] cannot invent an identity: on Android
 * the id comes from Room's autoincrement and the uuid is minted by the repository, and on
 * the web the server assigns both.
 */
data class Category(
    val id: Long = 0L,
    val uuid: String = "",
    val name: String,
    val type: CategoryType,
    val icon: String,
    val color: Long,
    val parentId: Long? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val budgetLimitMinor: Long? = null,
)
