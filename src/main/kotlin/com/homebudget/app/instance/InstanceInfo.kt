package com.homebudget.app.instance

/** What a client needs to know about an instance before anyone signs in. */
data class InstanceInfo(
    val name: String,
    val version: String,
    /** Bumped on a breaking API change; clients compare it with the range they support. */
    val apiVersion: Int,
    /** Nobody has an account yet: the next registration creates the administrator. */
    val setupRequired: Boolean,
    val registrationOpen: Boolean,
    /** False hides "forgot password"; an administrator issues reset links instead. */
    val mailEnabled: Boolean,
    val bankProviders: List<String>,
    /** An AI model is configured for the instance, so receipts can be scanned. */
    val receiptScan: Boolean,
    /** Assistants may connect over MCP; Settings shows Assistants. */
    val mcpEnabled: Boolean,
    /** MCP is on and APP_BASE_URL is set, so web connectors can sign in with OAuth. */
    val oauthAvailable: Boolean,
    /** APP_BASE_URL without a trailing slash, for the connector address; only when [oauthAvailable]. */
    val baseUrl: String? = null
)
