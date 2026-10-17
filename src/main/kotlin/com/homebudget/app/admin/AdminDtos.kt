package com.homebudget.app.admin

import com.homebudget.app.domain.Role
import java.time.Instant
import java.time.LocalDateTime

data class AdminUserResponse(
    val id: Long,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: Role,
    val createdAt: LocalDateTime,
    val lastSignInAt: Instant?,
    val householdId: Long?
)

data class RegistrationSetting(val open: Boolean, val lockedByEnvironment: Boolean)

data class SetRegistrationRequest(val open: Boolean)

data class McpSetting(val enabled: Boolean, val lockedByEnvironment: Boolean)

data class SetMcpRequest(val enabled: Boolean)

data class ResetLinkResponse(val path: String, val expiresAt: Instant)
