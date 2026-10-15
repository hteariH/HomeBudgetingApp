package com.homebudget.app.household

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.TestIdentity
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
class InviteEndpointsTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun asUser(p: UserPrincipal) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

    private fun createInvite(owner: UserPrincipal): Pair<Long, String> {
        val body = mvc.perform(
            post("/api/household/invites").with(asUser(owner))
                .contentType(MediaType.APPLICATION_JSON).content("""{"origin":"https://x.example"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.path").value(org.hamcrest.Matchers.startsWith("/join?token=")))
            .andExpect(jsonPath("$.qrRows").isArray)
            .andReturn().response.contentAsString
        val json = objectMapper.readTree(body)
        return json["id"].asLong() to json["path"].asText().substringAfter("token=")
    }

    @Test
    fun `an owner invites and anyone holding the link can preview it`() {
        val owner = identity.newMember("anna")
        val (_, token) = createInvite(owner)

        mvc.perform(get("/api/invites/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.householdName").value(owner.household.name))
            .andExpect(jsonPath("$.invitedBy").value("anna Test"))
    }

    @Test
    fun `invitation management needs a session`() {
        mvc.perform(get("/api/household/invites")).andExpect(status().is4xxClientError).andExpect { r ->
            check(r.response.status == 401 || r.response.status == 403) { "got ${r.response.status}" }
        }
        mvc.perform(post("/api/household/invites")).andExpect { r ->
            check(r.response.status == 401 || r.response.status == 403) { "got ${r.response.status}" }
        }
    }

    @Test
    fun `the preview is reachable without a session`() {
        // Not refused by the security chain: it reaches the controller, which says 404 for an unknown token.
        mvc.perform(get("/api/invites/not-a-real-token")).andExpect(status().isNotFound)
    }

    @Test
    fun `an unknown token is 404 and a revoked one is 410`() {
        val owner = identity.newMember("anna")
        val (id, token) = createInvite(owner)

        mvc.perform(get("/api/invites/nope")).andExpect(status().isNotFound)

        mvc.perform(delete("/api/household/invites/$id").with(asUser(owner))).andExpect(status().isNoContent)
        mvc.perform(get("/api/invites/$token")).andExpect(status().isGone)
    }

    @Test
    fun `accepting needs a session, joins once, and refuses one's own household`() {
        val owner = identity.newMember("anna")
        val joiner = identity.newMember("boris")
        val (_, token) = createInvite(owner)

        mvc.perform(post("/api/invites/$token/accept")).andExpect { r ->
            check(r.response.status == 401 || r.response.status == 403) { "got ${r.response.status}" }
        }
        mvc.perform(post("/api/invites/$token/accept").with(asUser(owner))).andExpect(status().isBadRequest)
        mvc.perform(post("/api/invites/$token/accept").with(asUser(joiner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.householdId").value(owner.household.id))
            .andExpect(jsonPath("$.householdName").value(owner.household.name))
        mvc.perform(post("/api/invites/$token/accept").with(asUser(joiner))).andExpect(status().isGone)
    }

    @Test
    fun `the owner lists live invites and a revoked one drops out`() {
        val owner = identity.newMember("anna")
        val (id, _) = createInvite(owner)

        mvc.perform(get("/api/household/invites").with(asUser(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(id))
        mvc.perform(delete("/api/household/invites/$id").with(asUser(owner))).andExpect(status().isNoContent)
        mvc.perform(get("/api/household/invites").with(asUser(owner)))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `a member cannot invite`() {
        val owner = identity.newMember("anna")
        val member = identity.joinAs("boris", owner.household)

        mvc.perform(post("/api/household/invites").with(asUser(member))).andExpect(status().isForbidden)
        mvc.perform(get("/api/household/invites").with(asUser(member))).andExpect(status().isForbidden)
    }

    @Test
    fun `an owner cannot revoke another household's invite`() {
        val (id, _) = createInvite(identity.newMember("anna"))
        val stranger = identity.newMember("carol")

        mvc.perform(delete("/api/household/invites/$id").with(asUser(stranger))).andExpect(status().isNotFound)
    }

    @Test
    fun `me reports the household and the caller's role`() {
        val owner = identity.newMember("anna")
        val member = identity.joinAs("boris", owner.household)

        mvc.perform(get("/api/users/me").with(asUser(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.householdId").value(owner.household.id))
            .andExpect(jsonPath("$.householdName").value(owner.household.name))
            .andExpect(jsonPath("$.householdRole").value("OWNER"))
        mvc.perform(get("/api/users/me").with(asUser(member)))
            .andExpect(jsonPath("$.householdId").value(owner.household.id))
            .andExpect(jsonPath("$.householdRole").value("MEMBER"))
    }

    @Test
    fun `the household lists its members in the order they joined`() {
        val owner = identity.newMember("anna")
        mvc.perform(get("/api/household").with(asUser(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.members[0].role").value("OWNER"))

        val member = identity.joinAs("boris", owner.household)
        mvc.perform(get("/api/household").with(asUser(member)))
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[0].firstName").value("anna"))
            .andExpect(jsonPath("$.members[1].firstName").value("boris"))
            .andExpect(jsonPath("$.members[1].role").value("MEMBER"))
    }
}
