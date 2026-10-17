package com.homebudget.app.admin

import com.homebudget.app.mcp.McpSettings
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.SecurityUtils
import com.homebudget.app.service.AuthFlowService
import com.homebudget.app.service.RegistrationService
import com.homebudget.app.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

/** What the person who runs the instance can do that its other users cannot. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val users: UserRepository,
    private val members: HouseholdMemberRepository,
    private val registration: RegistrationService,
    private val authFlow: AuthFlowService,
    private val userService: UserService,
    private val securityUtils: SecurityUtils,
    private val mcp: McpSettings
) {
    @GetMapping("/users")
    @Transactional(readOnly = true)
    fun users(): List<AdminUserResponse> = users.findAllByOrderByCreatedAtAsc().map {
        AdminUserResponse(
            id = it.id,
            email = it.email,
            firstName = it.firstName,
            lastName = it.lastName,
            role = it.role,
            createdAt = it.createdAt,
            lastSignInAt = it.lastSignInAt,
            householdId = members.findFirstByUserId(it.id)?.household?.id
        )
    }

    @GetMapping("/registration")
    fun registration() = RegistrationSetting(
        open = registration.registrationOpen(),
        lockedByEnvironment = registration.registrationLockedByEnvironment()
    )

    @PutMapping("/registration")
    fun setRegistration(@RequestBody req: SetRegistrationRequest): ResponseEntity<Any> {
        if (registration.registrationLockedByEnvironment()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Registration is set by the REGISTRATION environment variable."))
        }
        registration.setRegistrationOpen(req.open)
        return ResponseEntity.ok(registration())
    }

    /** Whether assistants may connect over MCP. */
    @GetMapping("/mcp")
    fun mcp() = McpSetting(enabled = mcp.enabled(), lockedByEnvironment = mcp.lockedByEnvironment())

    @PutMapping("/mcp")
    fun setMcp(@RequestBody req: SetMcpRequest): ResponseEntity<Any> {
        if (mcp.lockedByEnvironment()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Assistants (MCP) are set by the MCP_ENABLED environment variable."))
        }
        mcp.setEnabled(req.enabled)
        return ResponseEntity.ok(mcp())
    }

    @PostMapping("/users/{id}/reset-link")
    fun resetLink(@PathVariable id: Long): ResetLinkResponse =
        authFlow.issueAdminResetLink(users.findById(id).orElseThrow { NoSuchElementException("User not found: $id") })

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun delete(@PathVariable id: Long) {
        require(id != securityUtils.getCurrentUser().id) {
            "Delete your own account from Settings, not from the user list"
        }
        userService.deleteUser(users.findById(id).orElseThrow { NoSuchElementException("User not found: $id") })
    }
}
