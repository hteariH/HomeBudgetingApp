package com.homebudget.app.receipt

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.User
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class ReceiptStatus { PENDING, READY, FAILED }

/**
 * A photo of a receipt and what the household's model read off it.
 *
 * It lives until a transaction is saved with it (then it goes when that transaction is
 * deleted) or, unlinked, for a day - see [ReceiptCleanup]. Amounts are minor units in
 * [currencyCode], or in the household's base currency when the model named none we know.
 */
@Entity
@Table(name = "receipts")
class Receipt(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36, updatable = false)
    val uuid: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    var household: Household,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    var createdBy: User? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReceiptStatus = ReceiptStatus.PENDING,

    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null,

    @Column(length = 255)
    var merchant: String? = null,

    @Column(name = "receipt_date")
    var receiptDate: LocalDate? = null,

    @Column(name = "currency_code", length = 10)
    var currencyCode: String? = null,

    @Column(name = "total_minor")
    var totalMinor: Long? = null,

    @Column(name = "total_mismatch", nullable = false)
    var totalMismatch: Boolean = false,

    /** An id rather than an association: a category deleted later must not break reading this. */
    @Column(name = "suggested_category_id")
    var suggestedCategoryId: Long? = null,

    /** Relative to `app.data-dir`, so moving the data directory keeps the photos. */
    @Column(name = "image_path", nullable = false, length = 500)
    var imagePath: String,

    @OneToMany(mappedBy = "receipt", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position")
    val items: MutableList<ReceiptItem> = mutableListOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
