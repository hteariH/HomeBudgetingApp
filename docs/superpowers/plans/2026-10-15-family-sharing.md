# Family Sharing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let several logins keep one household's books: single-use invitation links, joining with existing books moved in, owner-managed membership, author shown on transactions, on the server, web and Android.

**Architecture:** A `household_invites` table and a `household` package on the server (invite, membership and merge services behind owner/member/public endpoints). Joining moves the joiner's rows into the target household inside one transaction that takes both households' sync locks in id order, stamping moved rows through a shared `SyncClock`. The web gets a public `/join` page and an app `/app/household` page; Android gets a Family screen, a share sheet, a Room column for the author and a household-change check in the sync engine.

**Tech Stack:** Kotlin/Spring Boot/Hibernate/Flyway/Postgres (H2 PostgreSQL mode in tests) · React 19/TanStack Query/Vitest · Compose Multiplatform/Koin/Ktor/Room KMP.

**Spec:** [docs/superpowers/specs/2026-10-15-family-sharing-design.md](../specs/2026-10-15-family-sharing-design.md)

## Global Constraints

- Migrations: new file `V3__household_invites.sql`; `V1__baseline.sql` and `V2__seed_currency_catalog.sql` are released and must not change.
- Invite token: 32 random bytes, base64url without padding; only its SHA-256 hex is stored. Lifetime 7 days, single use, revocable. At most 10 live invites per household.
- Invite path returned to clients: `/join?token=<raw token>`.
- Roles: `OWNER` and `MEMBER` (existing `HouseholdRole`). Members are equal in the ledger; only the owner invites, revokes, removes members, renames the household, changes its base currency and transfers ownership.
- Status codes: unknown token 404; expired/used/revoked token 410; member calling owner-only 403; >10 live invites 409; owner-with-members accepting or leaving 400; accepting into one's own household 400.
- `/api/invites/**` and `POST /api/auth/register` are rate-limited like sign-in.
- Lock order: when two households are locked, lock the lower id first (`HouseholdRepository.lockById`).
- UI strings in en, ru and uk for every new key (web: dotted keys in `src/i18n/locales/{en,ru,uk}.ts` for public pages, snake_case keys in `app.{en,ru,uk}.ts` for the app; Android: `composeResources/values{,-ru,-uk}/strings.xml`).
- Do not push, tag or deploy.

## Commands

- Server: `./mvnw -B -Dfrontend.skip=true test` (`-Dtest=Name` for one class). Docker is not running: Testcontainers tests skip.
- Web: `cd src/main/frontend && npm test && npm run typecheck`.
- Android (from `client/`, Git Bash): `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; ./gradlew testDebugUnitTest assembleDebug --no-daemon`.

## File map

Server (`src/main/kotlin/com/homebudget/app/`): create `household/HouseholdInvite.kt` (entity), `household/HouseholdInviteRepository.kt`, `household/InviteService.kt`, `household/MembershipService.kt`, `household/HouseholdMergeService.kt`, `household/HouseholdController.kt`, `household/InviteController.kt`, `household/HouseholdDtos.kt`, `household/HouseholdExceptions.kt`, `service/SyncClock.kt`; modify `service/SyncService.kt`, `service/RegistrationService.kt`, `service/UserService.kt`, `service/Mappers.kt`, `dto/Dtos.kt`, `dto/SyncDtos.kt`, `controller/UserController.kt`, `controller/TransactionController.kt`, `controller/GlobalExceptionHandler.kt`, `service/TransactionService.kt`, `repository/TransactionRepository.kt`, `repository/HouseholdMemberRepository.kt`, `security/SecurityConfig.kt`, `security/AuthRateLimitFilter.kt`; `src/main/resources/db/migration/V3__household_invites.sql`.

Web (`src/main/frontend/src/`): create `landing/Join.tsx`, `landing/Join.test.tsx`, `app/features/household/HouseholdPage.tsx`, `app/features/household/HouseholdPage.test.tsx`, `app/features/household/initials.ts`; modify `App.tsx`, `landing/session.ts`, `landing/authApi.ts`, `app/AppRoot.tsx`, `app/lib/routes.ts`, `app/shell/sections.ts`, `app/api/{types,endpoints,queryClient,hooks}.ts`, `app/features/transactions/{TransactionsPage.tsx,TransactionRow.tsx,useLedger.ts,ledger.logic.ts}`, locales.

Android (`client/composeApp/src/`): create `commonMain/.../feature/family/{FamilyScreen.kt,FamilyViewModel.kt}`, `commonMain/.../core/ui/TextSharer.kt` + `androidMain/.../core/ui/TextSharer.android.kt`, `commonMain/.../core/household/HouseholdMembers.kt`; modify Room entity/migrations/database, sync DTO/mapper/engine, domain `Transaction`, `RoomTransactionRepository`, `HomeBudgetApi`, `ApiDtos`, `UserPreferences`, `TransactionsScreen`/`ViewModel`, `MoreScreen`, `Routes`, `PersonalMoneyNavHost`, DI modules, strings; schema `client/composeApp/schemas/.../5.json`.

---

# Phase A — Server

### Task 1: Invitations, household overview and the user's household on /me

**Files:**
- Create: `src/main/resources/db/migration/V3__household_invites.sql`, `household/HouseholdInvite.kt`, `household/HouseholdInviteRepository.kt`, `household/InviteService.kt`, `household/HouseholdDtos.kt`, `household/HouseholdExceptions.kt`, `household/HouseholdController.kt` (invite + overview endpoints only), `household/InviteController.kt` (preview only)
- Modify: `dto/Dtos.kt` (`UserResponse`), `service/Mappers.kt` (`User.toResponse`), `controller/UserController.kt`, `controller/GlobalExceptionHandler.kt`, `security/SecurityConfig.kt`, `security/AuthRateLimitFilter.kt`, `repository/HouseholdMemberRepository.kt`
- Test: `src/test/kotlin/com/homebudget/app/household/InviteServiceTest.kt`, `src/test/kotlin/com/homebudget/app/household/InviteEndpointsTest.kt`

**Interfaces:**
- Produces:
  - `HouseholdInvite(id, household, tokenHash, createdBy: User?, createdAt, expiresAt, usedAt: Instant?, usedBy: User?, revokedAt: Instant?)` with `fun isLive(now: Instant): Boolean`
  - `HouseholdInviteRepository.findByTokenHash(hash): HouseholdInvite?`, `findLive(householdId, now): List<HouseholdInvite>`, `countLive(householdId, now): Long`
  - `InviteService`: `create(origin: String?): CreatedInvite`, `live(): List<InviteSummary>`, `revoke(id: Long)`, `preview(rawToken: String): InvitePreview`, `requireLive(rawToken: String): HouseholdInvite` (404/410 semantics), `markUsed(invite, user)`; `InviteService.hash(raw): String`
  - `MembershipGuard` functions in `household/HouseholdExceptions.kt`: `fun requireOwner(membership: HouseholdMember)` throwing `AccessDeniedException`
  - Exceptions: `InviteGoneException(reason: String)` → 410, `InviteLimitException` → 409
  - DTOs: `CreatedInvite(id: Long, path: String, expiresAt: Instant, qrRows: List<String>?)`, `InviteSummary(id: Long, createdAt: Instant, expiresAt: Instant, createdBy: String?)`, `InvitePreview(householdName: String, invitedBy: String?, expiresAt: Instant)`, `HouseholdResponse(id: Long, name: String, baseCurrency: CurrencyResponse?, members: List<MemberResponse>)`, `MemberResponse(userId: Long, firstName: String, lastName: String, email: String, role: HouseholdRole, joinedAt: Instant)`, `CreateInviteRequest(origin: String? = null)`
  - `UserResponse` gains `householdId: Long`, `householdName: String`, `householdRole: HouseholdRole`; `User.toResponse(household: Household, role: HouseholdRole)`
  - `HouseholdMemberRepository.findFirstByUserId` already exists; add `findAllByHouseholdIdOrderByJoinedAtAsc(householdId)`
  - Endpoints: `POST/GET /api/household/invites`, `DELETE /api/household/invites/{id}`, `GET /api/household`, `GET /api/invites/{token}`

