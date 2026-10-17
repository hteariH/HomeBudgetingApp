package com.homebudget.app.oauth

import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** The token endpoint (RFC 6749 §3.2) and revocation (RFC 7009). Form-encoded, public clients. */
@RestController
class OAuthTokenController(private val oauth: OAuthService) {

    @PostMapping("/oauth/token")
    fun token(@RequestParam params: Map<String, String>): ResponseEntity<Map<String, Any>> {
        requireAvailable()
        val issued = when (params["grant_type"]) {
            "authorization_code" -> oauth.exchangeCode(
                params["code"], params["redirect_uri"], params["client_id"], params["code_verifier"]
            )
            "refresh_token" -> oauth.refresh(params["refresh_token"], params["client_id"])
            null, "" -> throw OAuthException("invalid_request", "grant_type is required")
            else -> throw OAuthException("unsupported_grant_type", "Use authorization_code or refresh_token")
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(
            mapOf(
                "access_token" to issued.accessToken,
                "token_type" to "Bearer",
                "expires_in" to issued.expiresIn,
                "refresh_token" to issued.refreshToken,
                "scope" to issued.scope
            )
        )
    }

    /** Always 200, whether or not the token meant anything (RFC 7009 §2.2). */
    @PostMapping("/oauth/revoke")
    fun revoke(@RequestParam(required = false) token: String?): ResponseEntity<Void> {
        requireAvailable()
        oauth.revoke(token)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build()
    }

    @ExceptionHandler(OAuthException::class)
    fun invalid(e: OAuthException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
            .body(mapOf("error" to e.error, "error_description" to e.message))

    private fun requireAvailable() {
        if (!oauth.available()) throw NoSuchElementException("Not found")
    }
}
