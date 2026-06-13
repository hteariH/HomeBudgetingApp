package com.personalmoney.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val QUICK_ADD = "quickadd?type={type}&categoryId={categoryId}"
    const val QUICK_ADD_BASE = "quickadd"
    const val TRANSACTIONS = "transactions"
    const val TRANSACTION_EDIT = "transaction/edit/{transactionId}"
    const val ACCOUNTS = "accounts"
    const val ACCOUNT_EDIT = "account/edit?accountId={accountId}"
    const val CATEGORIES = "categories"
    const val CATEGORY_EDIT = "category/edit?categoryId={categoryId}&type={type}"
    const val TRANSFER = "transfer"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SUBSCRIPTION_EDIT = "subscription/edit?subscriptionId={subscriptionId}"
    const val SETTINGS = "settings"
    const val CURRENCIES = "currencies"
    const val CURRENCY_EDIT = "currency/edit?code={code}"
    const val ONBOARDING = "onboarding"
    const val ANALYTICS = "analytics"
    const val MORE = "more"
    const val ACCOUNT_PICKER = "account/picker?selectedId={selectedId}"

    /** Key used to return the picked account id to the previous screen's SavedStateHandle. */
    const val RESULT_PICKED_ACCOUNT_ID = "picked_account_id"

    fun quickAdd(type: String = "EXPENSE", categoryId: Long? = null): String {
        val params = buildList {
            add("type=$type")
            categoryId?.let { add("categoryId=$it") }
        }.joinToString("&")
        return "$QUICK_ADD_BASE?$params"
    }

    fun transactionEdit(id: Long) = "transaction/edit/$id"
    fun accountEdit(id: Long? = null) = "account/edit" + (id?.let { "?accountId=$it" } ?: "")
    fun categoryEdit(id: Long? = null, type: String = "EXPENSE") =
        "category/edit?type=$type" + (id?.let { "&categoryId=$it" } ?: "")
    fun subscriptionEdit(id: Long? = null) =
        "subscription/edit" + (id?.let { "?subscriptionId=$it" } ?: "")
    fun currencyEdit(code: String? = null) =
        "currency/edit" + (code?.let { "?code=$it" } ?: "")

    fun accountPicker(selectedId: Long? = null) =
        "account/picker" + (selectedId?.let { "?selectedId=$it" } ?: "")
}
