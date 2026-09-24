package com.homebudget.app.config

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The web app is one React document for the whole site, and /app is routes of it rather
 * than a directory of its own. Every address under /app has to answer with that document,
 * however deep - a reload on /app/transactions/12 is the case that breaks first.
 *
 * Runs against the stand-in static files in src/test/resources, so it does not depend on
 * the frontend having been built.
 */
@SpringBootTest(classes = [com.homebudget.app.HomeBudgetingApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebConfigTest {

    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `the bare app address is the React shell`() {
        mvc.perform(get("/app")).andExpect(status().isOk).andExpect(forwardedUrl("/index.html"))
        mvc.perform(get("/app/")).andExpect(status().isOk).andExpect(forwardedUrl("/index.html"))
    }

    @Test
    fun `deep app routes are the React shell too`() {
        for (path in listOf("/app/accounts", "/app/accounts/edit", "/app/transactions/12", "/app/import/homemoney")) {
            mvc.perform(get(path))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("test shell")))
                // The shell names the current bundle, so it must never be served stale.
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
        }
    }

    @Test
    fun `hashed bundles are cached for good`() {
        mvc.perform(get("/assets/app-test.js"))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", containsString("immutable")))
    }

    @Test
    fun `the API is not swallowed by the shell`() {
        mvc.perform(get("/api/accounts")).andExpect(status().is4xxClientError)
    }
}
