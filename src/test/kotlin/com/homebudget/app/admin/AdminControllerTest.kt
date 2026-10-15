package com.homebudget.app.admin

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.Role
import com.homebudget.app.repository.InstanceSettingRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.AuthFlowService
import com.homebudget.app.service.RegistrationService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
class AdminControllerTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var settings: InstanceSettingRepository
    @Autowired private lateinit var authFlow: AuthFlowService
    @Autowired private lateinit var objectMapper: ObjectMapper

    private lateinit var admin: UserPrincipal
    private lateinit var member: UserPrincipal

    @BeforeEach
    fun setUp() {
        val made = identity.newMember("admin")
        users.save(made.user.apply { role = Role.ADMIN })
        admin = UserPrincipal(made.user, made.household)
        member = identity.newMember("member")
    }

    @AfterEach
    fun tearDown() = settings.deleteById(RegistrationService.REGISTRATION_KEY).let { }

    private fun asUser(p: UserPrincipal) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

    @Test
    fun `only an administrator reaches the admin API`() {
        mvc.perform(get("/api/admin/users").with(asUser(member))).andExpect(status().isForbidden)
        mvc.perform(get("/api/admin/users").with(asUser(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.email == '${member.user.email}')].householdId").value(member.household.id.toInt()))
    }

    @Test
    fun `an administrator opens and closes registration`() {
        mvc.perform(put("/api/admin/registration").with(asUser(admin))
            .contentType(MediaType.APPLICATION_JSON).content("""{"open":true}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.open").value(true))
            .andExpect(jsonPath("$.lockedByEnvironment").value(false))
        mvc.perform(get("/api/admin/registration").with(asUser(admin)))
            .andExpect(jsonPath("$.open").value(true))
    }

    @Test
    fun `a reset link works once`() {
        val body = mvc.perform(post("/api/admin/users/${member.user.id}/reset-link").with(asUser(admin)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val path = objectMapper.readTree(body)["path"].asText()
        val token = path.substringAfter("token=")

        authFlow.resetPassword(token, "a-brand-new-password")
        assertThrows<RuntimeException> { authFlow.resetPassword(token, "another-new-password") }
    }

    @Test
    fun `an administrator deletes a user but not themselves`() {
        mvc.perform(delete("/api/admin/users/${member.user.id}").with(asUser(admin))).andExpect(status().isNoContent)
        assertFalse(users.existsById(member.user.id))
        mvc.perform(delete("/api/admin/users/${admin.user.id}").with(asUser(admin))).andExpect(status().isBadRequest)
    }
}
