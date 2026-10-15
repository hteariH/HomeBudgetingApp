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

/** The container's HEALTHCHECK and a NAS's status page read this, signed out. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointTest {

    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `health answers without a session and says nothing else`() {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk).andExpect(jsonPath("$.status").value("UP"))
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound)
    }
}
