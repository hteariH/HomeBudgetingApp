package com.homebudget.app.billing

import com.homebudget.app.domain.EntitlementSource
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.temporal.ChronoUnit

/**
 * Handing premium to somebody without a purchase.
 *
 * There is no payment provider on the web and there may never be one in Ukraine, so this
 * is not a stopgap: friends, testers, translators, anyone owed a favour, and anyone whose
 * payment went wrong all become subscribers through here. It is the same
 * [EntitlementService] a Play purchase goes through, so a manual grant is premium in
 * exactly the same sense - not a second kind of subscriber for every limit to remember.
 *
 * ADMIN only, which in this app means a row with `role = 'ADMIN'`; there is no UI, and
 * `docs/PREMIUM.md` has the two curl commands.
 */
@RestController
@RequestMapping("/api/admin/entitlements")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
class AdminEntitlementController(
    private val users: UserRepository,
    private val entitlements: EntitlementService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Finds the account to act on.
     *
     * By email fragment, because that is what an administrator has: somebody writes in
     * from the address they registered with. An empty query lists the accounts that
     * already have an entitlement, which is the other question worth asking here - who
     * has premium, and until when.
     */
    @GetMapping("/users")
    fun find(@RequestParam(required = false) query: String?): List<AdminUserResponse> {
        val found = if (query.isNullOrBlank()) {
            users.findAll().filter { it.entitlementSource != EntitlementSource.NONE }
        } else {
            users.findTop50ByEmailContainingIgnoreCaseOrderByIdAsc(query.trim())
        }
        return found.map { it.toAdminResponse() }
    }

    /**
     * Grants premium by hand.
     *
     * `{}` grants it indefinitely; `{"days": 90}` for a while; `{"until": "..."}` to an
     * exact instant. A grant never shortens an entitlement the account already has - see
     * [EntitlementService.grant] - so re-running this is safe.
     */
    @PostMapping("/users/{id}")
    fun grant(@PathVariable id: Long, @RequestBody req: GrantPremiumRequest): AdminUserResponse {
        require(req.days == null || req.until == null) { "Give either days or until, not both" }
        require(req.days == null || req.days > 0) { "days must be positive" }

        val user = find(id)
        val until = req.until ?: req.days?.let { entitlements.now().plus(it, ChronoUnit.DAYS) }
        log.info("Manual premium for {} until {}: {}", user.email, until ?: "forever", req.reason ?: "no reason given")
        return entitlements.grant(user, until, EntitlementSource.MANUAL, req.reason.orEmpty()).toAdminResponse()
    }

    /**
     * Takes it away, whatever granted it.
     *
     * Unlike the Play re-check this passes no `only`, so it can also end an entitlement
     * that came from a subscription - for a refund, say. It does not cancel the Play
     * subscription itself, and if one is still live the next hourly re-check will grant
     * premium straight back. Cancel it in the Play Console first.
     */
    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun revoke(@PathVariable id: Long): AdminUserResponse =
        entitlements.revoke(find(id), EntitlementSource.MANUAL).toAdminResponse()

    private fun find(id: Long): User = users.findById(id)
        .orElseThrow { NoSuchElementException("No user with id $id") }
}

private fun User.toAdminResponse() = AdminUserResponse(
    id = id,
    uuid = uuid,
    email = email,
    firstName = firstName,
    lastName = lastName,
    emailVerified = emailVerified,
    entitlement = entitlement,
    entitlementExpiresAt = entitlementExpiresAt,
    entitlementSource = entitlementSource,
    admin = role == Role.ADMIN
)
