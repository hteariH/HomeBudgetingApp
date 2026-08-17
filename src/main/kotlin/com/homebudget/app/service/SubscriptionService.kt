package com.homebudget.app.service

import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.domain.Subscription
import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.CreateSubscriptionRequest
import com.homebudget.app.dto.UpdateSubscriptionRequest
import com.homebudget.app.repository.SubscriptionRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.security.SecurityUtils
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Service
@Transactional
class SubscriptionService(
    private val repo: SubscriptionRepository,
    private val transactionRepo: TransactionRepository,
    private val accountService: AccountService,
    private val categoryService: CategoryService,
    private val securityUtils: SecurityUtils,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findAll(): List<Subscription> = repo.findAllByUser(securityUtils.getCurrentUser())

    fun findById(id: Long): Subscription = repo.findByIdAndUser(id, securityUtils.getCurrentUser())
        ?: throw NoSuchElementException("Subscription not found: $id")

    fun create(req: CreateSubscriptionRequest): Subscription {
        val user = securityUtils.getCurrentUser()
        val account = accountService.findById(req.accountId)
        val category = req.categoryId?.let { categoryService.findById(it) }
        require(req.endDate == null || !req.endDate.isBefore(req.startDate)) {
            "End date cannot precede the start date"
        }
        return repo.save(
            Subscription(
                user = user,
                name = req.name,
                account = account,
                category = category,
                amountMinor = MinorUnits.toMinor(req.amount, account.currency.decimals),
                currencyCode = account.currency.code,
                frequency = req.frequency,
                intervalCount = req.intervalCount,
                nextChargeDate = req.nextChargeDate ?: req.startDate,
                startDate = req.startDate,
                endDate = req.endDate,
                reminderMinutesBefore = req.reminderMinutesBefore,
                icon = req.icon,
                color = req.color,
                note = req.note
            )
        )
    }

    fun update(id: Long, req: UpdateSubscriptionRequest): Subscription {
        val sub = findById(id)
        req.name?.let { sub.name = it }
        req.categoryId?.let { sub.category = categoryService.findById(it) }
        req.amount?.let { sub.amountMinor = MinorUnits.toMinor(it, sub.account.currency.decimals) }
        req.frequency?.let { sub.frequency = it }
        req.intervalCount?.let { sub.intervalCount = it }
        req.nextChargeDate?.let { sub.nextChargeDate = it }
        req.endDate?.let { sub.endDate = it }
        req.active?.let { sub.active = it }
        req.reminderMinutesBefore?.let { sub.reminderMinutesBefore = it }
        req.icon?.let { sub.icon = it }
        req.color?.let { sub.color = it }
        req.note?.let { sub.note = it }
        sub.updatedAt = Instant.now(clock)
        return repo.save(sub)
    }

    fun delete(id: Long) {
        val sub = findById(id)
        sub.deleted = true
        sub.updatedAt = Instant.now(clock)
        repo.save(sub)
    }

    /**
     * Writes a transaction for every charge that has come due, across all users.
     *
     * This runs on the server rather than in each client so that a phone left closed
     * for a month does not hold its charges back, and so both clients see the same
     * rows. Charges are generated for every missed period, not just the latest.
     */
    @Scheduled(cron = "\${app.subscriptions.cron:0 10 3 * * *}")
    @Transactional
    fun generateDueCharges(): Int {
        val today = LocalDate.now(clock)
        val due = repo.findDue(today)
        var written = 0

        for (sub in due) {
            val catchUp = SubscriptionScheduler.catchUp(
                nextChargeDate = sub.nextChargeDate,
                today = today,
                frequency = sub.frequency,
                intervalCount = sub.intervalCount,
                endDate = sub.endDate
            )
            for (date in catchUp.dueDates) {
                transactionRepo.save(
                    Transaction(
                        user = sub.user,
                        account = sub.account,
                        category = sub.category,
                        amountMinor = sub.amountMinor,
                        currencyCode = sub.currencyCode,
                        accountAmountMinor = sub.amountMinor,
                        type = TransactionType.EXPENSE,
                        occurredAt = date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                        description = sub.name,
                        subscription = sub
                    )
                )
                written++
            }
            sub.nextChargeDate = catchUp.nextChargeDate
            if (catchUp.finished) sub.active = false
            sub.updatedAt = Instant.now(clock)
            repo.save(sub)
        }

        if (written > 0) log.info("Generated {} subscription charges across {} subscriptions", written, due.size)
        return written
    }
}
