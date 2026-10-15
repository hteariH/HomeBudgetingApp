package com.homebudget.app.service

import com.homebudget.app.TestIdentity
import com.homebudget.app.dto.SyncAccount
import com.homebudget.app.dto.SyncPayload
import com.homebudget.app.dto.SyncPushRequest
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Two members pushing into one household at the same moment must still get distinct,
 * ordered stamps - the pull cursor depends on it.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
class ConcurrentPushTest {

    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var syncService: SyncService

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `simultaneous pushes from two members get distinct stamps and all arrive`() {
        val alice = identity.newMember("alice")
        val partner = identity.joinAs("partner", alice.household)
        val rounds = 25
        val stamps = Collections.synchronizedList(mutableListOf<Instant>())
        val pool = Executors.newFixedThreadPool(2)

        repeat(rounds) {
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            for (who in listOf(alice, partner)) {
                pool.execute {
                    try {
                        start.await()
                        stamps += pushOneAccount(who)
                    } finally {
                        done.countDown()
                    }
                }
            }
            start.countDown()
            done.await(10, TimeUnit.SECONDS)
        }
        pool.shutdown()

        assertEquals(rounds * 2, stamps.size, "a push failed")
        assertEquals(stamps.size, stamps.toSet().size, "two pushes shared a stamp")

        identity.signIn(alice)
        assertEquals(rounds * 2, syncService.pull(Instant.EPOCH, 500).changes.accounts.size)
    }

    private fun pushOneAccount(who: UserPrincipal): Instant {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(who, null, who.authorities)
        try {
            return syncService.push(
                SyncPushRequest(SyncPayload(accounts = listOf(
                    SyncAccount(uuid = UUID.randomUUID().toString(), name = "acc", currencyCode = "USD")
                )))
            ).serverTime
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}
