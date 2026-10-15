package com.personalmoney.core.household

import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.createJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the phone shows beside a transaction to say who wrote it: initials, and only once
 * there is someone else to tell apart from.
 */
class HouseholdMembersTest {

    private fun member(id: Long, first: String, last: String, email: String, role: String) =
        """{"userId":$id,"firstName":"$first","lastName":"$last","email":"$email","role":"$role","joinedAt":"2026-01-01T00:00:00Z"}"""

    private fun household(vararg members: String) =
        """{"id":7,"name":"Home","baseCurrency":null,"members":[${members.joinToString(",")}]}"""

    private fun membersOver(body: String, status: HttpStatusCode = HttpStatusCode.OK): HouseholdMembers {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/household") {
                if (status.value >= 400) respondError(status)
                else respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(createJson()) }
        }
        return HouseholdMembers(HomeBudgetApi(client))
    }

    private val twoMembers = household(
        member(1, "Anna", "Berkunsky", "anna@example.com", "OWNER"),
        member(2, "ivan", "petrov", "ivan@example.com", "MEMBER"),
    )

    @Test
    fun `two members are told apart by their initials`() = runTest {
        val members = membersOver(twoMembers)
        members.refresh()

        assertEquals(2, members.members.value.size)
        assertEquals("AB", members.initialsFor(1))
        assertEquals("IP", members.initialsFor(2))
    }

    @Test
    fun `a household of one shows no initials at all`() = runTest {
        val members = membersOver(household(member(1, "Anna", "Berkunsky", "anna@example.com", "OWNER")))
        members.refresh()

        assertNull(members.initialsFor(1))
        assertNull(members.initialsFor(99))
    }

    @Test
    fun `an unknown or missing author has no initials`() = runTest {
        val members = membersOver(twoMembers)
        members.refresh()

        assertNull(members.initialsFor(99))
        assertNull(members.initialsFor(null))
    }

    @Test
    fun `a member without a name falls back to the start of their email`() = runTest {
        val members = membersOver(
            household(
                member(1, "", "", "zed@example.com", "OWNER"),
                member(2, "Ivan", "", "ivan@example.com", "MEMBER"),
            )
        )
        members.refresh()

        assertEquals("Z", members.initialsFor(1))
        assertEquals("I", members.initialsFor(2))
    }

    @Test
    fun `a failed refresh keeps what was known`() = runTest {
        var fail = false
        val engine = MockEngine {
            if (fail) respondError(HttpStatusCode.InternalServerError)
            else respond(twoMembers, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(createJson()) }
        }
        val members = HouseholdMembers(HomeBudgetApi(client))
        members.refresh()
        fail = true
        members.refresh()

        assertTrue(members.members.value.isNotEmpty())
        assertEquals("AB", members.initialsFor(1))
    }
}
