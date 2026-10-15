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
    const val SUBSCRIPTIONS = "subscriptions"
    const val SUBSCRIPTION_EDIT = "subscription/edit?subscriptionId={subscriptionId}"
    const val SETTINGS = "settings"
    const val FAMILY = "family"
    const val CURRENCIES = "currencies"
    const val BANKS = "banks"
    const val IMPORT_HOMEMONEY = "import/homemoney"
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

    /**
     * Whether [route] is one [patterns] would accept - the question the browser's address
     * bar raises and the nav controller does not answer.
     *
     * `navigate()` with an unregistered route throws, and the two-way URL binding swallows
     * that: a typed or stale fragment left the screen alone and the address bar wrong. The
     * platform that has an address bar asks this before handing anything to the controller;
     * see `BrowserUrl.wasmJs.kt`.
     *
     * Matching is by shape, not by value. A pattern segment in braces is the argument
     * placeholder and stands for any one segment, and the query string is dropped on both
     * sides, because arguments there are optional and defaulted - `account/edit` and
     * `account/edit?accountId=3` are the same destination.
     */
    fun isKnown(route: String, patterns: List<String>): Boolean {
        val path = route.substringBefore('?').trim('/')
        if (path.isEmpty()) return false
        val segments = path.split('/')
        return patterns.any { pattern ->
            val expected = pattern.substringBefore('?').trim('/').split('/')
            expected.size == segments.size &&
                expected.indices.all { i ->
                    expected[i].startsWith('{') || expected[i] == segments[i]
                }
        }
    }
}