- [ ] **Step 1: Write the failing service test**

`src/test/kotlin/com/homebudget/app/household/InviteServiceTest.kt`:

```kotlin
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
```

Run: `./mvnw -B -Dfrontend.skip=true test -Dtest=InviteServiceTest` — Expected: FAIL to compile (package `household` does not exist).

- [ ] **Step 2: Migration, entity, repository**

`V3__household_invites.sql`:

```sql
-- V3: invitations into a household. Single use, seven days, revocable; only the token's
-- SHA-256 is stored, as for auth and refresh tokens.
CREATE TABLE household_invites (
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    household_id       BIGINT                      NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    token_hash         VARCHAR(64)                 NOT NULL UNIQUE,
    created_by_user_id BIGINT                      NULL REFERENCES users (id) ON DELETE SET NULL,
    created_at         TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at         TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    used_at            TIMESTAMP(3) WITH TIME ZONE NULL,
    used_by_user_id    BIGINT                      NULL REFERENCES users (id) ON DELETE SET NULL,
    revoked_at         TIMESTAMP(3) WITH TIME ZONE NULL
);

CREATE INDEX idx_invites_household ON household_invites (household_id);
```

`household/HouseholdInvite.kt`:

```kotlin
package com.homebudget.app.household

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.User
import jakarta.persistence.*
import java.time.Instant

/** A single-use way into a household, handed on by its owner. */
@Entity
@Table(name = "household_invites")
data class HouseholdInvite(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "household_id", nullable = false)
    val household: Household,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    var createdBy: User? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by_user_id")
    var usedBy: User? = null,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
) {
    fun isLive(now: Instant): Boolean = usedAt == null && revokedAt == null && expiresAt.isAfter(now)
}
```

`household/HouseholdInviteRepository.kt`:

```kotlin
package com.homebudget.app.household

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface HouseholdInviteRepository : JpaRepository<HouseholdInvite, Long> {
    fun findByTokenHash(tokenHash: String): HouseholdInvite?

    @Query(
        """
        SELECT i FROM HouseholdInvite i
        WHERE i.household.id = :householdId AND i.usedAt IS NULL AND i.revokedAt IS NULL
          AND i.expiresAt > :now
        ORDER BY i.createdAt
        """
    )
    fun findLive(householdId: Long, now: Instant): List<HouseholdInvite>

    @Query(
        """
        SELECT COUNT(i) FROM HouseholdInvite i
        WHERE i.household.id = :householdId AND i.usedAt IS NULL AND i.revokedAt IS NULL
          AND i.expiresAt > :now
        """
    )
    fun countLive(householdId: Long, now: Instant): Long
}
```

- [ ] **Step 3: Exceptions, DTOs, service**

`household/HouseholdExceptions.kt`:

```kotlin
package com.homebudget.app.household

import com.homebudget.app.domain.HouseholdMember
import com.homebudget.app.domain.HouseholdRole
import org.springframework.security.access.AccessDeniedException

/** The link existed and no longer works: expired, already used, or revoked. */
class InviteGoneException(message: String) : RuntimeException(message)

/** A household may hold only so many open invitations at once. */
class InviteLimitException(message: String) : RuntimeException(message)

fun requireOwner(membership: HouseholdMember) {
    if (membership.role != HouseholdRole.OWNER) {
        throw AccessDeniedException("Only the household's owner can do that")
    }
}
```

`household/HouseholdDtos.kt`:

```kotlin
package com.homebudget.app.household

import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.dto.CurrencyResponse
import java.time.Instant

data class CreateInviteRequest(val origin: String? = null)

data class CreatedInvite(val id: Long, val path: String, val expiresAt: Instant, val qrRows: List<String>?)

data class InviteSummary(val id: Long, val createdAt: Instant, val expiresAt: Instant, val createdBy: String?)

data class InvitePreview(val householdName: String, val invitedBy: String?, val expiresAt: Instant)

data class MemberResponse(
    val userId: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val role: HouseholdRole,
    val joinedAt: Instant
)

data class HouseholdResponse(
    val id: Long,
    val name: String,
    val baseCurrency: CurrencyResponse?,
    val members: List<MemberResponse>
)
```

`household/InviteService.kt`:

```kotlin
package com.homebudget.app.household

import com.homebudget.app.bank.QrCode
import com.homebudget.app.domain.User
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * Invitations into the signed-in owner's household.
 *
 * The raw token leaves the server exactly once, in the response to [create]; afterwards
 * only its hash exists, so a leaked database is not a set of working links.
 */
@Service
@Transactional
class InviteService(
    private val invites: HouseholdInviteRepository,
    private val members: HouseholdMemberRepository,
    private val securityUtils: SecurityUtils,
    private val clock: Clock
) {
    companion object {
        val TTL: Duration = Duration.ofDays(7)
        const val MAX_LIVE = 10
        private val random = SecureRandom()

        fun hash(raw: String): String =
            MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }

        private fun fullName(user: User?) = user?.let { "${it.firstName} ${it.lastName}".trim() }
    }

    fun create(origin: String?): CreatedInvite {
        val me = securityUtils.getCurrentUser()
        val membership = members.findFirstByUserId(me.id) ?: throw IllegalStateException("No household")
        requireOwner(membership)
        val now = Instant.now(clock)
        if (invites.countLive(membership.household.id, now) >= MAX_LIVE) {
            throw InviteLimitException("This household already has $MAX_LIVE open invitations. Revoke one first.")
        }
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val saved = invites.save(
            HouseholdInvite(
                household = membership.household,
                tokenHash = hash(raw),
                createdBy = me,
                createdAt = now,
                expiresAt = now.plus(TTL)
            )
        )
        val path = "/join?token=$raw"
        val qr = origin?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { QrCode.rows(it + path) }
        return CreatedInvite(saved.id, path, saved.expiresAt, qr)
    }

    @Transactional(readOnly = true)
    fun live(): List<InviteSummary> {
        val membership = members.findFirstByUserId(securityUtils.getCurrentUser().id) ?: return emptyList()
        requireOwner(membership)
        return invites.findLive(membership.household.id, Instant.now(clock))
            .map { InviteSummary(it.id, it.createdAt, it.expiresAt, fullName(it.createdBy)) }
    }

    fun revoke(id: Long) {
        val membership = members.findFirstByUserId(securityUtils.getCurrentUser().id)
            ?: throw IllegalStateException("No household")
        requireOwner(membership)
        val invite = invites.findById(id).orElse(null)
            ?.takeIf { it.household.id == membership.household.id }
            ?: throw NoSuchElementException("Invitation not found: $id")
        if (invite.revokedAt == null) invites.save(invite.apply { revokedAt = Instant.now(clock) })
    }

    @Transactional(readOnly = true)
    fun preview(rawToken: String): InvitePreview {
        val invite = requireLive(rawToken)
        return InvitePreview(invite.household.name, fullName(invite.createdBy), invite.expiresAt)
    }

    /** 404 for a token that never existed, 410 for one that did and no longer works. */
    @Transactional(readOnly = true)
    fun requireLive(rawToken: String): HouseholdInvite {
        val invite = invites.findByTokenHash(hash(rawToken))
            ?: throw NoSuchElementException("This invitation link does not work")
        val now = Instant.now(clock)
        when {
            invite.revokedAt != null -> throw InviteGoneException("This invitation was withdrawn")
            invite.usedAt != null -> throw InviteGoneException("This invitation has already been used")
            !invite.expiresAt.isAfter(now) -> throw InviteGoneException("This invitation has expired")
        }
        return invite
    }

    fun markUsed(invite: HouseholdInvite, user: User) {
        invites.save(invite.apply { usedAt = Instant.now(clock); usedBy = user })
    }
}
```

