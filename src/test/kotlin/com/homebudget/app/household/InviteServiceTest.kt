package com.homebudget.app.household

import com.homebudget.app.TestIdentity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
class InviteServiceTest {

    @Autowired lateinit var identity: TestIdentity
    @Autowired lateinit var invites: InviteService
    @Autowired lateinit var repo: HouseholdInviteRepository

    @AfterEach fun tearDown() = SecurityContextHolder.clearContext()

    private fun tokenOf(path: String) = path.substringAfter("token=")

    @Test
    fun `an owner creates an invite that previews its household and inviter`() {
        val owner = identity.newMember("anna")
        identity.signIn(owner)
        val created = invites.create(origin = "https://money.example.com")

        assertTrue(created.path.startsWith("/join?token="))
        assertNotNull(created.qrRows)
        val preview = invites.preview(tokenOf(created.path))
        assertEquals(owner.household.name, preview.householdName)
        assertEquals("anna Test", preview.invitedBy)
        assertEquals(1, invites.live().size)
    }

    @Test
    fun `only the raw token's hash is stored`() {
        identity.signIn(identity.newMember("anna"))
        val raw = tokenOf(invites.create(null).path)
        assertNotNull(repo.findByTokenHash(InviteService.hash(raw)))
        assertTrue(repo.findAll().none { it.tokenHash == raw })
    }

    @Test
    fun `a member cannot invite`() {
        val owner = identity.newMember("anna")
        val member = identity.joinAs("boris", owner.household)
        identity.signIn(member)
        assertThrows<AccessDeniedException> { invites.create(null) }
    }

    @Test
    fun `revoked and unknown tokens are refused differently`() {
        identity.signIn(identity.newMember("anna"))
        val created = invites.create(null)
        invites.revoke(created.id)
        assertThrows<InviteGoneException> { invites.preview(tokenOf(created.path)) }
        assertThrows<NoSuchElementException> { invites.preview("not-a-token") }
    }

    @Test
    fun `an expired invite is gone`() {
        identity.signIn(identity.newMember("anna"))
        val created = invites.create(null)
        val invite = repo.findById(created.id).orElseThrow()
        repo.save(invite.copy(expiresAt = invite.createdAt.minusSeconds(1)))
        assertThrows<InviteGoneException> { invites.preview(tokenOf(created.path)) }
    }

    @Test
    fun `a household holds at most ten live invites`() {
        identity.signIn(identity.newMember("anna"))
        repeat(10) { invites.create(null) }
        assertThrows<InviteLimitException> { invites.create(null) }
    }
}
