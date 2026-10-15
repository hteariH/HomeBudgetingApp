package com.homebudget.app.admin

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.Role
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.UserService
import org.junit.jupiter.api.AfterEach
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** An instance must not be left with users and nobody to administer it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@Import(TestIdentity::class)
@TestPropertySource(properties = [
    // Its own database: the check counts every administrator on the instance.
    "spring.datasource.url=jdbc:h2:mem:last-administrator;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
])
class LastAdministratorTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var userService: UserService

    // Each case starts from an empty instance, whatever order they run in.
    @AfterEach
    fun tearDown() = users.findAll().forEach { userService.deleteUser(it) }

    private fun asUser(p: UserPrincipal) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

    private fun newAdmin(prefix: String): UserPrincipal {
        val made = identity.newMember(prefix)
        users.save(made.user.apply { role = Role.ADMIN })
        return UserPrincipal(made.user, made.household)
    }

    @Test
    fun `the only administrator cannot leave while others remain`() {
        val admin = newAdmin("admin")
        val member = identity.newMember("member")

        mvc.perform(delete("/api/users/me").with(asUser(admin)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("only administrator")))
        assertTrue(users.existsById(admin.user.id))

        // A member leaving is unaffected; once alone, the administrator can go too.
        mvc.perform(delete("/api/users/me").with(asUser(member))).andExpect(status().isNoContent)
        mvc.perform(delete("/api/users/me").with(asUser(admin))).andExpect(status().isNoContent)
        assertFalse(users.existsById(admin.user.id))
    }

    @Test
    fun `an administrator with a fellow administrator can leave`() {
        val first = newAdmin("first-admin")
        val second = newAdmin("second-admin")
        identity.newMember("member")

        mvc.perform(delete("/api/users/me").with(asUser(first))).andExpect(status().isNoContent)
        mvc.perform(delete("/api/users/me").with(asUser(second))).andExpect(status().isBadRequest)
    }
}
