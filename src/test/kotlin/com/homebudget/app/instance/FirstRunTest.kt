package com.homebudget.app.instance

import com.homebudget.app.domain.Role
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.service.RegistrationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** A brand-new instance: the first account is the administrator, then the door closes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:first-run;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
])
class FirstRunTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var registration: RegistrationService

    private fun register(email: String) = mvc.perform(
        post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
            """{"email":"$email","password":"a-long-enough-password","firstName":"Ada","lastName":"Admin"}"""
        )
    )

    @Test
    fun `setup, first administrator, closed door, reopened by the administrator`() {
        mvc.perform(get("/api/instance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.setupRequired").value(true))
            .andExpect(jsonPath("$.registrationOpen").value(true))
            .andExpect(jsonPath("$.mailEnabled").value(false))
            .andExpect(jsonPath("$.apiVersion").value(1))
            .andExpect(jsonPath("$.bankProviders[?(@ == 'MONOBANK_CORPORATE')]").isEmpty)

        register("admin@example.com").andExpect(status().isOk)
        val admin = users.findByEmail("admin@example.com").orElseThrow()
        assertEquals(Role.ADMIN, admin.role)
        assertTrue(admin.emailVerified, "with no mail configured nobody can be asked to verify")

        mvc.perform(get("/api/instance"))
            .andExpect(jsonPath("$.setupRequired").value(false))
            .andExpect(jsonPath("$.registrationOpen").value(false))
        register("stranger@example.com").andExpect(status().isForbidden)

        registration.setRegistrationOpen(true)
        register("family@example.com").andExpect(status().isOk)
        assertEquals(Role.USER, users.findByEmail("family@example.com").orElseThrow().role)
    }
}
