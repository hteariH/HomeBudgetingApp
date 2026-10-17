package com.homebudget.app.oauth

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Discovery: RFC 9728 for `/mcp`, RFC 8414 for the authorization server. */
@RestController
class OAuthMetadataController(private val oauth: OAuthService) {

    @GetMapping("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp")
    fun protectedResource(): Map<String, Any> {
        val base = requireAvailable()
        return mapOf(
            "resource" to "$base/mcp",
            "authorization_servers" to listOf(base),
            "scopes_supported" to listOf(OAuthService.SCOPE),
            "bearer_methods_supported" to listOf("header")
        )
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    fun authorizationServer(): Map<String, Any> {
        val base = requireAvailable()
        return mapOf(
            "issuer" to base,
            "authorization_endpoint" to "$base/oauth/authorize",
            "token_endpoint" to "$base/oauth/token",
            "registration_endpoint" to "$base/oauth/register",
            "revocation_endpoint" to "$base/oauth/revoke",
            "response_types_supported" to listOf("code"),
            "grant_types_supported" to listOf("authorization_code", "refresh_token"),
            "code_challenge_methods_supported" to listOf("S256"),
            "token_endpoint_auth_methods_supported" to listOf("none"),
            "scopes_supported" to listOf(OAuthService.SCOPE)
        )
    }

    private fun requireAvailable(): String {
        if (!oauth.available()) throw NoSuchElementException("Not found")
        return oauth.base
    }
}
