package com.homebudget.app.instance

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

/** `REGISTRATION=open` keeps the door open after the first account. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:open-registration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.instance.registration=open"
])
class OpenRegistrationTest {

    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `an instance configured open stays open`() {
        for (email in listOf("one@example.com", "two@example.com")) {
            mvc.perform(
                post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                    """{"email":"$email","password":"a-long-enough-password","firstName":"A","lastName":"B"}"""
                )
            ).andExpect(status().isOk)
        }
        mvc.perform(get("/api/instance")).andExpect(jsonPath("$.registrationOpen").value(true))
    }
}
