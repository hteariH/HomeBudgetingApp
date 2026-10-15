package com.homebudget.app.household

import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.dto.CurrencyResponse
import java.time.Instant

data class CreateInviteRequest(val origin: String? = null)

data class CreatedInvite(val id: Long, val path: String, val expiresAt: Instant, val qrRows: List<String>?)

data class InviteSummary(val id: Long, val createdAt: Instant, val expiresAt: Instant, val createdBy: String?)

data class InvitePreview(val householdName: String, val invitedBy: String?, val expiresAt: Instant)

data class MemberResponse(
    val userId: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val role: HouseholdRole,
    val joinedAt: Instant
)

data class HouseholdResponse(
    val id: Long,
    val name: String,
    val baseCurrency: CurrencyResponse?,
    val members: List<MemberResponse>
)
