package com.homebudget.app.oauth

import com.homebudget.app.security.SecurityUtils
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

data class AuthorizeDecision(
    val clientId: String? = null,
    val redirectUri: String? = null,
    val codeChallenge: String? = null,
    val state: String? = null,
    val scope: String? = null,
    val resource: String? = null,
    val approve: Boolean = false
)

data class ConsentClient(val clientName: String, val redirectHost: String)

/**
 * The authorization endpoint and the two calls the consent page makes.
 *
 * `GET /oauth/authorize` only validates and hands the browser to the web app's consent page,
 * which signs the user in if needed and asks. A client or redirect URI that does not check
 * out gets a plain page: sending an error to an unverified address would make this server an
 * open redirector.
 */
@RestController
class OAuthAuthorizeController(
    private val oauth: OAuthService,
    private val securityUtils: SecurityUtils
) {
    companion object {
        /** What the consent page receives, in this order. */
        private val FORWARDED = listOf(
            "response_type", "client_id", "redirect_uri", "code_challenge", "code_challenge_method",
            "state", "scope", "resource"
        )
    }

    @GetMapping("/oauth/authorize")
    fun authorize(request: HttpServletRequest): ResponseEntity<String> {
        requireAvailable()
        fun p(name: String): String? = request.getParameter(name)
        val authorization = AuthorizationRequest(
            clientId = p("client_id"),
            redirectUri = p("redirect_uri"),
            responseType = p("response_type"),
            codeChallenge = p("code_challenge"),
            codeChallengeMethod = p("code_challenge_method"),
            scope = p("scope"),
            resource = p("resource")
        )
        try {
            oauth.check(authorization)
        } catch (e: UntrustedRedirectException) {
            return ResponseEntity.badRequest()
                .contentType(MediaType("text", "plain", Charsets.UTF_8))
                .body("This connection request cannot be completed. ${e.message}")
        } catch (e: OAuthException) {
            return redirect(oauth.withQuery(authorization.redirectUri!!, "error" to e.error, "state" to p("state")))
        }
        val consent = oauth.withQuery("${oauth.base}/oauth/consent", *FORWARDED.map { it to p(it) }.toTypedArray())
        return redirect(consent)
    }

    @GetMapping("/api/oauth/clients/{clientId}")
    fun client(@PathVariable clientId: String, @RequestParam(required = false) redirectUri: String?): ConsentClient {
        requireAvailable()
        val client = oauth.client(clientId)
        require(client.allowsRedirect(redirectUri)) { "The redirect URI is not registered for this assistant" }
        return ConsentClient(client.clientName, URI(redirectUri!!).host)
    }

    @PostMapping("/api/oauth/authorize")
    fun decide(@RequestBody body: AuthorizeDecision): Map<String, String> {
        requireAvailable()
        val request = AuthorizationRequest(
            clientId = body.clientId,
            redirectUri = body.redirectUri,
            responseType = "code",
            codeChallenge = body.codeChallenge,
            codeChallengeMethod = "S256",
            scope = body.scope,
            resource = body.resource
        )
        return mapOf("redirect" to oauth.decide(securityUtils.getCurrentUser(), request, body.state, body.approve))
    }

    @ExceptionHandler(OAuthException::class)
    fun invalid(e: OAuthException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.badRequest().body(mapOf("error" to e.error, "error_description" to e.message))

    private fun redirect(location: String): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build()

    private fun requireAvailable() {
        if (!oauth.available()) throw NoSuchElementException("Not found")
    }
}
