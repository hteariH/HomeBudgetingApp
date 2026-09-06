package com.homebudget.app.importer.homemoney

import com.homebudget.app.service.FxService
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Where an import has got to.
 *
 * In memory, and therefore lost on restart. That is the right trade for what it is: a
 * progress bar for an operation the user is watching. Nothing is inferred from its
 * absence - the ledger itself is the record of what was imported, and re-running is
 * always safe because every row is deduped by external id.
 *
 * One import per user at a time, which is what [claim] enforces.
 */
@Component
class HomeMoneyImportStatuses {

    private val byUser = ConcurrentHashMap<Long, HomeMoneyImportStatus>()

    fun of(userId: Long): HomeMoneyImportStatus = byUser[userId] ?: HomeMoneyImportStatus()

    fun isRunning(userId: Long): Boolean = of(userId).state == HomeMoneyImportStatus.State.RUNNING

    /**
     * Marks an import as started, or reports that one already is.
     *
     * Atomic, and done before the work is handed to the pool: two clicks in a row must
     * not put two imports through the same rows. They would not corrupt anything - dedup
     * sees to that - but they would race each other for a quarter of an hour.
     */
    fun claim(userId: Long): Boolean {
        val fresh = HomeMoneyImportStatus(state = HomeMoneyImportStatus.State.RUNNING)
        val result = byUser.compute(userId) { _, current ->
            if (current?.state == HomeMoneyImportStatus.State.RUNNING) current else fresh
        }
        // Identity, not equality: a previous run left to its own devices could hold a
        // value equal to a fresh one, and "did I win the race" is a question about which
        // object went in, not about what it contains.
        return result === fresh
    }

    fun put(userId: Long, status: HomeMoneyImportStatus) {
        byUser[userId] = status
    }

    fun update(userId: Long, block: (HomeMoneyImportStatus) -> HomeMoneyImportStatus) {
        byUser.compute(userId) { _, current -> block(current ?: HomeMoneyImportStatus()) }
    }
}

/**
 * Runs an import in the background.
 *
 * Background because it is not a request-shaped piece of work: a decade of history is
 * dozens of calls to somebody else's API and thousands of rows, and holding an HTTP
 * connection open for that tells the user nothing while it happens and everything at once
 * when it finishes.
 *
 * A separate bean from [HomeMoneyImportService] on purpose. `@Transactional` is
 * proxy-based, so the per-year transactions only exist because the calls cross a bean
 * boundary - and those transactions are what make a half-finished import resumable.
 */
@Service
class HomeMoneyImportRunner(
    private val service: HomeMoneyImportService,
    private val connections: HomeMoneyConnectionService,
    private val statuses: HomeMoneyImportStatuses,
    private val fxService: FxService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * A thread of its own, rather than `@Async`.
     *
     * Declaring an executor bean for `@Async` to name would have been the idiomatic route,
     * and it has a sting: Spring Boot's own task executor backs off the moment any
     * `Executor` bean exists, so adding one silently demotes every *other* `@Async` in the
     * application - outgoing mail, here - to a thread per call. Owning the pool privately
     * leaves the rest of the application exactly as it was.
     *
     * One thread: an import is minutes of somebody else's API and thousands of rows, and
     * running two at once would only make both slower.
     */
    private val pool = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hm-import").apply { isDaemon = true }
    }

    @PreDestroy
    fun shutdown() {
        pool.shutdown()
        // An import that is mid-year keeps its finished years: every row is deduped by
        // external id, so resuming after a restart re-imports nothing.
        if (!pool.awaitTermination(20, TimeUnit.SECONDS)) pool.shutdownNow()
    }

    /**
     * Hands the import to the pool and returns at once.
     *
     * The slot is claimed by [HomeMoneyImportStatuses.claim] before this is called, on the
     * request thread, so two clicks cannot start two imports.
     */
    fun run(userId: Long, plan: HomeMoneyPlan) {
        pool.execute { import(userId, plan) }
    }

    private fun import(userId: Long, plan: HomeMoneyPlan) {
        try {
            val user = service.requireUser(userId)
            val connection = connections.requireFor(user)

            val targets = service.prepare(userId, plan)
            statuses.update(userId) {
                it.copy(
                    accountsCreated = targets.accountsCreated,
                    categoriesCreated = targets.categoriesCreated
                )
            }

            // The range is discovered rather than asked for: HomeMoney has no "how far
            // back does this go" call, so walk back from this year and stop once several
            // in a row come back empty.
            val years = service.yearsToScan()
            var emptyRun = 0
            var processed = 0
            for (year in years) {
                if (emptyRun >= service.emptyYearsBeforeStop()) break
                val operations = service.fetchYear(connection, year)
                processed++
                if (operations.isEmpty()) {
                    emptyRun++
                    statuses.update(userId) { it.copy(processedChunks = processed) }
                    continue
                }
                emptyRun = 0
                val result = service.importChunk(userId, targets, operations)
                statuses.update(userId) {
                    it.copy(
                        imported = it.imported + result.imported,
                        skipped = it.skipped + result.skipped,
                        processedChunks = processed
                    )
                }
            }

            service.reconcileOpeningBalances(userId, targets)
            try {
                fxService.refreshRates()
            } catch (e: Exception) {
                log.warn("FX rate refresh after import failed: {}", e.message)
            }
            statuses.update(userId) {
                it.copy(state = HomeMoneyImportStatus.State.DONE, totalChunks = processed)
            }
            log.info("HomeMoney import finished for user {}: {}", userId, statuses.of(userId))
        } catch (e: Exception) {
            // Whatever landed before the failure stays landed and is not imported twice
            // on the next attempt, so the honest thing is to report the error and leave
            // the ledger alone.
            log.warn("HomeMoney import failed for user {}", userId, e)
            statuses.update(userId) {
                it.copy(
                    state = HomeMoneyImportStatus.State.FAILED,
                    error = e.message ?: e::class.simpleName
                )
            }
        }
    }
}
