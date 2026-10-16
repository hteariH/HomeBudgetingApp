package com.homebudget.app.receipt

import com.homebudget.app.ai.AiProviderSettings
import com.homebudget.app.domain.Household
import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** No model is configured for this household, nor for the instance. */
class ReceiptScanUnavailableException : RuntimeException("Receipt recognition is not set up")

class ReceiptTooLargeException : RuntimeException("The photo is larger than 15 MB")

/** The receipt belongs to another transaction already: one receipt, one transaction. */
class ReceiptAlreadyLinkedException : IllegalArgumentException("This receipt is already saved with another transaction")

@Service
class ReceiptService(
    private val receipts: ReceiptRepository,
    private val images: ReceiptImages,
    private val reader: ReceiptReader,
    private val settings: AiProviderSettings,
    private val categories: CategoryRepository,
    private val currencies: CurrencyRepository,
    private val securityUtils: SecurityUtils,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager
) {
    companion object {
        const val MAX_UPLOAD_BYTES = 15L * 1024 * 1024
    }

    private val tx = TransactionTemplate(transactionManager)

    /**
     * Keeps the photo and queues it for reading. Not one transaction: decoding and scaling
     * take a while and hold no database connection; only saving the row is transactional,
     * and the reader is handed the receipt once that row is committed.
     */
    fun upload(image: MultipartFile): Receipt {
        val household = securityUtils.getCurrentHousehold()
        settings.resolveFor(household.id) ?: throw ReceiptScanUnavailableException()
        // Defensive only: Spring's multipart limit (MaxUploadSizeExceededException) refuses larger uploads first.
        if (image.size > MAX_UPLOAD_BYTES) throw ReceiptTooLargeException()

        val uuid = UUID.randomUUID().toString()
        val path = images.store(image.bytes, household.uuid, uuid)
        val now = Instant.now(clock)
        val receipt = try {
            tx.execute {
                receipts.save(
                    Receipt(
                        uuid = uuid,
                        household = household,
                        createdBy = securityUtils.getCurrentUser(),
                        imagePath = path,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }!!
        } catch (e: RuntimeException) {
            images.delete(path)
            throw e
        }
        reader.submit(receipt.id)
        return receipt
    }

    @Transactional(readOnly = true)
    fun get(uuid: String): ReceiptResponse {
        val household = securityUtils.getCurrentHousehold()
        return toResponse(find(uuid, household), household)
    }

    @Transactional(readOnly = true)
    fun image(uuid: String): ByteArray {
        val receipt = find(uuid, securityUtils.getCurrentHousehold())
        return images.read(receipt.imagePath) ?: throw NoSuchElementException("Receipt photo not found")
    }

    /** The household's receipt, or null - for sync, which reports rather than throws. */
    fun lookUp(uuid: String, householdId: Long): Receipt? = receipts.findByUuidAndHouseholdId(uuid, householdId)

    /**
     * True when a transaction other than [transactionUuid] (null: any) already holds [receipt].
     * Sees links made earlier in the same transaction: a new transaction row is inserted on
     * save (identity ids), and Hibernate flushes pending updates before a native query.
     */
    fun isLinkedElsewhere(receipt: Receipt, transactionUuid: String?): Boolean =
        (if (transactionUuid == null) receipts.countLinks(receipt.id) else receipts.countLinksExcept(receipt.id, transactionUuid)) > 0

    /**
     * The receipt [uuid] for a REST create or update of [transactionUuid].
     * @throws NoSuchElementException (404) when the household has no such receipt
     * @throws ReceiptAlreadyLinkedException (400) when another transaction holds it
     */
    fun linkable(uuid: String, household: Household, transactionUuid: String?): Receipt {
        val receipt = lookUp(uuid, household.id) ?: throw NoSuchElementException("Receipt not found: $uuid")
        if (isLinkedElsewhere(receipt, transactionUuid)) throw ReceiptAlreadyLinkedException()
        return receipt
    }

    /** Deletes the row now and its photo once that is committed. */
    fun deleteWithPhoto(receipt: Receipt) {
        val path = receipt.imagePath
        receipts.delete(receipt)
        images.deleteAfterCommit(path)
    }

    /** [deleteWithPhoto] by id; a receipt already gone is fine. */
    fun deleteWithPhoto(receiptId: Long) {
        receipts.findById(receiptId).ifPresent(::deleteWithPhoto)
    }

    private fun find(uuid: String, household: Household): Receipt =
        receipts.findByUuidAndHouseholdId(uuid, household.id) ?: throw NoSuchElementException("Receipt not found: $uuid")

    private fun toResponse(receipt: Receipt, household: Household): ReceiptResponse {
        val decimals = receipt.currencyCode?.let { currencies.findByCode(it)?.decimals }
            ?: household.baseCurrency?.decimals ?: 2
        fun decimal(minor: Long) = MinorUnits.toDecimal(minor, decimals).toPlainString()
        val category = receipt.suggestedCategoryId?.let { categories.findByIdAndHousehold(it, household) }
        return ReceiptResponse(
            uuid = receipt.uuid,
            status = receipt.status,
            failureReason = receipt.failureReason,
            merchant = receipt.merchant,
            date = receipt.receiptDate,
            currency = receipt.currencyCode,
            total = receipt.totalMinor?.let(::decimal),
            totalMismatch = receipt.totalMismatch,
            suggestedCategoryId = category?.id,
            suggestedCategoryUuid = category?.uuid,
            items = receipt.items.map {
                ReceiptItemResponse(
                    name = it.name,
                    quantity = it.quantity?.stripTrailingZeros()?.toPlainString(),
                    price = decimal(it.priceMinor)
                )
            }
        )
    }
}
