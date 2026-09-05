package com.personalmoney.core.network

import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `GlobalExceptionHandler.ErrorResponse` on the server, minus the parts a client cannot use.
 */
@Serializable
private data class ApiErrorBody(val message: String? = null)

private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Statuses whose `message` the server writes for the user to read.
 *
 * The bank ones are the reason this exists: a rejected token comes back as 422 with the
 * bank's own explanation, and repeating that is far more use than any sentence this app
 * could invent. 500 is deliberately absent - the server puts a raw exception message in
 * that one, which is for a log and not for a person.
 */
private val USER_FACING = setOf(
    HttpStatusCode.BadRequest,
    HttpStatusCode.NotFound,
    // A limit of the free plan. The server's sentence names the limit and what to do about
    // it, and without this the one refusal a user can actually act on is the one that comes
    // back as a shrug.
    HttpStatusCode.PaymentRequired,
    HttpStatusCode.UnprocessableEntity,
    HttpStatusCode.BadGateway,
)

/**
 * Whether this failure was the free plan's doing rather than a mistake in the request.
 *
 * The server answers 402 for exactly one reason - see its `PlanLimitException` - which is
 * what lets a screen offer the way out (the paywall) alongside the server's sentence,
 * instead of showing a refusal with nothing to do about it.
 */
fun Throwable.isPlanLimit(): Boolean =
    (this as? ResponseException)?.response?.status == HttpStatusCode.PaymentRequired

/**
 * The message the server meant for the user, or null when there is not one.
 *
 * Null rather than a default so the caller supplies a *localised* fallback - this layer has
 * no business reaching for a string resource, and the server does not speak the user's
 * language anyway.
 */
suspend fun Throwable.serverMessage(): String? {
    val response = (this as? ResponseException)?.response ?: return null
    if (response.status !in USER_FACING) return null
    // The response Ktor's validator saved, so reading it here is safe and repeatable.
    val body = runCatching { response.bodyAsText() }.getOrNull() ?: return null
    return runCatching { errorJson.decodeFromString<ApiErrorBody>(body) }
        .getOrNull()
        ?.message
        ?.takeIf { it.isNotBlank() }
}
