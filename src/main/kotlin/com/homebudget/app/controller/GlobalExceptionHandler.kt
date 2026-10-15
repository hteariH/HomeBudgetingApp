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

    /** The instance is not taking new accounts; only its administrator can change that. */
    @ExceptionHandler(com.homebudget.app.service.RegistrationClosedException::class)
    fun handleRegistrationClosed(ex: com.homebudget.app.service.RegistrationClosedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(status = 403, error = "Registration Closed", message = ex.message)
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

    /** An invitation that existed and no longer works: expired, used, or withdrawn. */
    @ExceptionHandler(com.homebudget.app.household.InviteGoneException::class)
    fun handleInviteGone(ex: com.homebudget.app.household.InviteGoneException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.GONE).body(
            ErrorResponse(status = 410, error = "Invitation Gone", message = ex.message)
        )

    @ExceptionHandler(com.homebudget.app.household.InviteLimitException::class)
    fun handleInviteLimit(ex: com.homebudget.app.household.InviteLimitException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(status = 409, error = "Too Many Invitations", message = ex.message)
        )

    /** The environment decides the AI provider; the admin page cannot overrule it. */
    @ExceptionHandler(com.homebudget.app.ai.AiProviderLockedException::class)
    fun handleAiProviderLocked(ex: com.homebudget.app.ai.AiProviderLockedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(status = 409, error = "Set By Environment", message = ex.message)
        )

    /** No model reads receipts for this household; an owner or the administrator can set one up. */
    @ExceptionHandler(com.homebudget.app.receipt.ReceiptScanUnavailableException::class)
    fun handleReceiptScanUnavailable(ex: com.homebudget.app.receipt.ReceiptScanUnavailableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(status = 409, error = "Not Set Up", message = ex.message)
        )

    /** A receipt photo over 15 MB. */
    @ExceptionHandler(com.homebudget.app.receipt.ReceiptTooLargeException::class)
    fun handlePhotoTooLarge(ex: com.homebudget.app.receipt.ReceiptTooLargeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            ErrorResponse(status = 413, error = "Payload Too Large", message = ex.message)
        )

    /**
     * Any upload over the multipart limit (`spring.servlet.multipart`). Generic on purpose:
     * the CSV import is a multipart endpoint too.
     */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(ex: org.springframework.web.multipart.MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            ErrorResponse(status = 413, error = "Payload Too Large", message = "The file is too large")
        )

    /** Not a JPEG, PNG or WebP this server can read - HEIC included. */
    @ExceptionHandler(com.homebudget.app.receipt.UnsupportedImageException::class)
    fun handleUnsupportedImage(ex: com.homebudget.app.receipt.UnsupportedImageException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
            ErrorResponse(status = 415, error = "Unsupported Media Type", message = ex.message)
        )

    /** The caller's membership moved after the request was authenticated; they sync again. */
    @ExceptionHandler(com.homebudget.app.household.HouseholdChangedException::class)
    fun handleHouseholdChanged(ex: com.homebudget.app.household.HouseholdChangedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(status = 409, error = "Household Changed", message = ex.message)
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
