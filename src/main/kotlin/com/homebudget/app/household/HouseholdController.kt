package com.homebudget.app.household

import com.homebudget.app.domain.Household
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.security.SecurityUtils
import com.homebudget.app.service.toResponse
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/household")
class HouseholdController(
    private val invites: InviteService,
    private val membership: MembershipService,
    private val members: HouseholdMemberRepository,
    private val securityUtils: SecurityUtils
) {
    @GetMapping
    @Transactional(readOnly = true)
    fun household(): HouseholdResponse = respond(securityUtils.getCurrentHousehold())

    private fun respond(household: Household): HouseholdResponse =
        HouseholdResponse(
            id = household.id,
            name = household.name,
            baseCurrency = household.baseCurrency?.toResponse(),
            members = members.findAllByHouseholdIdOrderByJoinedAtAscIdAsc(household.id).map {
                MemberResponse(it.user.id, it.user.firstName, it.user.lastName, it.user.email, it.role, it.joinedAt)
            }
        )

    @PutMapping
    @Transactional
    fun rename(@RequestBody req: RenameHouseholdRequest): HouseholdResponse = respond(membership.rename(req.name))

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave() = membership.leave()

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(@PathVariable userId: Long) = membership.remove(userId)

    @PostMapping("/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun transferOwnership(@RequestBody req: TransferOwnershipRequest) = membership.transferOwnership(req.userId)

    @PostMapping("/invites")
    fun createInvite(@RequestBody(required = false) req: CreateInviteRequest?) = invites.create(req?.origin)

    @GetMapping("/invites")
    fun liveInvites() = invites.live()

    @DeleteMapping("/invites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeInvite(@PathVariable id: Long) = invites.revoke(id)
}
