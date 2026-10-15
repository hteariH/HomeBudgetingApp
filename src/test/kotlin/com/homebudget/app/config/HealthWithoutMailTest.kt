package com.homebudget.app.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * A self-hoster without SMTP leaves MAIL_HOST empty, which still gives Spring a mail sender with a
 * blank host. The container's HEALTHCHECK must not turn that into "unhealthy".
 */
@SpringBootTest(properties = ["spring.mail.host=", "spring.mail.port=587"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthWithoutMailTest {

    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `health stays up when mail is not configured`() {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk).andExpect(jsonPath("$.status").value("UP"))
    }
}
