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

        /** Model names as providers write them (`llava:13b`, `openai/gpt-4o`, `claude-x@2025`); the column holds 200. */
        private val MODEL_NAME = Regex("[A-Za-z0-9._:/@-]{1,200}")

        /**
         * The longest key accepted. The column holds 2048 characters of *ciphertext*
         * (`base64(iv):base64(sealed key)`), so a 2048-character key would not fit:
         * 1500 encrypts to 2024. Real keys are a few hundred characters at most.
         */
        const val MAX_KEY_LENGTH = 1500

        const val MAX_BASE_URL_LENGTH = 500
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

    /**
     * The settings in [req] as they would be used, without saving them - for the Test button.
     * A household may only test what it may save.
     */
    @Transactional(readOnly = true)
    fun unsavedHousehold(householdId: Long, req: ProviderRequest): ResolvedProvider {
        validate(req, administrator = false)
        return unsaved(req, providers.findByHouseholdId(householdId), AiProviderSource.HOUSEHOLD)
    }

    /** The administrator's unsaved settings; refused like a save when the environment sets the provider. */
    @Transactional(readOnly = true)
    fun unsavedInstance(req: ProviderRequest): ResolvedProvider {
        if (lockedByEnvironment) throw AiProviderLockedException()
        validate(req, administrator = true)
        return unsaved(req, providers.findFirstByScope(AiProviderScope.INSTANCE), AiProviderSource.INSTANCE)
    }

    private fun unsaved(req: ProviderRequest, stored: AiProviderEntity?, source: AiProviderSource) = ResolvedProvider(
        type = req.type,
        baseUrl = endpointFor(req.type, normalisedBaseUrl(req)),
        model = req.model.trim(),
        apiKey = keyFor(stored, req),
        source = source
    )

    private fun apply(row: AiProviderEntity, req: ProviderRequest) {
        row.apiKey = keyFor(row, req)
        row.type = req.type
        row.model = req.model.trim()
        row.baseUrl = normalisedBaseUrl(req)
        row.updatedAt = Instant.now()
    }

    private fun normalisedBaseUrl(req: ProviderRequest): String? =
        if (req.type == AiProviderType.OPENAI_COMPATIBLE) req.baseUrl!!.trim().trimEnd('/') else null

    /**
     * The key [req] would use: its own, none for an empty string, and for null the stored
     * one - but only while type and address stay the same. A key belongs to the host it was
     * entered for and must never be sent anywhere else.
     */
    private fun keyFor(stored: AiProviderEntity?, req: ProviderRequest): String? = when {
        req.apiKey == null ->
            stored?.takeIf { it.type == req.type && it.baseUrl == normalisedBaseUrl(req) }?.apiKey
        req.apiKey.isEmpty() -> null
        else -> req.apiKey
    }

    private fun validate(req: ProviderRequest, administrator: Boolean) {
        require(req.model.isNotBlank()) { "Choose a model" }
        require(MODEL_NAME.matches(req.model.trim())) {
            "A model name is up to 200 letters, digits and . _ : / @ -"
        }
        require(req.apiKey == null || req.apiKey.length <= MAX_KEY_LENGTH) { "The key is too long" }
        if (req.type != AiProviderType.OPENAI_COMPATIBLE) return
        val url = req.baseUrl?.trim()?.trimEnd('/')
        require(!url.isNullOrBlank()) { "Enter the address of the model's API" }
        require(url.length <= MAX_BASE_URL_LENGTH) { "The address is too long" }
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

    private fun endpointFor(type: AiProviderType, baseUrl: String?) = when (type) {
        AiProviderType.OPENAI_COMPATIBLE -> baseUrl ?: OPENAI_URL
        AiProviderType.ANTHROPIC -> ANTHROPIC_URL
        AiProviderType.GEMINI -> GEMINI_URL
    }

    private fun resolved(row: AiProviderEntity, source: AiProviderSource) = ResolvedProvider(
        type = row.type,
        baseUrl = endpointFor(row.type, row.baseUrl),
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
