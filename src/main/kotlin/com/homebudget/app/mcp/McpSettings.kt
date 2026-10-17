package com.homebudget.app.mcp

import com.homebudget.app.domain.InstanceSetting
import com.homebudget.app.repository.InstanceSettingRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Whether assistants may connect over MCP. Off by default. `MCP_ENABLED` (`true`/`false`)
 * wins; otherwise the administrator's switch, the instance setting [KEY].
 */
@Component
class McpSettings(
    private val settings: InstanceSettingRepository,
    @Value("\${app.mcp.enabled:}") environment: String
) {
    companion object {
        const val KEY = "mcp.enabled"
        private val EXPLICIT = setOf("true", "false")
    }

    private val environment = environment.trim().lowercase()

    init {
        if (this.environment.isNotEmpty() && this.environment !in EXPLICIT) {
            LoggerFactory.getLogger(javaClass).warn(
                "MCP_ENABLED={} is not recognised (use true or false); treating it as unset", environment
            )
        }
    }

    fun lockedByEnvironment(): Boolean = environment in EXPLICIT

    fun enabled(): Boolean =
        if (lockedByEnvironment()) environment == "true"
        else settings.findById(KEY).map { it.value == "true" }.orElse(false)

    @Transactional
    fun setEnabled(enabled: Boolean) {
        check(!lockedByEnvironment()) { "MCP is set by the MCP_ENABLED environment variable" }
        settings.save(InstanceSetting(KEY, enabled.toString()))
    }
}
