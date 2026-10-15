package com.homebudget.app.receipt

import com.homebudget.app.ai.AiProviderSettings
import com.homebudget.app.ai.ExtractedReceipt
import com.homebudget.app.ai.ExtractionContext
import com.homebudget.app.ai.ExtractionException
import com.homebudget.app.ai.ExtractorFactory
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.UserCurrencySettingRepository
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Reads uploaded receipts in the background, at most `AI_CONCURRENCY` at a time; the
 * rest wait in an unbounded queue (each entry holds only a receipt id).
 *
 * A job never holds a database transaction while the model is thinking: it reads what
 * it needs, lets go, waits for the model, and writes the outcome in a new one.
 * Nothing here logs the photo or the model's reply.
 */
@Component
class ReceiptReader(
    private val receipts: ReceiptRepository,
    private val images: ReceiptImages,
    private val settings: AiProviderSettings,
    private val extractors: ExtractorFactory,
    private val categories: CategoryRepository,
    private val currencySettings: UserCurrencySettingRepository,
    private val currencies: CurrencyRepository,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager
) {
    companion object {
        const val NO_PROVIDER = "No model is set up to read receipts"
        const val INTERRUPTED = "Reading was interrupted — scan again"

        /** NUMERIC(14,3): eleven digits before the point. */
        private val MAX_QUANTITY = BigDecimal("99999999999.999")
    }

    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)
    private val inFlight = AtomicInteger()
    private val threadNumber = AtomicInteger()

    /** Anything PENDING from before this was queued by a previous run and will never be read. */
    private val startedAt: Instant = Instant.now(clock)

    private val executor = settings.concurrency.coerceAtLeast(1).let { n ->
        ThreadPoolExecutor(n, n, 60, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
            Thread(task, "receipt-reader-${threadNumber.incrementAndGet()}").apply { isDaemon = true }
        }.apply { allowCoreThreadTimeOut(true) }
    }

    private data class Job(val householdId: Long, val imagePath: String, val context: ExtractionContext)

    private sealed interface Outcome {
        data class Read(val receipt: ExtractedReceipt) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /** Queues [receiptId]; call once its row is committed. */
    fun submit(receiptId: Long) {
        inFlight.incrementAndGet()
        try {
            executor.execute {
                try {
                    read(receiptId)
                } finally {
                    inFlight.decrementAndGet()
                }
            }
        } catch (e: RuntimeException) {
            inFlight.decrementAndGet()
            throw e
        }
    }

    /** For tests: waits until every submitted receipt has been read. */
    fun awaitIdle(timeout: Duration): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (inFlight.get() > 0) {
            if (System.nanoTime() > deadline) return false
            Thread.sleep(20)
        }
        return true
    }

    /**
     * The queue lives in memory, so a restart loses what was waiting in it. Those receipts
     * are failed rather than left PENDING, where a client polling them would wait forever.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun failInterrupted() {
        val failed = tx.execute { receipts.failPendingBefore(startedAt, INTERRUPTED, Instant.now(clock)) } ?: 0
        if (failed > 0) log.info("Marked {} receipts interrupted by a restart as failed", failed)
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    private fun read(receiptId: Long) {
        val outcome = try {
            val job = tx.execute { load(receiptId) } ?: return
            val provider = settings.resolveFor(job.householdId) ?: throw ExtractionException(NO_PROVIDER)
            val jpeg = images.read(job.imagePath) ?: throw ExtractionException(ExtractionException.UNREADABLE)
            Outcome.Read(extractors.create(provider, settings.timeout).extract(jpeg, job.context))
        } catch (e: ExtractionException) {
            Outcome.Failed(e.reason)
        } catch (e: Exception) {
            log.warn("Reading receipt {} failed unexpectedly: {}", receiptId, e.javaClass.name)
            Outcome.Failed(ExtractionException.UNREADABLE)
        }
        try {
            tx.executeWithoutResult { write(receiptId, outcome) }
        } catch (e: Exception) {
            log.warn("Saving what was read off receipt {} failed: {}", receiptId, e.javaClass.name)
            runCatching { tx.executeWithoutResult { write(receiptId, Outcome.Failed(ExtractionException.UNREADABLE)) } }
                .onFailure { log.warn("Marking receipt {} failed did not work either: {}", receiptId, it.javaClass.name) }
        }
    }

    private fun load(receiptId: Long): Job? {
        val receipt = receipts.findById(receiptId).orElse(null) ?: return null
        val household = receipt.household
        val categoryNames = categories.findByHouseholdAndType(household, CategoryType.EXPENSE)
            .filter { !it.archived }
            .map { it.name }
        val active = currencySettings.findAllByHousehold(household).filter { it.active }.map { it.currency.code }
        val codes = active.ifEmpty { currencies.findAll().sortedBy { it.sortOrder }.map { it.code } }
        return Job(household.id, receipt.imagePath, ExtractionContext(categoryNames, codes))
    }

    /** Writes the outcome; a receipt deleted while it was being read stays deleted. */
    private fun write(receiptId: Long, outcome: Outcome) {
        val receipt = receipts.findById(receiptId).orElse(null) ?: return
        when (outcome) {
            is Outcome.Failed -> {
                receipt.status = ReceiptStatus.FAILED
                receipt.failureReason = outcome.reason.take(500)
            }
            is Outcome.Read -> {
                fill(receipt, outcome.receipt)
                receipt.status = ReceiptStatus.READY
                receipt.failureReason = null
            }
        }
        receipt.updatedAt = Instant.now(clock)
        receipts.save(receipt)
    }

    /** Keeps only what checks out against the currency catalogue and the household. */
    private fun fill(receipt: Receipt, read: ExtractedReceipt) {
        val household = receipt.household
        val currency = read.currency?.trim()?.uppercase()?.let { currencies.findByCode(it) }
        val decimals = currency?.decimals ?: household.baseCurrency?.decimals ?: 2

        receipt.merchant = read.merchant?.trim()?.take(255)?.ifBlank { null }
        receipt.receiptDate = read.date
        receipt.currencyCode = currency?.code
        receipt.totalMinor = read.total?.let { toMinorOrNull(it, decimals) }

        val items = mutableListOf<ReceiptItem>()
        for (item in read.items) {
            val name = item.name.trim().take(255)
            val price = toMinorOrNull(item.price, decimals)
            if (name.isEmpty() || price == null) continue
            items += ReceiptItem(
                receipt = receipt,
                position = items.size,
                name = name,
                quantity = item.quantity?.setScale(3, RoundingMode.HALF_UP)?.takeIf { it.abs() <= MAX_QUANTITY },
                priceMinor = price
            )
        }
        receipt.items.clear()
        receipt.items.addAll(items)

        val total = receipt.totalMinor
        receipt.totalMismatch = items.isNotEmpty() && total != null &&
            abs(items.fold(0L) { sum, it -> Math.addExact(sum, it.priceMinor) } - total) > items.size

        receipt.suggestedCategoryId = read.suggestedCategory?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            categories.findByHouseholdAndType(household, CategoryType.EXPENSE)
                .firstOrNull { !it.archived && it.name.trim().equals(name, ignoreCase = true) }?.id
        }
    }

    private fun toMinorOrNull(amount: BigDecimal, decimals: Int): Long? =
        try {
            MinorUnits.toMinor(amount, decimals)
        } catch (e: IllegalArgumentException) {
            null
        }
}
