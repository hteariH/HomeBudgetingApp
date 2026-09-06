package com.homebudget.app.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.LocalDateTime

data class ErrorResponse(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: Int,
    val error: String,
    val message: String?
)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(status = 404, error = "Not Found", message = ex.message)
        )

    /**
     * A static file that is not there.
     *
     * Without this it reaches the catch-all below and comes back as a 500 with a Java
     * message in it, which says the server is broken when nothing is. It matters most for
     * the client bundle under /app: a mis-pinned `web.bundle.tag` asks for a .wasm that
     * this build does not contain, and "500 Internal Server Error" sends whoever is
     * debugging that to entirely the wrong place.
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleMissingResource(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(status = 404, error = "Not Found", message = ex.message)
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(status = 400, error = "Bad Request", message = ex.message)
        )

    /**
     * A limit of the free plan, not a mistake in the request.
     *
     * 402 rather than 400 or 403 so a client can tell "this needs a subscription" from
     * "this was malformed" and from "this is not yours" without matching on the message.
     */
    @ExceptionHandler(com.homebudget.app.service.PlanLimitException::class)
    fun handlePlanLimit(ex: com.homebudget.app.service.PlanLimitException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(
            ErrorResponse(status = 402, error = "Plan Limit Reached", message = ex.message)
        )

    /**
     * Google could not be asked, so nothing is known about the purchase.
     *
     * 503 rather than 500 or 400 because it says the right thing to all three readers:
     * the client should offer "try again" instead of "your purchase is invalid", a
     * monitor should see a dependency being down rather than this server being broken,
     * and the user has not lost their money.
     */
    @ExceptionHandler(com.homebudget.app.billing.BillingUnavailableException::class)
    fun handleBillingUnavailable(
        ex: com.homebudget.app.billing.BillingUnavailableException
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            ErrorResponse(status = 503, error = "Billing Unavailable", message = ex.message)
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val messages = ex.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage}"
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(status = 400, error = "Validation Failed", message = messages)
        )
    }

    /**
     * The bank refused the credentials. That is the user's problem to fix, not a server
     * fault, so it must not be a 500 - the client shows this message verbatim.
     */
    @ExceptionHandler(com.homebudget.app.bank.BankAuthException::class)
    fun handleBankAuth(ex: com.homebudget.app.bank.BankAuthException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorResponse(status = 422, error = "Bank Rejected Credentials", message = ex.message)
        )

    /** The bank is reachable but unhappy - rate limit, outage, bad date range. */
    @ExceptionHandler(com.homebudget.app.bank.BankRequestException::class)
    fun handleBankRequest(ex: com.homebudget.app.bank.BankRequestException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
            ErrorResponse(status = 502, error = "Bank Unavailable", message = ex.message)
        )

    /**
     * Signed in, but not allowed to do this.
     *
     * Without a handler of its own, `@PreAuthorize` refusing lands in the catch-all below
     * and comes back as a 500 - which reads as "the server is broken" to a client and
     * fills the log with stack traces for what is a working access check. The message is
     * fixed rather than the exception's: telling a caller *why* they were refused is a
     * hint about what exists on the other side.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun handleAccessDenied(
        ex: org.springframework.security.access.AccessDeniedException
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(status = 403, error = "Forbidden", message = "Not allowed")
        )

    /**
     * A failed sign-in is a 401, not a 500.
     *
     * `AuthenticationManager.authenticate` throws `BadCredentialsException` for a wrong
     * password or an email that does not exist, and nothing here caught it - so the
     * commonest error in the whole application fell into the catch-all below, answered
     * 500, and logged a stack trace every time somebody mistyped. The client reads the
     * status: it has a "Wrong email or password" for 401 that could never fire, and
     * showed "The server is having trouble. Try again later." instead.
     *
     * Caught at the `AuthenticationException` base so a disabled or locked account cannot
     * reintroduce the same 500 later. The message is fixed and deliberately vague for the
     * same reason [handleAccessDenied]'s is: distinguishing "no such user" from "wrong
     * password" tells an attacker which addresses are registered.
     *
     * Rate limiting is not involved - `AuthRateLimitFilter` writes its own 429 straight to
     * the response and never reaches this class.
     */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException::class)
    fun handleAuthenticationFailure(
        ex: org.springframework.security.core.AuthenticationException
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(status = 401, error = "Unauthorized", message = "Wrong email or password")
        )

    /**
     * The catch-all, and the only handler here that logs.
     *
     * Everything above is a request the server understood and refused, which the client
     * shows to the user; there is nothing for an operator to do about those and logging
     * them would be noise. This one is the opposite: it means the server broke, the user
     * saw "something went wrong", and without a stack trace the only record of it is a
     * screenshot. That has already cost one debugging session.
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(status = 500, error = "Internal Server Error", message = ex.message)
        )
    }
}
