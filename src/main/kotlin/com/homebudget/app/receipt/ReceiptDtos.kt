package com.homebudget.app.receipt

import java.time.LocalDate

/** The answer to an upload: the receipt is being read; poll `GET /api/receipts/{uuid}`. */
data class ReceiptAccepted(val uuid: String, val status: ReceiptStatus)

data class ReceiptItemResponse(
    val name: String,
    /** Decimal string; absent when the receipt did not say. */
    val quantity: String? = null,
    /** Decimal string in the receipt's currency. */
    val price: String
)

/**
 * What the model read, checked against the household's catalogue. Amounts are decimal
 * strings so no client rounds them through a float.
 */
data class ReceiptResponse(
    val uuid: String,
    val status: ReceiptStatus,
    val failureReason: String? = null,
    val merchant: String? = null,
    val date: LocalDate? = null,
    /** Only a code from the currency catalogue; otherwise absent. */
    val currency: String? = null,
    val total: String? = null,
    /** The items do not add up to the total by more than a minor unit per item. */
    val totalMismatch: Boolean = false,
    val suggestedCategoryId: Long? = null,
    /** For the offline-first Android client, which knows categories by uuid. */
    val suggestedCategoryUuid: String? = null,
    val items: List<ReceiptItemResponse> = emptyList()
)
