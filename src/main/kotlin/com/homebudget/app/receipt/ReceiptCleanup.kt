package com.homebudget.app.receipt

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Deletes receipts nobody saved a transaction with, a day after they were taken: a photo
 * of a receipt is personal data, and one that was only glanced at should not stay.
 */
@Component
class ReceiptCleanup(
    private val receipts: ReceiptRepository,
    private val service: ReceiptService,
    private val clock: Clock
) {
    companion object {
        val KEEP_UNLINKED: Duration = Duration.ofHours(24)
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    fun deleteUnlinked(): Int {
        val stale = receipts.findUnlinkedBefore(Instant.now(clock).minus(KEEP_UNLINKED))
        stale.forEach(service::deleteWithPhoto)
        if (stale.isNotEmpty()) log.info("Deleted {} unsaved receipts older than a day", stale.size)
        return stale.size
    }
}
