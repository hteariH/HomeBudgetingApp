package com.homebudget.app.ai

import com.homebudget.app.household.requireOwner
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/** The instance-wide model, for the administrator. */
@RestController
@RequestMapping("/api/admin/ai-provider")
@PreAuthorize("hasRole('ADMIN')")
class AdminAiProviderController(private val settings: AiProviderSettings) {
    @GetMapping
    fun get() = settings.instanceView()

    @PutMapping
    fun put(@RequestBody req: ProviderRequest) = settings.saveInstance(req)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete() = settings.deleteInstance()
}

/** A household's own model: any member sees which one is in use, the owner chooses it. */
@RestController
@RequestMapping("/api/household/ai-provider")
class HouseholdAiProviderController(
    private val settings: AiProviderSettings,
    private val members: HouseholdMemberRepository,
    private val securityUtils: SecurityUtils
) {
    @GetMapping
    fun get(): ProviderView = settings.viewFor(membership().household.id)

    @PutMapping
    fun put(@RequestBody req: ProviderRequest): ProviderView {
        val m = membership()
        requireOwner(m)
        return settings.saveHousehold(m.household.id, req)
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete() {
        val m = membership()
        requireOwner(m)
        settings.deleteHousehold(m.household.id)
    }

    private fun membership() = members.findFirstByUserId(securityUtils.getCurrentUser().id)
        ?: throw IllegalStateException("No household")
}
