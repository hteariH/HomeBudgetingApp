package com.homebudget.app.household

import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.HouseholdRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.HouseholdService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** An invitation admits a newcomer straight into the family, even with registration closed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:register-invite;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.instance.registration=closed"
])
class RegisterWithInviteTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var householdService: HouseholdService
    @Autowired private lateinit var householdRepository: HouseholdRepository
    @Autowired private lateinit var householdMemberRepository: HouseholdMemberRepository
    @Autowired private lateinit var inviteService: InviteService

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun register(email: String, inviteToken: String? = null) = mvc.perform(
        post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
            """{"email":"$email","password":"a-long-enough-password","firstName":"Kid","lastName":"A"""" +
                (inviteToken?.let { ""","inviteToken":"$it"""" } ?: "") + "}"
        )
    )

    @Test
    fun `registering through an invite joins the family even with registration closed`() {
        register("parent@example.com").andExpect(status().isOk)
        val parent = users.findByEmail("parent@example.com").orElseThrow()
        val family = householdService.householdOf(parent)
        val principal = UserPrincipal(parent, family)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        val token = inviteService.create(null).path.substringAfter("token=")
        SecurityContextHolder.clearContext()

        register("kid@example.com", token).andExpect(status().isOk)

        val kid = users.findByEmail("kid@example.com").orElseThrow()
        val membership = householdMemberRepository.findFirstByUserId(kid.id)!!
        assertEquals(family.id, membership.household.id)
        assertEquals(HouseholdRole.MEMBER, membership.role)
        assertEquals(2, householdMemberRepository.findAllByHouseholdId(family.id).size)
        assertEquals(1, householdRepository.count(), "the newcomer gets no household of their own")

        register("stranger@example.com").andExpect(status().isForbidden)
        register("second-kid@example.com", token).andExpect(status().isGone)
    }
}
