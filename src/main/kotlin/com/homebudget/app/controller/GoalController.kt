package com.homebudget.app.controller

import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.domain.SavingsGoal
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.GoalRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.Period

data class GoalResponse(
    val id: Long,
    val uuid: String,
    val name: String,
    val targetAmount: BigDecimal,
    val currentAmount: BigDecimal,
    val currencyCode: String,
    val targetDate: LocalDate?,
    val linkedAccountId: Long?,
    val icon: String,
    val color: String,
    val archived: Boolean,
    val pacing: GoalPacingResponse
)

data class GoalPacingResponse(
    val progressPercent: Double,
    val remainingAmount: BigDecimal,
    val monthsRemaining: Int,
    val monthlyPacing: BigDecimal?
)

data class CreateGoalRequest(
    val name: String,
    val targetAmount: BigDecimal,
    val currencyCode: String,
    val currentAmount: BigDecimal = BigDecimal.ZERO,
    val targetDate: LocalDate? = null,
    val linkedAccountId: Long? = null,
    val icon: String = "flag",
    val color: String = "#10B981"
)

data class UpdateGoalRequest(
    val name: String? = null,
    val targetAmount: BigDecimal? = null,
    val currentAmount: BigDecimal? = null,
    val targetDate: LocalDate? = null,
    val linkedAccountId: Long? = null,
    val icon: String? = null,
    val color: String? = null,
    val archived: Boolean? = null
)

@RestController
@RequestMapping("/api/goals")
class GoalController(
    private val goalRepo: GoalRepository,
    private val accountRepo: AccountRepository,
    private val currencyRepo: CurrencyRepository,
    private val securityUtils: SecurityUtils
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(@RequestParam(defaultValue = "false") includeArchived: Boolean): List<GoalResponse> {
        val user = securityUtils.getCurrentUser()
        val all = goalRepo.findAllByUser(user)
        val filtered = if (includeArchived) all else all.filter { !it.archived }
        return filtered.map { toResponse(it) }
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    fun get(@PathVariable id: Long): GoalResponse {
        val user = securityUtils.getCurrentUser()
        val goal = goalRepo.findByIdAndUser(id, user)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found")
        return toResponse(goal)
    }

    @PostMapping
    @Transactional
    fun create(@RequestBody req: CreateGoalRequest): GoalResponse {
        val user = securityUtils.getCurrentUser()
        val decimals = currencyRepo.findByCode(req.currencyCode)?.decimals ?: 2
        val linkedAccount = req.linkedAccountId?.let { accId ->
            accountRepo.findByIdAndUser(accId, user)
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Linked account not found: $accId")
        }

        val goal = goalRepo.save(
            SavingsGoal(
                user = user,
                name = req.name,
                targetAmountMinor = MinorUnits.toMinor(req.targetAmount, decimals),
                currencyCode = req.currencyCode,
                currentAmountMinor = MinorUnits.toMinor(req.currentAmount, decimals),
                targetDate = req.targetDate,
                linkedAccount = linkedAccount,
                icon = req.icon,
                color = req.color
            )
        )
        return toResponse(goal)
    }

    @PutMapping("/{id}")
    @Transactional
    fun update(@PathVariable id: Long, @RequestBody req: UpdateGoalRequest): GoalResponse {
        val user = securityUtils.getCurrentUser()
        val goal = goalRepo.findByIdAndUser(id, user)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found")
        val decimals = currencyRepo.findByCode(goal.currencyCode)?.decimals ?: 2

        req.name?.let { goal.name = it }
        req.targetAmount?.let { goal.targetAmountMinor = MinorUnits.toMinor(it, decimals) }
        req.currentAmount?.let { goal.currentAmountMinor = MinorUnits.toMinor(it, decimals) }
        req.targetDate?.let { goal.targetDate = it }
        req.icon?.let { goal.icon = it }
        req.color?.let { goal.color = it }
        req.archived?.let { goal.archived = it }
        if (req.linkedAccountId != null) {
            val linked = accountRepo.findByIdAndUser(req.linkedAccountId, user)
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Account not found: ${req.linkedAccountId}")
            goal.linkedAccount = linked
        }
        goal.updatedAt = Instant.now()
        return toResponse(goalRepo.save(goal))
    }

    @DeleteMapping("/{id}")
    @Transactional
    fun delete(@PathVariable id: Long) {
        val user = securityUtils.getCurrentUser()
        val goal = goalRepo.findByIdAndUser(id, user)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found")
        goal.deleted = true
        goal.updatedAt = Instant.now()
        goalRepo.save(goal)
    }

    private fun toResponse(goal: SavingsGoal): GoalResponse {
        val decimals = currencyRepo.findByCode(goal.currencyCode)?.decimals ?: 2
        val target = MinorUnits.toDecimal(goal.targetAmountMinor, decimals)
        val current = MinorUnits.toDecimal(goal.currentAmountMinor, decimals)
        val remainingMinor = (goal.targetAmountMinor - goal.currentAmountMinor).coerceAtLeast(0)
        val remaining = MinorUnits.toDecimal(remainingMinor, decimals)

        val progressPercent = if (goal.targetAmountMinor > 0) {
            (goal.currentAmountMinor.toDouble() / goal.targetAmountMinor.toDouble() * 100.0).coerceIn(0.0, 100.0)
        } else 100.0

        val now = LocalDate.now()
        val monthsRemaining = if (goal.targetDate != null && goal.targetDate!!.isAfter(now)) {
            val period = Period.between(now, goal.targetDate)
            (period.years * 12 + period.months).coerceAtLeast(1)
        } else 0

        val monthlyPacing = if (monthsRemaining > 0 && remainingMinor > 0) {
            remaining.divide(BigDecimal(monthsRemaining), decimals, RoundingMode.HALF_UP)
        } else null

        return GoalResponse(
            id = goal.id,
            uuid = goal.uuid,
            name = goal.name,
            targetAmount = target,
            currentAmount = current,
            currencyCode = goal.currencyCode,
            targetDate = goal.targetDate,
            linkedAccountId = goal.linkedAccount?.id,
            icon = goal.icon,
            color = goal.color,
            archived = goal.archived,
            pacing = GoalPacingResponse(
                progressPercent = progressPercent,
                remainingAmount = remaining,
                monthsRemaining = monthsRemaining,
                monthlyPacing = monthlyPacing
            )
        )
    }
}
