package com.homebudget.app.household

import com.homebudget.app.security.SecurityUtils
import org.springframework.web.bind.annotation.*

/** What someone holding a link may do with it. Preview is public; accepting needs a session. */
@RestController
@RequestMapping("/api/invites")
class InviteController(
    private val invites: InviteService,
    private val merge: HouseholdMergeService,
    private val securityUtils: SecurityUtils
) {
    @GetMapping("/{token}")
    fun preview(@PathVariable token: String) = invites.preview(token)

    /** Moves the caller, with their books, into the invitation's household. */
    @PostMapping("/{token}/accept")
    fun accept(@PathVariable token: String): JoinedResponse {
        val household = merge.join(securityUtils.getCurrentUser(), invites.requireLive(token))
        return JoinedResponse(household.id, household.name)
    }
}
