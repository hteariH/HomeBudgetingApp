package com.homebudget.app.ai

import com.homebudget.app.household.requireOwner
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.security.SecurityUtils
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*

/** The instance-wide model, for the administrator. */
@RestController
@RequestMapping("/api/admin/ai-provider")
@PreAuthorize("hasRole('ADMIN')")
class AdminAiProviderController(private val settings: AiProviderSettings, private val sample: SampleReceiptTest) {
    @GetMapping
    fun get() = settings.instanceView()

    @PutMapping
    fun put(@RequestBody req: ProviderRequest) = settings.saveInstance(req)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete() = settings.deleteInstance()

    /** Reads the sample receipt with the given unsaved settings, or with the saved ones when there is no body. */
    @PostMapping("/test")
    fun test(@RequestBody(required = false) req: ProviderRequest?): ProviderTestResult =
        sample.run(if (req == null) settings.instanceProvider() else settings.unsavedInstance(req))
}

/** A household's own model: any member sees which one is in use, the owner chooses it. */
@RestController
@RequestMapping("/api/household/ai-provider")
class HouseholdAiProviderController(
    private val settings: AiProviderSettings,
    private val members: HouseholdMemberRepository,
    private val securityUtils: SecurityUtils,
    private val sample: SampleReceiptTest
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

    /** As for the administrator; owner only, and only settings the household could save. */
    @PostMapping("/test")
    fun test(@RequestBody(required = false) req: ProviderRequest?): ProviderTestResult {
        val m = membership()
        requireOwner(m)
        return sample.run(if (req == null) settings.resolveFor(m.household.id) else settings.unsavedHousehold(m.household.id, req))
    }

    private fun membership() = members.findFirstByUserId(securityUtils.getCurrentUser().id)
        ?: throw IllegalStateException("No household")
}

/** Runs the bundled sample receipt through a provider, for the Test button. */
@Component
class SampleReceiptTest(private val factory: ExtractorFactory, private val settings: AiProviderSettings) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val image: ByteArray by lazy {
        ClassPathResource("ai/sample-receipt.jpg").inputStream.use { it.readBytes() }
    }

    fun run(provider: ResolvedProvider?): ProviderTestResult {
        if (provider == null) return ProviderTestResult(ok = false, reason = "No model is set up to read receipts")
        return try {
            val receipt = factory.create(provider, settings.timeout)
                .extract(image, ExtractionContext(expenseCategories = emptyList(), currencies = emptyList()))
            ProviderTestResult(ok = true, receipt = receipt)
        } catch (e: ExtractionException) {
            log.info("Testing the {} model {} failed: {}", provider.type, provider.model, e.reason)
            ProviderTestResult(ok = false, reason = e.reason)
        }
    }
}