If `Clock` is not already a bean, check how `AuthFlowService` gets its `clock` and do the same. The test helper `TestIdentity` names people `"<prefix> Test"`, so the expected `invitedBy` is `"anna Test"`.

`repository/HouseholdMemberRepository.kt` — add `fun findAllByHouseholdIdOrderByJoinedAtAsc(householdId: Long): List<HouseholdMember>`.

- [ ] **Step 4: Controllers, /me, exception mapping, security**

`household/HouseholdController.kt`:

```kotlin
package com.homebudget.app.household

import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.security.SecurityUtils
import com.homebudget.app.service.toResponse
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/household")
class HouseholdController(
    private val invites: InviteService,
    private val members: HouseholdMemberRepository,
    private val securityUtils: SecurityUtils
) {
    @GetMapping
    @Transactional(readOnly = true)
    fun household(): HouseholdResponse {
        val household = securityUtils.getCurrentHousehold()
        return HouseholdResponse(
            id = household.id,
            name = household.name,
            baseCurrency = household.baseCurrency?.toResponse(),
            members = members.findAllByHouseholdIdOrderByJoinedAtAsc(household.id).map {
                MemberResponse(it.user.id, it.user.firstName, it.user.lastName, it.user.email, it.role, it.joinedAt)
            }
        )
    }

    @PostMapping("/invites")
    fun createInvite(@RequestBody(required = false) req: CreateInviteRequest?) = invites.create(req?.origin)

    @GetMapping("/invites")
    fun liveInvites() = invites.live()

    @DeleteMapping("/invites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeInvite(@PathVariable id: Long) = invites.revoke(id)
}
```

`household/InviteController.kt`:

```kotlin
package com.homebudget.app.household

import org.springframework.web.bind.annotation.*

/** What someone holding a link may do with it. Preview is public; accepting needs a session. */
@RestController
@RequestMapping("/api/invites")
class InviteController(private val invites: InviteService) {
    @GetMapping("/{token}")
    fun preview(@PathVariable token: String) = invites.preview(token)
}
```

(Task 2 adds `POST /{token}/accept` to this controller.)

`dto/Dtos.kt` — `UserResponse` gains, after `baseCurrency`:

```kotlin
    val householdId: Long,
    val householdName: String,
    val householdRole: HouseholdRole
```

`service/Mappers.kt`: `fun User.toResponse(household: Household, role: HouseholdRole)` filling them. `controller/UserController.kt`: inject `HouseholdMemberRepository`; both endpoints pass `members.findFirstByUserId(user.id)!!.role`.

`controller/GlobalExceptionHandler.kt` — add handlers: `InviteGoneException` → 410 `ErrorResponse(status = 410, error = "Invitation Gone", message = ex.message)`; `InviteLimitException` → 409 `ErrorResponse(status = 409, error = "Too Many Invitations", message = ex.message)`.

`security/SecurityConfig.kt` — before `/api/**` authenticated: `.requestMatchers(HttpMethod.GET, "/api/invites/*").permitAll()` with a comment (the join page reads it before anyone signs in).

`security/AuthRateLimitFilter.kt` — in `shouldNotFilter`, also filter when `path.startsWith("/api/invites/")`:

```kotlin
        if (path.startsWith("/api/invites/")) return false
```

placed before the existing set check, with the comment "Invitation tokens are guessable surface too".

- [ ] **Step 5: Endpoint test**

`src/test/kotlin/com/homebudget/app/household/InviteEndpointsTest.kt` (`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Import(TestIdentity::class)`; authenticate with `SecurityMockMvcRequestPostProcessors.authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))` as `AdminControllerTest` does):
- owner `POST /api/household/invites` with `{"origin":"https://x.example"}` → 200, `$.path` starts with `/join?token=`, `$.qrRows` is an array;
- anonymous `GET /api/invites/{token}` → 200 with `$.householdName`;
- anonymous `GET /api/invites/nope` → 404; after `DELETE /api/household/invites/{id}` the same preview → 410;
- member `POST /api/household/invites` → 403;
- `GET /api/users/me` → `$.householdId`, `$.householdName`, `$.householdRole == "OWNER"`;
- `GET /api/household` → members array of length 1.

- [ ] **Step 6: Run and commit**

Run: `./mvnw -B -Dfrontend.skip=true test` — Expected: PASS.

```bash
git add -A src/main src/test
git commit -m "Let a household's owner create, list and revoke invitations"
```

---

### Task 2: Accepting an invitation, and registering through one

**Files:**
- Create: `service/SyncClock.kt`, `household/HouseholdMergeService.kt`
- Modify: `service/SyncService.kt` (use `SyncClock`), `household/InviteController.kt` (accept), `dto/Dtos.kt` (`RegisterRequest.inviteToken`), `service/RegistrationService.kt`
- Test: `src/test/kotlin/com/homebudget/app/household/JoinHouseholdTest.kt`, `src/test/kotlin/com/homebudget/app/household/RegisterWithInviteTest.kt`

**Interfaces:**
- Consumes: `InviteService.requireLive/markUsed`, `HouseholdRepository.lockById`, `TestIdentity`.
- Produces:
  - `SyncClock.next(vararg householdIds: Long): Instant` — strictly later than every `updated_at` in the given households and than now
  - `HouseholdMergeService.join(user: User, invite: HouseholdInvite): Household`
  - `POST /api/invites/{token}/accept` → `HouseholdResponse`-like `{ householdId, householdName }` (`JoinedResponse(householdId: Long, householdName: String)` in `HouseholdDtos.kt`)
  - `RegisterRequest(…, inviteToken: String? = null)`

- [ ] **Step 1: Extract the sync clock**

`service/SyncClock.kt`:

```kotlin
package com.homebudget.app.service

import com.homebudget.app.repository.SyncCursorRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Stamps strictly later than everything already stored in the given households.
 *
 * Callers hold those households' row locks (HouseholdRepository.lockById), so no other
 * writer can slip a stamp in between reading the latest and writing the next.
 */
@Component
class SyncClock(private val cursors: SyncCursorRepository, private val clock: Clock) {
    fun next(vararg householdIds: Long): Instant {
        val now = Instant.now(clock)
        val latest = householdIds.mapNotNull { cursors.latestUpdatedAt(it) }.maxOrNull() ?: return now
        return if (now.isAfter(latest)) now else latest.plusMillis(1)
    }
}
```

In `SyncService`, replace the private `nextStamp(household)` with `syncClock.next(household.id)` (inject `SyncClock`; drop `cursorRepo`/`clock` constructor parameters if nothing else uses them). Run `-Dtest='SyncServiceTest,ConcurrentPushTest'` — Expected: PASS.

