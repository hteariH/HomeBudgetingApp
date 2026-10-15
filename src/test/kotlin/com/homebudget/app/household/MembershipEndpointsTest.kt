package com.homebudget.app.household

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
class MembershipEndpointsTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity

    private fun asUser(p: UserPrincipal) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

    @Test
    fun `renaming answers with the new name, and only for the owner`() {
        val anna = identity.newMember("anna")
        val boris = identity.joinAs("boris", anna.household)

        mvc.perform(put("/api/household").with(asUser(boris)).contentType(MediaType.APPLICATION_JSON).content("""{"name":"x"}"""))
            .andExpect(status().isForbidden)
        mvc.perform(put("/api/household").with(asUser(anna)).contentType(MediaType.APPLICATION_JSON).content("""{"name":"The Annas"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("The Annas"))
            .andExpect(jsonPath("$.members.length()").value(2))
        mvc.perform(put("/api/household").with(asUser(anna)).contentType(MediaType.APPLICATION_JSON).content("""{"name":" "}"""))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `leaving, removing and handing over answer with the documented statuses`() {
        val anna = identity.newMember("anna")
        val boris = identity.joinAs("boris", anna.household)
        val clara = identity.joinAs("clara", anna.household)

        mvc.perform(post("/api/household/leave").with(asUser(anna))).andExpect(status().isBadRequest)
        mvc.perform(delete("/api/household/members/${clara.user.id}").with(asUser(boris))).andExpect(status().isForbidden)
        mvc.perform(delete("/api/household/members/${clara.user.id}").with(asUser(anna))).andExpect(status().isNoContent)
        mvc.perform(delete("/api/household/members/${clara.user.id}").with(asUser(anna))).andExpect(status().isNotFound)
        mvc.perform(post("/api/household/owner").with(asUser(anna)).contentType(MediaType.APPLICATION_JSON)
            .content("""{"userId":${boris.user.id}}""")).andExpect(status().isNoContent)
        mvc.perform(post("/api/household/leave").with(asUser(anna))).andExpect(status().isNoContent)
    }
}
