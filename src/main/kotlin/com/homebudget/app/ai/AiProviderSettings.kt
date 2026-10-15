package com.homebudget.app.ai

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Duration
import java.time.Instant

/** The administrator tried to change a provider that the environment sets. */
class AiProviderLockedException(message: String = "The AI provider is set by the AI_PROVIDER environment variable.") :
    RuntimeException(message)

/** A provider ready to call. [baseUrl] is the full endpoint base; Anthropic's and Gemini's are fixed. */
data class ResolvedProvider(
    val type: AiProviderType,
    val baseUrl: String,
    val model: String,
    val apiKey: String?,
    val source: AiProviderSource
) {
    // The key must not reach a log line through a stray toString.
    override fun toString() = "ResolvedProvider(type=$type, baseUrl=$baseUrl, model=$model, source=$source)"
}

/**
 * Which model reads receipts: the household's own, else the one the environment names,
 * else the one the administrator chose in the app, else none.
 */
@Service
class AiProviderSettings(
    private val providers: AiProviderRepository,
    @Value("\${app.ai.provider:}") private val envProvider: String,
    @Value("\${app.ai.base-url:}") private val envBaseUrl: String,
    @Value("\${app.ai.model:}") private val envModel: String,
    @Value("\${app.ai.api-key:}") private val envApiKey: String,
    @Value("\${app.ai.timeout:5m}") val timeout: Duration,
    @Value("\${app.ai.concurrency:2}") val concurrency: Int
) {
    companion object {
        const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
        const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
        const val OPENAI_URL = "https://api.openai.com/v1"

        /** The OpenAI-compatible endpoints a household may choose; anything else is the administrator's call. */
        val HOUSEHOLD_PRESETS = setOf(OPENAI_URL, "https://openrouter.ai/api/v1")
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private val environment: ResolvedProvider? = buildEnvironmentProvider()

    /** True when the environment names the provider, so the admin page cannot. */
    val lockedByEnvironment: Boolean get() = environment != null

    private fun buildEnvironmentProvider(): ResolvedProvider? {
        if (envProvider.isBlank()) return null
        val type = AiProviderType.entries.firstOrNull { it.name.equals(envProvider.trim().replace('-', '_'), ignoreCase = true) }
        if (type == null) {
            log.warn("AI_PROVIDER '{}' is not one of {}; ignoring it", envProvider, AiProviderType.entries.joinToString())
            return null
        }
        if (envModel.isBlank()) {
            log.warn("AI_PROVIDER is set but AI_MODEL is empty; ignoring it")
            return null
        }
        val baseUrl = when (type) {
            AiProviderType.OPENAI_COMPATIBLE -> envBaseUrl.trim().ifBlank { OPENAI_URL }
            AiProviderType.ANTHROPIC -> ANTHROPIC_URL
            AiProviderType.GEMINI -> GEMINI_URL
        }
        return ResolvedProvider(type, baseUrl, envModel.trim(), envApiKey.ifBlank { null }, AiProviderSource.ENVIRONMENT)
    }

    /** The provider used for [householdId]'s receipts, or null when none is configured. */
    @Transactional(readOnly = true)
    fun resolveFor(householdId: Long): ResolvedProvider? =
        providers.findByHouseholdId(householdId)?.let { resolved(it, AiProviderSource.HOUSEHOLD) }
            ?: instanceProvider()

    /** The provider a household without its own would use: the environment's, else the admin page's. */
    @Transactional(readOnly = true)
    fun instanceProvider(): ResolvedProvider? =
        environment ?: providers.findFirstByScope(AiProviderScope.INSTANCE)?.let { resolved(it, AiProviderSource.INSTANCE) }

    /** What the household's owner and members see. */
    @Transactional(readOnly = true)
    fun viewFor(householdId: Long): ProviderView = view(resolveFor(householdId))

    /** What the administrator sees: the environment's provider if set, else the stored one. */
    @Transactional(readOnly = true)
    fun instanceView(): ProviderView = view(instanceProvider())

    @Transactional
    fun saveHousehold(householdId: Long, req: ProviderRequest): ProviderView {
        validate(req, administrator = false)
        val row = providers.findByHouseholdId(householdId)
            ?: AiProviderEntity(scope = AiProviderScope.HOUSEHOLD, householdId = householdId, type = req.type, model = req.model)
        apply(row, req)
        providers.save(row)
        return viewFor(householdId)
    }

    @Transactional
    fun deleteHousehold(householdId: Long) {
        providers.findByHouseholdId(householdId)?.let(providers::delete)
    }

    @Transactional
    fun saveInstance(req: ProviderRequest): ProviderView {
        if (lockedByEnvironment) throw AiProviderLockedException()
        validate(req, administrator = true)
        // One row, kept that way here: H2 (the test database) has no partial unique index.
        val row = providers.findFirstByScope(AiProviderScope.INSTANCE)
            ?: AiProviderEntity(scope = AiProviderScope.INSTANCE, type = req.type, model = req.model)
        apply(row, req)
        providers.save(row)
        return instanceView()
    }

    @Transactional
    fun deleteInstance() {
        if (lockedByEnvironment) throw AiProviderLockedException()
        providers.findFirstByScope(AiProviderScope.INSTANCE)?.let(providers::delete)
    }

    private fun apply(row: AiProviderEntity, req: ProviderRequest) {
        val typeChanged = row.type != req.type
        row.type = req.type
        row.model = req.model.trim()
        row.baseUrl = if (req.type == AiProviderType.OPENAI_COMPATIBLE) req.baseUrl!!.trim().trimEnd('/') else null
        // A key belongs to the provider it was issued for; switching type without a new one must not carry it over.
        row.apiKey = when {
            req.apiKey == null -> if (typeChanged) null else row.apiKey
            req.apiKey.isEmpty() -> null
            else -> req.apiKey
        }
        row.updatedAt = Instant.now()
    }

    private fun validate(req: ProviderRequest, administrator: Boolean) {
        require(req.model.isNotBlank()) { "Choose a model" }
        if (req.type != AiProviderType.OPENAI_COMPATIBLE) return
        val url = req.baseUrl?.trim()?.trimEnd('/')
        require(!url.isNullOrBlank()) { "Enter the address of the model's API" }
        if (administrator) {
            val uri = runCatching { URI(url) }.getOrNull()
            require(uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
                "Enter a full http:// or https:// address"
            }
        } else {
            require(url in HOUSEHOLD_PRESETS) {
                "Choose one of the listed providers; the administrator can set up another address"
            }
        }
    }

    private fun resolved(row: AiProviderEntity, source: AiProviderSource) = ResolvedProvider(
        type = row.type,
        baseUrl = when (row.type) {
            AiProviderType.OPENAI_COMPATIBLE -> row.baseUrl ?: OPENAI_URL
            AiProviderType.ANTHROPIC -> ANTHROPIC_URL
            AiProviderType.GEMINI -> GEMINI_URL
        },
        model = row.model,
        apiKey = row.apiKey,
        source = source
    )

    private fun view(p: ResolvedProvider?) =
        if (p == null) ProviderView(AiProviderSource.NONE)
        else ProviderView(
            source = p.source,
            type = p.type,
            baseUrl = p.baseUrl,
            model = p.model,
            hasKey = !p.apiKey.isNullOrEmpty(),
            host = runCatching { URI(p.baseUrl.replace("{model}", "m")).host }.getOrNull()
        )
}
