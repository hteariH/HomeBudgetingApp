package com.homebudget.app.household

import com.homebudget.app.domain.HouseholdMember
import com.homebudget.app.domain.HouseholdRole
import org.springframework.security.access.AccessDeniedException

/** The link existed and no longer works: expired, already used, or revoked. */
class InviteGoneException(message: String) : RuntimeException(message)

/** A household may hold only so many open invitations at once. */
class InviteLimitException(message: String) : RuntimeException(message)

/** The caller's membership moved after their request was authenticated; they sync again. */
class HouseholdChangedException(message: String = "Your household changed; sync again") : RuntimeException(message)

fun requireOwner(membership: HouseholdMember) {
    if (membership.role != HouseholdRole.OWNER) {
        throw AccessDeniedException("Only the household's owner can do that")
    }
}
