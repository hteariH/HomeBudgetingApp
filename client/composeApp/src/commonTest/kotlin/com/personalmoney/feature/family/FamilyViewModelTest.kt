package com.personalmoney.feature.family

import com.personalmoney.core.auth.FakeTokenStore
import com.personalmoney.core.household.HouseholdMembers
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.createJson
import com.personalmoney.core.sync.SyncTrigger
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the Family screen offers: the owner can mint an invitation link, anyone else can
 * leave, and a refusal from the server is reported rather than swallowed.
 */
class FamilyViewModelTest {

    private fun familyTest(body: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            body()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class CountingSync : SyncTrigger {
        var requests = 0
        override fun requestSync() { requests++ }
    }

    private class Rig(
        val viewModel: FamilyViewModel,
        val calls: MutableList<String>,
        val sync: CountingSync,
    )

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun household() =
        """{"id":7,"name":"Home","baseCurrency":null,"members":[
            {"userId":1,"firstName":"Anna","lastName":"B","email":"anna@example.com","role":"OWNER"},
            {"userId":2,"firstName":"Ivan","lastName":"P","email":"ivan@example.com","role":"MEMBER"}]}"""

    private fun me(role: String) =
        """{"id":1,"email":"anna@example.com","householdId":7,"householdName":"Home","householdRole":"$role"}"""

    private fun rig(role: String, leaveStatus: HttpStatusCode = HttpStatusCode.NoContent): Rig {
        val calls = mutableListOf<String>()
        val engine = MockEngine { request ->
            val call = "${request.method.value} ${request.url.encodedPath}"
            calls += call
            when (call) {
                "GET /api/users/me" -> respond(me(role), HttpStatusCode.OK, json)
                "GET /api/household" -> respond(household(), HttpStatusCode.OK, json)
                "POST /api/household/invites" -> respond(
                    """{"id":5,"path":"/join?token=abc_DEF-123","expiresAt":"2026-10-22T00:00:00Z"}""",
                    HttpStatusCode.Created, json
                )
                "POST /api/household/leave" ->
                    if (leaveStatus.value < 400) respond("", leaveStatus)
                    else respond("""{"message":"Transfer ownership first"}""", leaveStatus, json)
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val api = HomeBudgetApi(
            HttpClient(engine) {
                expectSuccess = true
                install(ContentNegotiation) { json(createJson()) }
            }
        )
        val tokens = FakeTokenStore().apply { serverUrl = "https://money.example.com/" }
        val sync = CountingSync()
        return Rig(FamilyViewModel(api, HouseholdMembers(api), tokens, sync), calls, sync)
    }

    private suspend fun Rig.loaded() = viewModel.state.first { !it.busy && it.members.isNotEmpty() }

    @Test
    fun `an owner sees the members and can invite`() = familyTest {
        val rig = rig("OWNER")

        val state = rig.loaded()

        assertTrue(state.isOwner)
        assertEquals(listOf("Anna", "Ivan"), state.members.map { it.firstName })
    }

    @Test
    fun `inviting posts to the server and builds a link on the server address`() = familyTest {
        val rig = rig("OWNER")
        rig.loaded()

        rig.viewModel.invite()
        val state = rig.viewModel.state.first { it.inviteLink != null }

        assertTrue("POST /api/household/invites" in rig.calls)
        // The server's trailing slash and the path's leading one must not double up.
        assertEquals("https://money.example.com/join?token=abc_DEF-123", state.inviteLink)
        assertFalse(state.busy)
    }

    @Test
    fun `a member is not an owner`() = familyTest {
        val rig = rig("MEMBER")

        assertFalse(rig.loaded().isOwner)
    }

    @Test
    fun `leaving posts to the server, refreshes the household and asks for a sync`() = familyTest {
        val rig = rig("MEMBER")
        rig.loaded()
        val membersFetchedBefore = rig.calls.count { it == "GET /api/household" }

        rig.viewModel.leave()
        rig.viewModel.state.first { !it.busy }

        assertTrue("POST /api/household/leave" in rig.calls)
        assertEquals(1, rig.sync.requests)
        assertEquals(membersFetchedBefore + 1, rig.calls.count { it == "GET /api/household" })
        assertNull(rig.viewModel.state.value.error)
    }

    @Test
    fun `a refused leave shows the server's reason and does not sync`() = familyTest {
        val rig = rig("OWNER", leaveStatus = HttpStatusCode.BadRequest)
        rig.loaded()

        rig.viewModel.leave()
        val state = rig.viewModel.state.first { it.error != null }

        assertEquals("Transfer ownership first", state.error)
        assertEquals(0, rig.sync.requests)
        assertFalse(state.busy)
    }

    @Test
    fun `an invitation link is only the owner's to make`() = familyTest {
        val rig = rig("MEMBER")
        rig.loaded()

        rig.viewModel.invite()

        assertNull(rig.viewModel.state.value.inviteLink)
        assertTrue(rig.calls.none { it == "POST /api/household/invites" })
    }
}
