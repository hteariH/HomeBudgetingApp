package com.homebudget.app.controller

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

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(status = 500, error = "Internal Server Error", message = ex.message)
        )
}
