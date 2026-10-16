package com.personalmoney.core.receipt

import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.bodyMessage
import com.personalmoney.core.network.ReceiptDto
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What a read receipt puts into the entry form. Every field but the uuid is optional: the
 * model can miss any one of them, and the form keeps what it could not fill editable.
 *
 * [total] is a plain decimal string, `"249.90"`, because that is what
 * [com.personalmoney.feature.quickadd.QuickAddViewModel.setAmountText] takes. A scan fills
 * the form and stops - it never saves on the user's behalf.
 */
data class ReceiptDraft(
    /** Saved with the transaction, so the photo and the items stay with the entry. */
    val receiptUuid: String,
    val total: String? = null,
    val date: LocalDate? = null,
    val merchant: String? = null,
    val suggestedCategoryUuid: String? = null,
    /** The receipt's currency; the amount is only filled when it is the account's. */
    val currency: String? = null,
    /** The items do not add up to [total]; the person should check the amount. */
    val totalMismatch: Boolean = false,
)

/** Whether the household's receipts can be read, as far as the server says. */
enum class ScanAvailability {
    AVAILABLE,

    /** Nobody has set a provider up - which only the web app can do. */
    NOT_SET_UP,

    /** The server could not be asked. */
    UNKNOWN,
}

sealed interface ScanOutcome {
    data class Ready(val draft: ReceiptDraft) : ScanOutcome

    /**
     * The server could not read it, or refused the photo. [reason] is the server's own
     * sentence; null when there is none to show (the read timed out, or the server failed
     * in a way it does not explain), and the screen says something generic instead.
     */
    data class Failed(val reason: String?) : ScanOutcome

    /** The server could not be reached. Reading needs it - nothing happens on the phone. */
    data object Offline : ScanOutcome
}

/**
 * Receipts are read by the server, through whichever model the household or the instance
 * has configured: upload the photo, then poll until it is read.
 *
 * Polling rather than waiting on one long request, because a model can take minutes on a
 * long receipt and no mobile connection holds a request open that long.
 */
class ReceiptScanner(
    private val api: HomeBudgetApi,
    private val pollInterval: Duration = 2.seconds,
    giveUpAfter: Duration = 6.minutes,
) {
    // Counted in polls rather than measured against a clock, so the limit is the same
    // under a test's virtual time as on a phone.
    private val maxPolls = (giveUpAfter / pollInterval).toInt()

    suspend fun availability(): ScanAvailability =
        try {
            if (api.householdAiProvider().source == "NONE") ScanAvailability.NOT_SET_UP
            else ScanAvailability.AVAILABLE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ScanAvailability.UNKNOWN
        }

    /** Whether the household has a receipt reader. False when that cannot be found out. */
    suspend fun canScan(): Boolean = availability() == ScanAvailability.AVAILABLE

    suspend fun scan(jpeg: ByteArray): ScanOutcome =
        try {
            val accepted = api.uploadReceipt(jpeg)
            poll(accepted.uuid)
        } catch (e: IOException) {
            ScanOutcome.Offline
        } catch (e: ResponseException) {
            ScanOutcome.Failed(e.userMessage())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A body that is not the shape it should be: nothing the user can act on.
            ScanOutcome.Failed(null)
        }

    private suspend fun poll(uuid: String): ScanOutcome {
        // The photo is already on the server and being read, so one dropped poll on a
        // mobile connection is no reason to give up on it. Only a run of them is.
        var failedPolls = 0
        repeat(maxPolls) {
            delay(pollInterval)
            val receipt = try {
                api.receipt(uuid).also { failedPolls = 0 }
            } catch (e: IOException) {
                if (++failedPolls >= MAX_FAILED_POLLS) throw e
                return@repeat
            }
            when (receipt.status) {
                "READY" -> return ScanOutcome.Ready(receipt.toDraft())
                "FAILED" -> return ScanOutcome.Failed(receipt.failureReason)
            }
        }
        return ScanOutcome.Failed(null)
    }
}

private fun ReceiptDto.toDraft() = ReceiptDraft(
    receiptUuid = uuid,
    total = total,
    date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    merchant = merchant?.takeIf { it.isNotBlank() },
    suggestedCategoryUuid = suggestedCategoryUuid,
    currency = currency?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
    totalMismatch = totalMismatch,
)

/** Consecutive polls that may fail to reach the server before the scan counts as offline. */
private const val MAX_FAILED_POLLS = 3

/**
 * The server's `message` for a refused upload - 409 no provider, 413 too large, 415 not an
 * image it can read - all written for the user. 5xx carries an exception message meant for
 * a log, so it is not shown.
 */
private suspend fun ResponseException.userMessage(): String? =
    if (response.status.value >= 500) null else bodyMessage()
