package com.homebudget.app.instance

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

/**
 * HomeMoney's OAuth redirect has to be an absolute address on this instance, so without
 * APP_BASE_URL the import can only fail and is not offered.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["app.base-url="])
class HomeMoneyWithoutBaseUrlTest {

    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `HomeMoney is not offered without a base address`() {
        mvc.perform(get("/api/instance"))
            .andExpect(jsonPath("$.bankProviders[?(@ == 'HOMEMONEY')]").isEmpty)
            .andExpect(jsonPath("$.bankProviders[?(@ == 'CSV')]").isNotEmpty)
    }
}

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["app.base-url=https://money.example.com"])
class HomeMoneyWithBaseUrlTest {

    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `HomeMoney is offered once the base address is set`() {
        mvc.perform(get("/api/instance"))
            .andExpect(jsonPath("$.bankProviders[?(@ == 'HOMEMONEY')]").isNotEmpty)
    }
}
