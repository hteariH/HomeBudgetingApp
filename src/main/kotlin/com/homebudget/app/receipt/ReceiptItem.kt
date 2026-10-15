package com.homebudget.app.receipt

import jakarta.persistence.*
import java.math.BigDecimal

/** One line of a receipt, in the receipt's currency's minor units. */
@Entity
@Table(name = "receipt_items")
class ReceiptItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    var receipt: Receipt,

    @Column(nullable = false)
    var position: Int,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(precision = 14, scale = 3)
    var quantity: BigDecimal? = null,

    @Column(name = "price_minor", nullable = false)
    var priceMinor: Long
)
