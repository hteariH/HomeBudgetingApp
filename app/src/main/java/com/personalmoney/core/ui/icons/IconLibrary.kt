package com.personalmoney.core.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Payments
import androidx.compose.ui.graphics.vector.ImageVector

object IconLibrary {
    private val map: Map<String, ImageVector> = buildMap {
        put("wallet", Icons.Filled.AccountBalanceWallet)
        put("bank", Icons.Filled.AccountBalance)
        put("card", Icons.Filled.CreditCard)
        put("cash", Icons.Filled.AttachMoney)
        put("savings", Icons.Filled.Savings)
        put("currency_exchange", Icons.Filled.CurrencyExchange)

        put("restaurant", Icons.Filled.Restaurant)
        put("directions_car", Icons.Filled.DirectionsCar)
        put("home", Icons.Filled.Home)
        put("bolt", Icons.Filled.Bolt)
        put("movie", Icons.Filled.Movie)
        put("favorite", Icons.Filled.Favorite)
        put("shopping_bag", Icons.Filled.ShoppingBag)
        put("local_cafe", Icons.Filled.LocalCafe)
        put("school", Icons.Filled.School)
        put("flight", Icons.Filled.Flight)
        put("subscriptions", Icons.Filled.Subscriptions)
        put("redeem", Icons.Filled.Redeem)
        put("pets", Icons.Filled.Pets)
        put("fitness_center", Icons.Filled.FitnessCenter)
        put("spa", Icons.Filled.Spa)
        put("account_balance", Icons.Filled.AccountBalance)
        put("more_horiz", Icons.Filled.MoreHoriz)
        put("payments", Icons.Filled.Payments)
        put("work", Icons.Filled.Work)
        put("stars", Icons.Filled.Stars)
        put("trending_up", Icons.Filled.TrendingUp)
        put("undo", Icons.Filled.Undo)
    }

    val pickerSet: List<String> = listOf(
        "wallet", "bank", "card", "cash", "savings", "currency_exchange",
        "restaurant", "directions_car", "home", "bolt", "movie", "favorite",
        "shopping_bag", "local_cafe", "school", "flight", "subscriptions", "redeem",
        "pets", "fitness_center", "spa", "account_balance", "payments", "work", "stars",
        "trending_up", "undo"
    )

    fun get(key: String?): ImageVector =
        key?.let { map[it] } ?: Icons.Filled.MoreHoriz
}