- [ ] **Step 2: Write the failing join tests**

`JoinHouseholdTest.kt` (`@SpringBootTest @ActiveProfiles("test") @Import(TestIdentity::class)`), with a helper that pushes data through `SyncService.push` as the signed-in user (copy the push shapes from `HouseholdIsolationTest`):

1. `joining moves the joiner's books into the family` — owner Anna has account "Family cash" and category EXPENSE "Food"; Boris (own household) has account "Boris card", category EXPENSE "Food" with one transaction on it, category EXPENSE "Hobby", a subscription on "Boris card", currency setting EUR active. Anna creates an invite; Boris signs in and calls `mergeService.join(boris.user, inviteService.requireLive(token))`. Assert, querying repositories by Anna's household:
   - accounts are {"Family cash", "Boris card"};
   - live categories of type EXPENSE named "Food": exactly one (Anna's), and Boris's transaction now points at it;
   - "Hobby" now belongs to Anna's household;
   - the subscription belongs to Anna's household;
   - Boris's old household no longer exists (`householdRepository.existsById(old.id)` is false);
   - Boris's membership is `MEMBER` of Anna's household; the invite has `usedAt` set.
2. `another member's next pull sees the moved rows` — Anna pulls from a cursor taken before the join (call `syncService.pull(Instant.EPOCH, 500)` as Anna before the join, keep `serverTime`), joins Boris, Anna pulls from that cursor: the result contains "Boris card" and Boris's transaction.
3. `an owner whose household has other members cannot join elsewhere` — Boris owns a household with Clara (`identity.joinAs("clara", boris.household)`); joining throws `IllegalArgumentException`.
4. `an invite cannot be used into one's own household` — Anna accepts her own invite → `IllegalArgumentException`.
5. `an invite works once` — after Boris joins, `inviteService.requireLive(token)` throws `InviteGoneException`.
6. `a join and a sync push into the same household finish together` — like `ConcurrentPushTest`: one thread joins Boris into Anna's household while another pushes an account as Anna, released by a latch, 10 rounds with fresh users; every round completes within 10 seconds and Anna's pull contains both the pushed and the moved accounts (no deadlock: both paths lock households lowest id first).

`RegisterWithInviteTest.kt` (own H2 database via `@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:register-invite;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH", "app.instance.registration=closed"])`, `@DirtiesContext`, MockMvc): register the first user (allowed: no users yet), sign them in, create an invite via `InviteService`, then `POST /api/auth/register` with `{"email":"kid@example.com","password":"a-long-enough-password","firstName":"Kid","lastName":"A","inviteToken":"<raw>"}` → 200; the new user is a `MEMBER` of the first user's household and has no household of their own (`householdMemberRepository.findAllByHouseholdId(...)` has 2 rows; total households count is 1). A second registration without a token → 403.

Run: `-Dtest='JoinHouseholdTest,RegisterWithInviteTest'` — Expected: FAIL (compile).

- [ ] **Step 3: Implement the merge**

`household/HouseholdMergeService.kt`:

```kotlin
package com.homebudget.app.household

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.HouseholdMember
import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.domain.User
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.HouseholdRepository
import com.homebudget.app.service.SyncClock
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

/**
 * Moves a user, and every row their household holds, into the household an invitation
 * names. One transaction; both households' sync locks are taken lowest id first, the
 * same lock a sync push takes, so no push and no second join can interleave.
 */
@Service
class HouseholdMergeService(
    private val households: HouseholdRepository,
    private val members: HouseholdMemberRepository,
    private val invites: InviteService,
    private val syncClock: SyncClock,
    private val em: EntityManager
) {
    @Transactional
    fun join(user: User, invite: HouseholdInvite): Household {
        val membership = members.findFirstByUserId(user.id) ?: throw IllegalStateException("No household")
        val from = membership.household
        val to = invite.household
        require(from.id != to.id) { "You are already in this household" }
        require(membership.role != HouseholdRole.OWNER || members.countByHouseholdId(from.id) == 1L) {
            "You own a household that others share. Hand ownership over or remove its members first."
        }

        listOf(from.id, to.id).sorted().forEach { households.lockById(it) }
        // Re-read under the lock: a second click may have used it meanwhile.
        val live = invites.refreshLive(invite.id)
        em.flush()

        val stamp = Timestamp.from(syncClock.next(from.id, to.id))
        mergeCategories(from.id, to.id, stamp)
        listOf("accounts", "subscriptions", "transactions").forEach { table ->
            exec("UPDATE $table SET household_id = :to, updated_at = :stamp WHERE household_id = :from",
                "to" to to.id, "stamp" to stamp, "from" to from.id)
        }
        exec("UPDATE bank_connections SET household_id = :to WHERE household_id = :from", "to" to to.id, "from" to from.id)
        exec(
            """UPDATE household_currency_settings SET household_id = :to, updated_at = :stamp
               WHERE household_id = :from AND currency_id NOT IN
                 (SELECT currency_id FROM household_currency_settings WHERE household_id = :to)""",
            "to" to to.id, "stamp" to stamp, "from" to from.id
        )
        exec("DELETE FROM household_currency_settings WHERE household_id = :from", "from" to from.id)
        exec(
            """UPDATE exchange_rates r SET household_id = :to
               WHERE r.household_id = :from AND NOT EXISTS
                 (SELECT 1 FROM exchange_rates t WHERE t.household_id = :to
                   AND t.base_code = r.base_code AND t.quote_code = r.quote_code)""",
            "to" to to.id, "from" to from.id
        )
        exec("DELETE FROM exchange_rates WHERE household_id = :from", "from" to from.id)
        exec("DELETE FROM household_members WHERE user_id = :user", "user" to user.id)
        exec("DELETE FROM households WHERE id = :from", "from" to from.id)
        em.clear()

        members.save(HouseholdMember(household = households.findById(to.id).orElseThrow(), user = user, role = HouseholdRole.MEMBER))
        invites.markUsed(live, user)
        return households.findById(to.id).orElseThrow()
    }

    /**
     * A joiner's category with the same name (case-insensitive), type and parent as one
     * the family has is the same category: its rows are repointed and it is tombstoned.
     * Parents first, so a child compares against its already-resolved parent.
     */
    private fun mergeCategories(from: Long, to: Long, stamp: Timestamp) {
        @Suppress("UNCHECKED_CAST")
        val rows = em.createNativeQuery(
            "SELECT id, name, type, parent_id FROM categories WHERE household_id = :from AND deleted = false ORDER BY parent_id NULLS FIRST, id"
        ).setParameter("from", from).resultList as List<Array<Any?>>
        val resolved = mutableMapOf<Long, Long>()
        for (row in rows) {
            val id = (row[0] as Number).toLong()
            val name = row[1] as String
            val type = row[2] as String
            val parent = (row[3] as Number?)?.toLong()?.let { resolved[it] ?: it }
            val match = (em.createNativeQuery(
                """SELECT id FROM categories WHERE household_id = :to AND deleted = false
                   AND LOWER(name) = LOWER(:name) AND type = :type
                   AND ((:parent IS NULL AND parent_id IS NULL) OR parent_id = :parent)"""
            ).setParameter("to", to).setParameter("name", name).setParameter("type", type)
                .setParameter("parent", parent).resultList.firstOrNull() as Number?)?.toLong()
            if (match == null) continue
            resolved[id] = match
            exec("UPDATE transactions SET category_id = :t WHERE category_id = :s", "t" to match, "s" to id)
            exec("UPDATE subscriptions SET category_id = :t WHERE category_id = :s", "t" to match, "s" to id)
            exec("UPDATE categories SET parent_id = :t WHERE parent_id = :s", "t" to match, "s" to id)
            exec(
                """INSERT INTO category_mcc_codes (category_id, mcc_code)
                   SELECT :t, mcc_code FROM category_mcc_codes WHERE category_id = :s
                   AND mcc_code NOT IN (SELECT mcc_code FROM category_mcc_codes WHERE category_id = :t)""",
                "t" to match, "s" to id
            )
            exec("UPDATE categories SET deleted = true WHERE id = :s", "s" to id)
        }
        exec("UPDATE categories SET household_id = :to, updated_at = :stamp WHERE household_id = :from",
            "to" to to, "stamp" to stamp, "from" to from)
    }

    private fun exec(sql: String, vararg params: Pair<String, Any?>) {
        val q = em.createNativeQuery(sql)
        params.forEach { (k, v) -> q.setParameter(k, v) }
        q.executeUpdate()
    }
}
```

The merge has the invite entity, not the raw token, so it re-checks liveness by re-reading the entity under the lock. Add to `InviteService`:

```kotlin
    /** The same invite re-read inside the caller's transaction, refused if it stopped being live. */
    fun refreshLive(id: Long): HouseholdInvite {
        val invite = invites.findById(id).orElseThrow { NoSuchElementException("Invitation not found") }
        if (!invite.isLive(Instant.now(clock))) throw InviteGoneException("This invitation has already been used")
        return invite
    }
```

Check H2 accepts `ORDER BY parent_id NULLS FIRST` and the `UPDATE exchange_rates r … NOT EXISTS` alias form in PostgreSQL mode; if H2 rejects the alias, rewrite with a `(base_code, quote_code) NOT IN (SELECT …)` row-value comparison or a correlated subquery without alias, keeping Postgres-valid SQL.

`household/InviteController.kt` — add (inject `HouseholdMergeService`, `SecurityUtils`):

```kotlin
    @PostMapping("/{token}/accept")
    fun accept(@PathVariable token: String): JoinedResponse {
        val household = merge.join(securityUtils.getCurrentUser(), invites.requireLive(token))
        return JoinedResponse(household.id, household.name)
    }
```

and `data class JoinedResponse(val householdId: Long, val householdName: String)` in `HouseholdDtos.kt`. `SecurityConfig` already requires a session for `POST /api/invites/*/accept` (only GET is permitted).

- [ ] **Step 4: Register through an invitation**

`dto/Dtos.kt` — `RegisterRequest` gains `val inviteToken: String? = null`.

`service/RegistrationService.register` — inject `InviteService` and `HouseholdMemberRepository`; at the top:

```kotlin
        val invite = req.inviteToken?.let { invites.requireLive(it) }
        if (invite == null && !registrationOpen()) throw RegistrationClosedException()
```

and after the user is saved:

```kotlin
        if (invite != null) {
            // Joining a family: no household of their own, no default categories.
            members.save(HouseholdMember(household = invite.household, user = user, role = HouseholdRole.MEMBER))
            invites.markUsed(invite, user)
        } else {
            val baseCurrency = req.baseCurrencyId?.let { currencies.findById(it).orElse(null) }
                ?: currencies.findByCode("USD")
            categories.seedDefaultCategories(households.createFor(user, baseCurrency))
        }
```

(keep the first-user `ADMIN` and mail-verification logic as is).

- [ ] **Step 5: Run and commit**

Run: `./mvnw -B -Dfrontend.skip=true test` — Expected: PASS.

```bash
git add -A src/main src/test
git commit -m "Join a household by invitation, bringing your books along"
```

---

### Task 3: Leaving, removing members, ownership, renaming

**Files:**
- Create: `household/MembershipService.kt`
- Modify: `household/HouseholdController.kt`, `service/UserService.kt` (owner-only base currency; owner handoff on delete), `controller/UserController.kt`
- Test: `src/test/kotlin/com/homebudget/app/household/MembershipTest.kt`; add a case to `src/test/kotlin/com/homebudget/app/HouseholdIsolationTest.kt`

**Interfaces:**
- Consumes: `HouseholdService.createFor`, `CategoryService.seedDefaultCategories(household)`, `requireOwner`.
- Produces: `MembershipService.leave()`, `remove(userId: Long)`, `transferOwnership(userId: Long)`, `rename(name: String): Household`; endpoints `POST /api/household/leave`, `DELETE /api/household/members/{userId}`, `POST /api/household/owner` body `{ "userId": … }` (`TransferOwnershipRequest`), `PUT /api/household` body `{ "name": … }` (`RenameHouseholdRequest`); `UserService.updateBaseCurrency` refuses members.

- [ ] **Step 1: Write the failing tests**

`MembershipTest.kt` (`@SpringBootTest @ActiveProfiles("test") @Import(TestIdentity::class)`):
- `a member who leaves gets an empty household of their own and their transactions stay` — Anna owns, Boris joined (`identity.joinAs`) and wrote a transaction through `SyncService.push` as Boris; Boris `leave()`s → Boris's membership is `OWNER` of a new household with the default categories and Anna's household's base currency; the transaction is still in Anna's household with `createdBy == boris`.
- `an owner cannot leave while others remain` → `IllegalArgumentException`.
- `the owner removes a member` — `remove(boris.id)` as Anna → Boris has a new own household; as Boris, `remove(anna.id)` → `AccessDeniedException`; Anna removing herself → `IllegalArgumentException`.
- `ownership moves` — Anna `transferOwnership(boris.id)` → Boris `OWNER`, Anna `MEMBER`; Anna can now `leave()`.
- `only the owner renames and changes the base currency` — Boris `rename("x")` → `AccessDeniedException`; Boris `userService.updateBaseCurrency(eurId)` → `AccessDeniedException`; Anna renames → `householdRepository` shows the new name.
- `deleting an owner hands the household to the longest-standing member` — `userService.deleteUser(anna.user)` with Boris and Clara (joined in that order) → Boris is `OWNER`, the household and its accounts still exist.

`HouseholdIsolationTest` — add `a member who leaves no longer sees the family's books`: partner joins Alice's household, then `membershipService.leave()` as partner; `accountService.findAll()` as partner is empty and `syncService.pull(Instant.EPOCH, 500).changes.accounts` is empty.

Run: `-Dtest='MembershipTest,HouseholdIsolationTest'` — Expected: FAIL.

- [ ] **Step 2: Implement**

`household/MembershipService.kt`:

```kotlin
package com.homebudget.app.household

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.HouseholdMember
import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.domain.User
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.HouseholdRepository
import com.homebudget.app.security.SecurityUtils
import com.homebudget.app.service.CategoryService
import com.homebudget.app.service.HouseholdService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class MembershipService(
    private val members: HouseholdMemberRepository,
    private val households: HouseholdRepository,
    private val householdService: HouseholdService,
    private val categories: CategoryService,
    private val securityUtils: SecurityUtils
) {
    private fun mine(): HouseholdMember =
        members.findFirstByUserId(securityUtils.getCurrentUser().id) ?: throw IllegalStateException("No household")

    fun leave() {
        val me = mine()
        val count = members.countByHouseholdId(me.household.id)
        require(count > 1) { "You are the only member of this household" }
        require(me.role != HouseholdRole.OWNER) { "Hand ownership to another member before leaving" }
        detach(me)
    }

    fun remove(userId: Long) {
        val me = mine()
        requireOwner(me)
        require(userId != me.user.id) { "Use leave, or hand ownership over first" }
        val target = members.findAllByHouseholdId(me.household.id).firstOrNull { it.user.id == userId }
            ?: throw NoSuchElementException("Not a member of this household: $userId")
        detach(target)
    }

    fun transferOwnership(userId: Long) {
        val me = mine()
        requireOwner(me)
        val target = members.findAllByHouseholdId(me.household.id).firstOrNull { it.user.id == userId }
            ?: throw NoSuchElementException("Not a member of this household: $userId")
        require(target.user.id != me.user.id) { "You already own this household" }
        members.save(target.apply { role = HouseholdRole.OWNER })
        members.save(me.apply { role = HouseholdRole.MEMBER })
    }

    fun rename(name: String): Household {
        val me = mine()
        requireOwner(me)
        require(name.isNotBlank()) { "A household needs a name" }
        val household = households.findById(me.household.id).orElseThrow()
        household.name = name.trim()
        household.updatedAt = Instant.now()
        return households.save(household)
    }

    /**
     * Gives [membership]'s user a household of their own, as at registration. Their
     * transactions stay where they were written, with their name as the author.
     */
    private fun detach(membership: HouseholdMember) {
        val user: User = membership.user
        val baseCurrency = membership.household.baseCurrency
        members.delete(membership)
        members.flush()
        categories.seedDefaultCategories(householdService.createFor(user, baseCurrency))
    }
}
```

`HouseholdController` — inject `MembershipService`; add the four endpoints (`leave` and `remove` → 204, `owner` → 204, `PUT` → `HouseholdResponse` via the existing `household()` builder after renaming). DTOs `TransferOwnershipRequest(val userId: Long)`, `RenameHouseholdRequest(val name: String)` in `HouseholdDtos.kt`.

`UserService.updateBaseCurrency` — first line `requireOwner(memberRepository.findFirstByUserId(getCurrentUser().id)!!)`.

`UserService.deleteUser` — before the existing "last member" check:

```kotlin
        if (membership != null && membership.role == HouseholdRole.OWNER &&
            memberRepository.countByHouseholdId(membership.household.id) > 1
        ) {
            // Never leave a household without an owner: the longest-standing member takes over.
            memberRepository.findAllByHouseholdIdOrderByJoinedAtAsc(membership.household.id)
                .first { it.user.id != user.id }
                .let { memberRepository.save(it.apply { role = HouseholdRole.OWNER }) }
        }
```

`HouseholdMember.role` must be a `var` (it is, per the baseline).

- [ ] **Step 3: Run and commit**

Run: `./mvnw -B -Dfrontend.skip=true test` — Expected: PASS.

```bash
git add -A src/main src/test
git commit -m "Let members leave, and the owner remove, promote and rename"
```

---

### Task 4: The author on the wire, and filtering by it

**Files:**
- Modify: `dto/SyncDtos.kt` (`SyncTransaction.createdByUserId`), `service/SyncService.kt` (`toSync`), `dto/Dtos.kt` (`TransactionResponse.createdByUserId`), `service/Mappers.kt`, `controller/TransactionController.kt`, `service/TransactionService.kt`, `repository/TransactionRepository.kt`
- Test: extend `src/test/kotlin/com/homebudget/app/service/SyncServiceTest.kt` and add `src/test/kotlin/com/homebudget/app/controller/TransactionAuthorFilterTest.kt`

**Interfaces:**
- Produces: `SyncTransaction.createdByUserId: Long? = null` (filled on pull, ignored on push); `TransactionResponse.createdByUserId: Long?`; `GET /api/transactions?createdBy=<userId>`; `TransactionService.findFiltered(…, createdBy: Long?, …)`.

- [ ] **Step 1: Failing tests**
- `SyncServiceTest`: `a pulled transaction names its author` — push one as alice, pull: `changes.transactions.single().createdByUserId == alice.user.id`.
- `TransactionAuthorFilterTest` (MockMvc, two members of one household each pushing one transaction): `GET /api/transactions?createdBy=<boris>` returns only Boris's, and each item carries `createdByUserId`.

- [ ] **Step 2: Implement**
- `SyncTransaction`: add `val createdByUserId: Long? = null` last; `toSync` sets `createdByUserId = createdBy?.id`; the push path ignores it.
- `TransactionResponse`: add `val createdByUserId: Long?`; mapper sets `createdBy?.id`.
- Repository JPQL `findFiltered`: add `AND (:createdBy IS NULL OR t.createdBy.id = :createdBy)` and the parameter `createdBy: Long?`; service and controller pass `@RequestParam createdBy: Long?`.

- [ ] **Step 3: Run and commit**

Run: `./mvnw -B -Dfrontend.skip=true test` — Expected: PASS.

```bash
git add -A src/main src/test
git commit -m "Carry each transaction's author to the clients and filter by it"
```

---

# Phase B — Web (`src/main/frontend/src`)

### Task 5: The /join page

**Files:**
- Create: `landing/Join.tsx`, `landing/Join.test.tsx`
- Modify: `App.tsx` (route `/join`), `landing/session.ts` (`nextPath` accepts `/join?token=…`), `landing/session.test.ts`, `landing/authApi.ts` (`invitePreview`, `register` accepts `inviteToken`), `app/api/endpoints.ts` (`acceptInvite`), `landing/Register.tsx` (pass through `?invite=`), locales `en/ru/uk.ts`

**Interfaces:**
- Consumes: `GET /api/invites/{token}` → `{householdName, invitedBy, expiresAt}`; `POST /api/invites/{token}/accept` (session) → `{householdId, householdName}`; register body `inviteToken`.
- Produces: route `/join`; `authApi.invitePreview(token): Promise<{householdName: string; invitedBy?: string|null; expiresAt: string}>`; `endpoints.acceptInvite(token)`; `nextPath` allows `/join?token=<[A-Za-z0-9_-]+>`.

- [ ] **Step 1: Tests first**

`landing/session.test.ts` — add: `nextPath('?next=%2Fjoin%3Ftoken%3Dabc_DEF-1')` returns `/join?token=abc_DEF-1`; `nextPath('?next=%2Fjoin%3Ftoken%3Da%2F..')` returns the dashboard.

`landing/Join.test.tsx` — render `<MemoryRouter initialEntries={['/join?token=t1']}>` with `I18nProvider` and `ThemeProvider` (AuthShell needs it), routes `/join` → `<Join/>`, `/app` → `<div>app</div>`, `/register` → `<div>register</div>`, `/login` → `<div>login</div>`; stub `fetch` by URL:
1. signed out, preview 200 → shows `householdName`, a "create an account" link to `/register?invite=t1` and a "sign in" link to `/login?next=%2Fjoin%3Ftoken%3Dt1`;
2. signed in (`session.save({token:'a',refreshToken:'r',email:'e@x'})`), preview 200, accept 200 → clicking the join button calls `POST /api/invites/t1/accept` and lands on `app`;
3. preview 410 `{message:'This invitation has expired'}` → shows that message and no join button;
4. preview 404 → shows `join.notFound`.

Run: `npm test -- src/landing` — Expected: FAIL.

- [ ] **Step 2: Implement**
- `session.ts` `nextPath`: before the existing `/app` check, accept `next` matching `/^\/join\?token=[A-Za-z0-9_-]{10,100}$/`.
- `authApi.ts`: `invitePreview: (token: string) => request<InvitePreview>(`/invites/${encodeURIComponent(token)}`)`; `register` already sends its body as-is (pass `inviteToken`).
- `endpoints.ts`: `acceptInvite: (token: string) => api<{ householdId: number; householdName: string }>(`/invites/${encodeURIComponent(token)}/accept`, { method: 'POST' })`.
- `Join.tsx` (default export): reads `token` from `useSearchParams`; loads the preview with `useEffect`; signed-in detection via `session.get()` from `app/api/session`; renders `AuthShell` with `title={t('join.title', { name: preview.householdName })}` and `subtitle` naming the inviter; signed in → a warning paragraph `t('join.moveWarning')` and a primary button `t('join.accept')` that calls `endpoints.acceptInvite`, then `queryClient.clear()` (import from `app/api/queryClient`) and `navigate('/app', { replace: true })`; signed out → links `/register?invite=<token>` (`t('join.register')`) and `/login?next=<encoded /join?token=…>` (`t('join.signIn')`); errors: 410 shows the server message, 404 shows `t('join.notFound')`.
- `Register.tsx`: read `invite` from the query string; when present, send `inviteToken` with the registration, skip the "registration closed" block, set the subtitle to `t('join.registerSubtitle')`, and after registering + signing in navigate to `/app`.
- `App.tsx`: `<Route path="/join" element={<Join />} />`.
- Locale keys (dotted, all three files): `join.title` ("Join {{name}}"), `join.invitedBy` ("{{name}} invited you"), `join.moveWarning` ("Your accounts, categories and history will move into this household."), `join.accept` ("Join"), `join.register` ("Create an account"), `join.signIn` ("I already have an account"), `join.notFound` ("This invitation link does not work."), `join.registerSubtitle` ("Create your account to join the household."). Translate for ru and uk.

- [ ] **Step 3: Run and commit**

Run: `npm run typecheck && npm test` — Expected: PASS.

```bash
git add -A src
git commit -m "Add the page an invitation link opens"
```

---

### Task 6: The Family page

**Files:**
- Create: `app/features/household/HouseholdPage.tsx`, `app/features/household/HouseholdPage.test.tsx`
- Modify: `app/api/types.ts` (`HouseholdDto`, `MemberDto`, `InviteDto`, `CreatedInviteDto`, `UserDto.householdId/householdName/householdRole`), `app/api/endpoints.ts`, `app/api/queryClient.ts` (`qk.household`, `qk.invites`), `app/api/hooks.ts` (`useHousehold`), `app/lib/routes.ts` (`household`), `app/AppRoot.tsx`, `app/shell/sections.ts` (Family entry, `Users` icon), `app.{en,ru,uk}.ts`

**Interfaces:**
- Consumes: Task 1 and Task 3 endpoints.
- Produces: `AppRoutes.household = '/app/household'`; endpoints `household()`, `invites()`, `createInvite(origin)`, `revokeInvite(id)`, `renameHousehold(name)`, `removeMember(userId)`, `transferOwnership(userId)`, `leaveHousehold()`; `useHousehold()` (`useQuery({ queryKey: qk.household, queryFn: endpoints.household })`).

- [ ] **Step 1: Tests first**

`HouseholdPage.test.tsx` (render inside `QueryClientProvider` + `MemoryRouter` + `I18nProvider`; stub `fetch` by URL and method):
1. as owner (`/api/users/me` → `householdRole: 'OWNER'`): lists members; "Invite" posts `/api/household/invites` with body `{"origin": window.location.origin}` and shows the returned link `origin + path` in a read-only input; a live invite row has a revoke button that sends `DELETE /api/household/invites/<id>`;
2. as member: no Invite button, no remove buttons; "Leave" sends `POST /api/household/leave` after the confirm dialog.

Run: `npm test -- src/app/features/household` — Expected: FAIL.

- [ ] **Step 2: Implement**

Follow `AdminPage.tsx`'s structure (Page, Card, ConfirmDialog, ErrorBanner, mutations invalidating `qk.household`/`qk.invites`). Sections: members list (name, email, role badge, joined date; owner sees "Make owner" and "Remove" per other member, each behind `ConfirmDialog`); for the owner a name field with save, "Invite" with the link and the QR from `qrRows` (render rows of `'1'`/`'0'` as a grid of black/white squares, as `BanksPage.tsx` does — reuse its QR component if it is exported, otherwise move it to `app/components/QrRows.tsx` and import it in both); live invites with revoke; "Leave household" for members (owner sees an explanation that ownership must be handed over first when others remain). After leave: `queryClient.clear()` and navigate to `/app`.

App keys (snake_case, all three locale files): `household_title` ("Family"), `household_members`, `household_owner`, `household_member`, `household_invite`, `household_invite_ready` ("Send this link — it works once, for 7 days"), `household_invites_open`, `household_revoke`, `household_rename`, `household_make_owner`, `household_make_owner_confirm`, `household_remove`, `household_remove_confirm` ("{{name}} will get a household of their own. What they wrote stays here."), `household_leave`, `household_leave_confirm`, `household_leave_owner_hint`, `nav_household` ("Family").

- [ ] **Step 3: Run and commit**

Run: `npm run typecheck && npm test` — Expected: PASS.

```bash
git add -A src
git commit -m "Add the Family page: members, invitations and ownership"
```

---

### Task 7: The author on transactions, and the Who filter

**Files:**
- Create: `app/features/household/initials.ts` (+ test `initials.test.ts`)
- Modify: `app/api/types.ts` (`RestTransaction.createdByUserId?: number | null`), `app/api/endpoints.ts` (`transactions` query gains `createdBy?: number`), `app/features/transactions/ledger.logic.ts` (`LedgerFilter.createdBy?: number`, passed to `fetchLedger`), `useLedger.ts` (URL key `who`), `TransactionsPage.tsx` (Who select when members > 1), `TransactionRow.tsx` (initials badge prop), app locales (`filter_who`)

**Interfaces:**
- Consumes: Task 4 (`createdByUserId`, `?createdBy=`), Task 6 `useHousehold()`.
- Produces: `initialsOf(firstName?: string, lastName?: string): string` (first letters, uppercase, max 2); `TransactionRow` prop `author?: string`.

- [ ] **Step 1: Tests first**
- `initials.test.ts`: `initialsOf('Anna','Ivanova') === 'AI'`; `initialsOf('anna', '') === 'A'`; `initialsOf(undefined, undefined) === ''`.
- Extend `ledger.logic.test.ts`: `fetchLedger` passes `createdBy` through to the `/transactions` query.
- A `TransactionsPage` test (or extend an existing one): with `/api/household` returning two members, the Who select is present and rows show the author's initials; with one member, neither appears.

- [ ] **Step 2: Implement** as listed in **Files**; the badge is a small rounded `span` before the title, `title` attribute = full name; the filter state's `createdBy` round-trips through the URL key `who` and counts in the `filtered` flag.

- [ ] **Step 3: Run and commit**

Run: `npm run typecheck && npm test` — Expected: PASS.

```bash
git add -A src
git commit -m "Show who wrote each transaction and filter by them"
```

---

# Phase C — Android (`client/composeApp/src`)

### Task 8: The author in the local database and on transaction rows

**Files:**
- Modify: `androidMain/.../core/database/entity/TransactionEntity.kt` (`createdByUserId: Long? = null`, column `created_by_user_id`), `androidMain/.../core/database/Migrations.kt` (`MIGRATION_4_5`, registry), `androidMain/.../core/database/AppDatabase.kt` (version 5), `commonMain/.../core/network/ApiDtos.kt` (`SyncTransactionDto.createdByUserId: Long? = null`; `UserDto.householdId: Long? = null`, `householdName: String? = null`, `householdRole: String? = null`; `HouseholdDto`, `MemberDto`), `androidMain/.../core/sync/SyncMappers.kt` (pull mapping), `commonMain/.../core/domain/model/Transaction.kt` (`createdByUserId: Long? = null`), `androidMain/.../core/data/repository/RoomTransactionRepository.kt` (map both ways; an edit keeps the stored author), `commonMain/.../core/network/HomeBudgetApi.kt` (`household(): HouseholdDto`), new `commonMain/.../core/household/HouseholdMembers.kt`, `TransactionsViewModel.kt`/`TransactionsScreen.kt` (initials), DI module, schema `client/composeApp/schemas/<db FQCN>/5.json` (generated by the build — commit it)
- Test: `androidInstrumentedTest/.../core/database/MigrationTest.kt` (`migrate4To5_addsTheAuthorColumn`), `commonTest/.../core/household/HouseholdMembersTest.kt`

**Interfaces:**
- Produces: `MIGRATION_4_5`; `HouseholdMembers(api: HomeBudgetApi)` with `val members: StateFlow<List<MemberDto>>`, `suspend fun refresh()`, `fun initialsFor(userId: Long?): String?` (null when ≤ 1 member or unknown); `TransactionRow` gains `author: String?`.

- [ ] **Step 1: Tests first**
- `HouseholdMembersTest` (MockEngine answering `/api/household` with two members): after `refresh()`, `initialsFor(<id>)` returns the member's initials; with one member it returns null for everyone.
- `MigrationTest.migrate4To5_addsTheAuthorColumn`: create v4 with one transaction row, run `MIGRATION_4_5`, validate; the row's `created_by_user_id` is NULL. (Instrumented: it compiles in `assembleDebug`; run on a device only if one is attached.)

- [ ] **Step 2: Implement**
- `MIGRATION_4_5`: `db.execSQL("ALTER TABLE transactions ADD COLUMN created_by_user_id INTEGER")`; add to `ALL_MIGRATIONS`; `AppDatabase` version 5.
- Pull mapping sets `createdByUserId = createdByUserId`; push mapping does not send it (the server ignores it anyway).
- `RoomTransactionRepository`: `toTransaction()` copies it; on update, read the existing entity's value and keep it (like `createdAt`).
- `HouseholdMembers` is a Koin `single`; `TransactionsViewModel` collects `members` and exposes `authorInitials: Map<Long, String>`; `TransactionRow` renders the initials in a small rounded `Surface` before the title when non-null; refresh members when the transactions screen opens (ignore network failures).

- [ ] **Step 3: Build, test, commit**

Run: `./gradlew testDebugUnitTest assembleDebug --no-daemon` — Expected: BUILD SUCCESSFUL; `client/composeApp/schemas/.../5.json` exists.

```bash
git add -A client
git commit -m "Keep each transaction's author on the phone and show it"
```

---

### Task 9: The Family screen, sharing an invitation, and following a household change

**Files:**
- Create: `commonMain/.../feature/family/FamilyScreen.kt`, `commonMain/.../feature/family/FamilyViewModel.kt`, `commonMain/.../core/ui/TextSharer.kt` (`@Composable expect fun rememberTextSharer(): (String) -> Unit`), `androidMain/.../core/ui/TextSharer.android.kt`
- Modify: `commonMain/.../core/network/HomeBudgetApi.kt` (`createInvite(): CreatedInviteDto`, `leaveHousehold()`), `ApiDtos.kt` (`CreatedInviteDto(id: Long, path: String, expiresAt: String)`), `commonMain/.../core/data/UserPreferences.kt` (`householdId: Flow<Long?>`, `setHouseholdId`, `householdNotice: Flow<String?>`, `setHouseholdNotice`; both keys are removed by `clearAccountScoped()`, and the sync engine writes them again right after a wipe), `androidMain/.../core/sync/SyncEngine.kt` (household check), `commonMain/.../feature/more/MoreScreen.kt` (`onOpenFamily`), `navigation/Routes.kt` (`FAMILY = "family"`), `navigation/PersonalMoneyNavHost.kt` (route + registeredRoutes + MoreScreen arg), `core/di/SharedViewModels.kt`, `androidMain/.../core/di/Modules.kt` (SyncEngine gets `LocalDataWiper`), dashboard (one-time notice), strings ×3
- Test: `commonTest/.../feature/family/FamilyViewModelTest.kt`, `androidUnitTest/.../core/sync/HouseholdChangeTest.kt`

**Interfaces:**
- Consumes: `HouseholdMembers`, `TokenStore.serverUrl`, `LocalDataWiper`, `/api/users/me` household fields.
- Produces: `FamilyViewModel(api, members: HouseholdMembers, tokens: TokenStore)` with state `{ members, isOwner, inviteLink: String?, busy, error }`, `invite()`, `leave()`; `rememberTextSharer()`.

- [ ] **Step 1: Tests first**
- `FamilyViewModelTest` (MockEngine): owner → `invite()` posts `/api/household/invites` and sets `inviteLink` to `serverUrl.trimEnd('/') + path`; member → `isOwner` false; `leave()` posts `/api/household/leave`.
- `HouseholdChangeTest` (androidUnitTest with mockk for DAOs/api, or a fake): stored household 1, `/me` says 2 → the wiper runs once, the cursor is blank before the pull, `householdNotice` is set to the new household's name, stored id becomes 2; stored household null → no wipe, id stored; same id → no wipe.

- [ ] **Step 2: Implement**
- `SyncEngine.syncNow()`: before `pull()`, call `api.me()` (wrapped so a network failure skips the check), compare `householdId` with `prefs.householdId.first()`:
  - null stored → store it;
  - different → `wiper.wipe()` (this also clears the cursor via `clearAccountScoped()`), then `prefs.setHouseholdId(new)`, `prefs.setHouseholdNotice(me.householdName)`. Unsynced local edits are dropped: they belonged to the previous household; document this in a KDoc line.
- `TextSharer.android.kt`: `Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)` wrapped in `Intent.createChooser(…, null)` with `FLAG_ACTIVITY_NEW_TASK` when the context is not an Activity.
- `FamilyScreen`: members list with role; owner sees "Invite" (calls `invite()`, then shares `inviteLink` with `rememberTextSharer()`); members see "Leave" behind the existing confirm dialog; after `leave()`, call `syncTrigger.requestSync()` so the household change is picked up.
- Dashboard: when `householdNotice` is non-null, show a snackbar/banner once with `family_joined_notice` and clear it.
- `MoreScreen`: `MoreItem(Icons.Filled.Groups, Res.string.family_title, onOpenFamily)`; NavHost: `composable(Routes.FAMILY) { FamilyScreen(onBack = sectionBack) }`, add `Routes.FAMILY` to `registeredRoutes`.
- Strings (en/ru/uk): `family_title`, `family_owner`, `family_member`, `family_invite`, `family_invite_share_text` ("Join our household in PersonalMoney: %1$s"), `family_leave`, `family_leave_confirm`, `family_leave_owner_hint`, `family_joined_notice` ("You are now in the household “%1$s”").

- [ ] **Step 3: Build, test, commit**

Run: `./gradlew testDebugUnitTest assembleDebug --no-daemon` — Expected: BUILD SUCCESSFUL.

```bash
git add -A client
git commit -m "Add the Family screen and follow a household change on the phone"
```

---

## After the last task

Run every suite (server, web, site, Android) once more, then superpowers:finishing-a-development-branch.
