# Open-source baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn PersonalMoney into an AGPL self-hosted app: no monetisation, Postgres with households built in, installable with `docker compose up`, administrable by its owner, with the Android app pointing at any server and the landing page moved to its own site.

**Architecture:** The Spring Boot server keeps its shape (controller → service → repository) but every ledger row moves from `user_id` to `household_id`, resolved from the authenticated principal. A new unauthenticated `/api/instance` endpoint describes what the instance offers, and both clients (React web, Compose Android) adapt to it. Packaging is one multi-arch image plus a two-service compose file; the marketing site becomes a separate static Vite project.

**Tech Stack:** Kotlin 2.1 / Spring Boot 3.4 / Hibernate 6 / Flyway / Postgres 17 (H2 in PostgreSQL mode for fast tests, Testcontainers for real Postgres) · React 19 / Vite / TanStack Query / Vitest · Compose Multiplatform / Koin / Ktor / Room KMP · GitHub Actions / GHCR.

**Spec:** [docs/superpowers/specs/2026-10-15-open-source-baseline-design.md](../specs/2026-10-15-open-source-baseline-design.md) (and the [pivot overview](../specs/2026-10-15-self-hosted-pivot-overview.md)).

## Global Constraints

- Licence: AGPL-3.0.
- Database: Postgres only in production (`postgres:17` in compose). MySQL/MariaDB drivers and `flyway-mysql` are removed.
- Migrations: `V1__baseline.sql` (Postgres DDL) and `V2__seed_currency_catalog.sql` only. V1 may be edited freely on this branch until the first `v*` tag; after that, only new migrations.
- Image: `ghcr.io/htearih/personalmoney`, tags `latest` and `vX.Y.Z`, platforms `linux/amd64` and `linux/arm64`, non-root, listens on `8080`, state in `/data`, `HEALTHCHECK` on `/actuator/health`.
- Required env for a self-hoster: `DB_PASSWORD` only. `JWT_SECRET` and `CRYPTO_SECRET` are generated into `/data/secrets` when absent; an env value always wins.
- `REGISTRATION` = `open` | `closed`; unset means open while there are no users, closed afterwards.
- `/api/instance` JSON fields: `name`, `version`, `apiVersion` (integer, currently `1`), `setupRequired`, `registrationOpen`, `mailEnabled`, `bankProviders`.
- Android application id stays `com.personalmoney`; distribution is a signed APK on GitHub Releases.
- UI strings exist in en, ru and uk for every new key (web: `src/i18n/locales/{en,ru,uk}.ts` for dotted keys, `app.{en,ru,uk}.ts` for snake_case keys; Android: `composeResources/values{,-ru,-uk}/strings.xml`).
- Do not make the GitHub repository public, push tags, or deploy anything; those are the owner's steps.

## Deviations from the spec (decided while planning)

Record these in the spec in Task 1's commit:

1. `transactions.created_by_user_id` is **nullable**: subscription charges are written by the scheduler with no user, and deleting a user must not delete household history (`ON DELETE SET NULL`).
2. `household_members` has a surrogate `id` plus `UNIQUE (household_id, user_id)` instead of a composite primary key (simpler JPA mapping, same guarantee).
3. `bank_connections.connected_by_user_id` is nullable with `ON DELETE SET NULL`, for the same reason as 1.
4. New column `users.last_sign_in_at` (shown in the admin users table) and new table `instance_settings` (stores the runtime registration toggle).
5. Android stores the server address next to the tokens in `TokenStore` (EncryptedSharedPreferences, synchronous) rather than DataStore, because Ktor's `defaultRequest` needs it synchronously on every request.

## Commands used throughout

- Server tests: `./mvnw -B -Dfrontend.skip=true test` (from the repository root; on Windows `mvnw.cmd`). A single class: add `-Dtest=ClassName`.
- Server compile only: `./mvnw -B -q -Dfrontend.skip=true test-compile`.
- Web: `cd src/main/frontend && npm test` and `npm run typecheck`.
- Android (from `client/`, PowerShell): `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; ./gradlew.bat testDebugUnitTest assembleDebug --no-daemon`.
- Testcontainers tests skip themselves when Docker is not running (`disabledWithoutDocker = true`); CI runs them.

## File map

Server (`src/main/kotlin/com/homebudget/app/`):

| Path | Change |
| --- | --- |
| `billing/`, `domain/PlayPurchase.kt`, `repository/PlayPurchaseRepository.kt`, `service/PlanLimitException.kt` | delete |
| `domain/Household.kt`, `domain/HouseholdMember.kt`, `domain/InstanceSetting.kt` | create |
| `repository/HouseholdRepository.kt`, `repository/HouseholdMemberRepository.kt`, `repository/InstanceSettingRepository.kt` | create |
| `service/HouseholdService.kt`, `service/RegistrationService.kt` | create |
| `instance/InstanceController.kt`, `instance/InstanceInfo.kt` | create |
| `admin/AdminController.kt`, `admin/AdminDtos.kt`, `admin/ResetPasswordCommand.kt` | create |
| `config/SecretsEnvironmentPostProcessor.kt` | create |
| `domain/*` ledger entities, all ledger repositories, `security/*`, all services | modify (user → household) |
| `src/main/resources/META-INF/spring.factories` | create |
| `src/main/resources/db/migration/` | replace with `V1__baseline.sql`, `V2__seed_currency_catalog.sql` |

Web (`src/main/frontend/src/`): delete `landing/Landing.tsx`, `app/features/premium/`; create `landing/Setup.tsx`, `landing/instance.ts`, `app/features/admin/AdminPage.tsx`; modify `App.tsx`, `landing/Login.tsx`, `landing/Register.tsx`, `app/AppRoot.tsx`, `app/lib/routes.ts`, `app/shell/AccountMenu.tsx`, `app/components/ErrorBanner.tsx`, `app/api/{http,endpoints,types,queryClient}.ts`, `app/features/settings/SettingsPage.tsx`, locale files.

Android (`client/composeApp/src/`): delete `androidMain/.../ads/`, `androidMain/.../core/billing/`, `commonMain/.../ads/`, `commonMain/.../core/billing/`, `commonMain/.../feature/premium/`, `commonTest/.../core/billing/`, `androidDebug/`; create `androidMain/res/xml/network_security_config.xml`, `commonMain/.../core/auth/ServerAddress.kt`; modify DI, auth, network, navigation, preferences.

Repository root: `Dockerfile`, `docker-compose.yml`, `.env.example`, `docker/personalmoney` (CLI wrapper), `deploy/` (landing site only), `site/` (new), `LICENSE`, `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `.github/workflows/{ci,release,site}.yml` (replacing `deploy.yml`).

---

# Phase A — Server

### Task 1: Remove billing, premium and the account cap

**Files:**
- Delete: `src/main/kotlin/com/homebudget/app/billing/` (whole directory), `src/main/kotlin/com/homebudget/app/domain/PlayPurchase.kt`, `src/main/kotlin/com/homebudget/app/repository/PlayPurchaseRepository.kt`, `src/main/kotlin/com/homebudget/app/service/PlanLimitException.kt`, `src/test/kotlin/com/homebudget/app/billing/` (whole directory), `src/test/kotlin/com/homebudget/app/service/PlanLimitTest.kt`, `docs/PREMIUM.md`
- Modify: `src/main/kotlin/com/homebudget/app/domain/User.kt`, `dto/Dtos.kt:315-333`, `service/Mappers.kt:71-83`, `service/AccountService.kt`, `service/SyncService.kt:213-221`, `importer/homemoney/HomeMoneyImportService.kt:100-125,165-171`, `repository/AccountRepository.kt:20-26`, `controller/GlobalExceptionHandler.kt:50-80`, `src/main/resources/application.yml` (the `app.billing` block), `docs/superpowers/specs/2026-10-15-open-source-baseline-design.md` (record the deviations)
- Test: `src/test/kotlin/com/homebudget/app/service/NoAccountCapTest.kt`

**Interfaces:**
- Produces: `User` without `entitlement`, `entitlementExpiresAt`, `entitlementSource`; `UserResponse` without `entitlement`, `entitlementExpiresAt`. No `PlanLimits`, `PlanLimitException`, `isPremium`, `Entitlement`, `EntitlementSource` anywhere.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/homebudget/app/service/NoAccountCapTest.kt`:

```kotlin
package com.homebudget.app.service

import com.homebudget.app.domain.AccountType
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.dto.CreateAccountRequest
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.UUID

/** A self-hosted instance sells nothing, so nothing caps how many accounts a person keeps. */
@SpringBootTest
@ActiveProfiles("test")
class NoAccountCapTest {

    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var currencyRepository: CurrencyRepository

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `a twelfth account is created like the first`() {
        val usd = currencyRepository.findByCode("USD")!!
        val user = userRepository.save(
            User(
                email = "cap-${UUID.randomUUID()}@example.com",
                passwordHash = "x",
                firstName = "Cap",
                lastName = "Test",
                role = Role.USER,
                baseCurrency = usd
            )
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UserPrincipal(user), null, emptyList())

        repeat(12) { i ->
            accountService.create(
                CreateAccountRequest(
                    name = "Account $i",
                    type = AccountType.CASH,
                    initialBalance = BigDecimal.ZERO,
                    currencyId = usd.id
                )
            )
        }

        assertEquals(12, accountService.findAll().size)
    }
}
```

Before writing it, open `dto/Dtos.kt` and check `CreateAccountRequest`'s parameter names and which ones have defaults, and `domain/Account.kt` for the `AccountType` constant names; adjust the constructor call to match exactly (pass every parameter without a default).

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -B -Dfrontend.skip=true test -Dtest=NoAccountCapTest`
Expected: FAIL with `PlanLimitException: The free plan covers 5 accounts...`.

- [ ] **Step 3: Delete the billing code and its tests**

```bash
git rm -r src/main/kotlin/com/homebudget/app/billing src/test/kotlin/com/homebudget/app/billing
git rm src/main/kotlin/com/homebudget/app/domain/PlayPurchase.kt src/main/kotlin/com/homebudget/app/repository/PlayPurchaseRepository.kt
git rm src/main/kotlin/com/homebudget/app/service/PlanLimitException.kt src/test/kotlin/com/homebudget/app/service/PlanLimitTest.kt docs/PREMIUM.md
```

- [ ] **Step 4: Strip entitlements from `User`**

In `domain/User.kt` delete the `Entitlement` enum, the `EntitlementSource` enum, the `PlanLimits` object, the three fields `entitlement`, `entitlementExpiresAt`, `entitlementSource` with their KDoc, and the `User.isPremium` extension at the end of the file. Replace the KDoc on `uuid` with:

```kotlin
    /**
     * A stable public identifier. Not the primary key: an auto-increment id tells anyone
     * who sees it roughly how many accounts exist and in what order they were created.
     */
```

Remove the now-unused `java.time.Instant` import if nothing else uses it.

- [ ] **Step 5: Strip entitlements from the user response**

In `dto/Dtos.kt` replace `data class UserResponse(...)` with:

```kotlin
data class UserResponse(
    val id: Long,
    val uuid: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: Role,
    val emailVerified: Boolean,
    val baseCurrency: CurrencyResponse?
)
```

Remove the `Entitlement` import from `Dtos.kt`. In `service/Mappers.kt` replace `fun User.toResponse()` with:

```kotlin
fun User.toResponse() = UserResponse(
    id = id,
    uuid = uuid,
    email = email,
    firstName = firstName,
    lastName = lastName,
    role = role,
    emailVerified = emailVerified,
    baseCurrency = baseCurrency?.toResponse()
)
```

and remove the `Entitlement` / `isPremium` imports.

- [ ] **Step 6: Remove the cap from every door**

`service/AccountService.kt`: delete `requireAccountSlot` (with its KDoc), delete the call `requireAccountSlot(user)` in `create`, and remove the imports of `PlanLimits`, `isPremium` and `User` if unused.

`repository/AccountRepository.kt`: delete `countByUserAndArchivedFalse` and its KDoc.

`service/SyncService.kt`: in `applyAccounts` delete the block that starts with the comment `// The same cap the REST path applies` through the `continue` and closing brace of the `if (existing == null && !user.isPremium() && ...)` statement. Remove the `isPremium`/`PlanLimits` imports if they are explicit (the file imports `com.homebudget.app.domain.*`, so nothing to remove there).

`importer/homemoney/HomeMoneyImportService.kt`: delete the `accountCapReason` function and its KDoc; in the plan builder replace

```kotlin
            blockedReason = accountCapReason(
                user,
                plannedAccounts.count { it.included && it.targetAccountId == null }
            )
```

with `blockedReason = null`; in `prepare` replace

```kotlin
        plan.blockedReason?.let { throw com.homebudget.app.service.PlanLimitException(it) }
        accountCapReason(user, included.count { it.targetAccountId == null })?.let {
            throw com.homebudget.app.service.PlanLimitException(it)
        }
```

with

```kotlin
        plan.blockedReason?.let { throw IllegalStateException(it) }
```

and remove the `PlanLimits` / `isPremium` imports. (`blockedReason` stays on the wire for client compatibility; it is now always null.)

`controller/GlobalExceptionHandler.kt`: delete the `handlePlanLimit` handler (with its KDoc) and the `BillingUnavailableException` handler (with its KDoc).

`src/main/resources/application.yml`: delete the whole `billing:` block under `app:`.

- [ ] **Step 7: Run the whole server suite**

Run: `./mvnw -B -Dfrontend.skip=true test`
Expected: PASS, including `NoAccountCapTest`. If `HomeMoneyApiTest` mentions `isPremium`, that is HomeMoney's own JSON field (`@JsonProperty("IsPremium")`) and stays.

- [ ] **Step 8: Record the planning deviations in the spec**

In `docs/superpowers/specs/2026-10-15-open-source-baseline-design.md`, under "Data model":
- change `transactions.created_by_user_id` (not null) to: "`transactions.created_by_user_id`, nullable: null for rows the server writes itself (subscription charges) and after the author's account is deleted (`ON DELETE SET NULL`)."
- change the `household_members` bullet to "`id`, `household_id`, `user_id`, `role`, `joined_at`; `UNIQUE (household_id, user_id)`".
- change "`bank_connections`, which also gains `connected_by_user_id`" to "... gains a nullable `connected_by_user_id` (`ON DELETE SET NULL`)".
- add "`users.last_sign_in_at` (nullable), set on every successful sign-in" and "`instance_settings` (`key` primary key, `value`), holding the registration mode an administrator set at runtime".

Under "Android" replace "remembered in DataStore" with "remembered next to the session tokens in `TokenStore` (read synchronously by every request)".

- [ ] **Step 9: Commit**

```bash
git add -A src/main src/test docs
git commit -m "Remove Play billing, premium and the five-account cap"
```

---

### Task 2: Move the schema to Postgres

The schema keeps its current shape (still `user_id`) in this task; households arrive in Task 3. Splitting it this way means a dialect problem and a modelling problem never show up in the same failing build.

**Files:**
- Delete: `src/main/resources/db/migration/V1__initial_schema.sql`, `V3__add_entitlement.sql`, `V4__homemoney_import.sql`, `V5__play_purchases.sql`, `V6__monobank_corporate.sql`
- Create: `src/main/resources/db/migration/V1__baseline.sql`
- Keep: `src/main/resources/db/migration/V2__seed_currency_catalog.sql` (its `INSERT` is portable; only its header comment changes)
- Modify: `pom.xml`, `src/main/resources/application-local.yml`, `src/main/resources/application-prod.yml`, `src/test/resources/application-test.yml`, `src/main/kotlin/com/homebudget/app/repository/SyncCursorRepository.kt`, `src/main/kotlin/com/homebudget/app/service/UserService.kt` (comment only)
- Create (test): `src/test/kotlin/com/homebudget/app/PostgresContainerConfig.kt`, `src/test/kotlin/com/homebudget/app/PostgresSchemaTest.kt`

**Interfaces:**
- Produces: `PostgresContainerConfig` — a `@TestConfiguration` that provides a `@ServiceConnection` `PostgreSQLContainer`. Later Postgres test classes use `@Import(PostgresContainerConfig::class)` plus `@Testcontainers(disabledWithoutDocker = true)` and `@TestPropertySource(properties = ["spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"])`.

- [ ] **Step 1: Swap the database dependencies**

In `pom.xml`:
- delete the `mariadb-java-client`, `mysql-connector-j` and `flyway-mysql` dependency blocks;
- add, next to `flyway-core`:

```xml
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
```

- add test dependencies (versions come from the Spring Boot BOM):

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Write the Postgres schema test**

`src/test/kotlin/com/homebudget/app/PostgresContainerConfig.kt`:

```kotlin
package com.homebudget.app

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer

/**
 * A real Postgres for the tests that must prove the dialect, not just the model.
 *
 * H2 in PostgreSQL mode runs the bulk of the suite because it needs no Docker; it is close
 * to Postgres but not the same engine, and the migrations ship to Postgres alone.
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresContainerConfig {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
}
```

`src/test/kotlin/com/homebudget/app/PostgresSchemaTest.kt`:

```kotlin
package com.homebudget.app

import com.homebudget.app.repository.CurrencyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

/** The migrations against the engine they ship to, validated by Hibernate as in production. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresContainerConfig::class)
@TestPropertySource(properties = ["spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"])
class PostgresSchemaTest {

    @Autowired private lateinit var currencyRepository: CurrencyRepository

    @Test
    fun `migrations build a schema the entities validate against on Postgres`() {
        assertEquals(104L, currencyRepository.count())
        assertEquals(8, currencyRepository.findByCode("BTC")?.decimals)
    }
}
```

- [ ] **Step 3: Point the H2 profiles at PostgreSQL mode**

`src/test/resources/application-test.yml` — replace the datasource URL with:

```yaml
    url: jdbc:h2:mem:homebudget-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
```

`src/main/resources/application-local.yml` — replace the datasource URL with:

```yaml
    url: jdbc:h2:file:./data/homebudgetdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
```

(`clean-on-validation-error: true` already rebuilds a stale local database.)

`src/main/resources/application-prod.yml` — replace the `spring.datasource` and `spring.jpa.database-platform` lines with:

```yaml
  datasource:
    url: jdbc:postgresql://${DB_HOST:db}:${DB_PORT:5432}/${DB_NAME:personalmoney}
    username: ${DB_USERNAME:personalmoney}
    password: ${DB_PASSWORD}
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

and delete the `# No baseline on purpose...` comment block under `flyway` (it describes the old MySQL production database).

- [ ] **Step 4: Run the tests to watch the old migrations fail**

Run: `./mvnw -B -Dfrontend.skip=true test -Dtest=SchemaConsistencyTest`
Expected: FAIL — Flyway reports a syntax error in `V1__initial_schema.sql` (`AUTO_INCREMENT` / `DATETIME` are not PostgreSQL).

- [ ] **Step 5: Replace the migrations with a Postgres baseline**

```bash
git rm src/main/resources/db/migration/V1__initial_schema.sql src/main/resources/db/migration/V3__add_entitlement.sql src/main/resources/db/migration/V4__homemoney_import.sql src/main/resources/db/migration/V5__play_purchases.sql src/main/resources/db/migration/V6__monobank_corporate.sql
```

Create `src/main/resources/db/migration/V1__baseline.sql`. Column types follow the entity field types: `Instant` → `TIMESTAMP(3) WITH TIME ZONE`, `LocalDateTime` → `TIMESTAMP`, `LocalDate` → `DATE`, `BigDecimal` → `NUMERIC(p,s)` with the entity's precision and scale.

```sql
-- V1: PersonalMoney baseline schema, PostgreSQL.
--
-- Three conventions run through the whole thing and explain most of the column choices:
--
--   Money is an integer count of a currency's smallest unit ("minor units"), never a
--   decimal. The exponent lives in currencies.decimals, because a third of the catalog
--   is not two-decimal: JPY and KRW have no minor unit, the Gulf dinars have three
--   digits, BTC has eight. Both clients store money this way, so neither has to round.
--
--   Account balances are derived, never stored. A stored running total has to be
--   mutated by every write, and an offline client replaying its outbox applies the
--   same delta twice and drifts silently. `initial_balance_minor` is the opening
--   figure; the current balance is summed from the ledger on read, which is idempotent.
--
--   Every row a client can own carries `uuid`, `updated_at` and `deleted`. The uuid is
--   minted by whichever side creates the row, so a phone can create a transaction that
--   references an account the server has never seen and both arrive in one push.
--   `deleted` is a tombstone: a hard delete is invisible to a client that was offline
--   when it happened, so deletions have to be something you can *replicate*.
--
-- Instants are TIMESTAMP WITH TIME ZONE; local date-times (audit columns) are TIMESTAMP.
-- The test suite runs this file on H2 in PostgreSQL mode as well as on Postgres itself,
-- so stay within syntax both accept.

-- ── Currencies ──────────────────────────────────────────────────────────────
-- The catalog is global and admin-managed, so it is not synced and needs no uuid.
CREATE TABLE currencies (
    id                    BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    -- 10 rather than 3: the catalog carries 4-character tickers (USDT, USDC).
    code                  VARCHAR(10)   NOT NULL UNIQUE,
    name                  VARCHAR(255)  NOT NULL,
    symbol                VARCHAR(10)   NOT NULL,
    -- Digits after the decimal point, and the exponent for minor units.
    decimals              INT           NOT NULL DEFAULT 2,
    sort_order            INT           NOT NULL DEFAULT 0,
    -- Legacy single-rate FX. Nothing new should be built on these two columns.
    exchange_rate_to_base NUMERIC(18,6) NOT NULL DEFAULT 1.000000,
    is_base               BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Users ───────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    uuid             VARCHAR(36)  NOT NULL UNIQUE,
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    first_name       VARCHAR(255) NOT NULL,
    last_name        VARCHAR(255) NOT NULL,
    role             VARCHAR(20)  NOT NULL DEFAULT 'USER',
    email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    base_currency_id BIGINT       REFERENCES currencies (id),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Long-lived refresh tokens. Stored hashed: a leaked table must not hand out sessions.
CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id    BIGINT                      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)                 NOT NULL UNIQUE,
    expires_at TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    revoked    BOOLEAN                     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);

-- Email verification and password reset share one table; only the SHA-256 is stored.
CREATE TABLE auth_tokens (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id    BIGINT                      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    VARCHAR(30)                 NOT NULL,
    token_hash VARCHAR(64)                 NOT NULL UNIQUE,
    expires_at TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP(3) WITH TIME ZONE NULL,
    created_at TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auth_token_user ON auth_tokens (user_id, purpose);

-- ── Bank connections ────────────────────────────────────────────────────────
-- One set of credentials per bank, not per account. `credentials` is the provider's
-- opaque, encrypted payload. Deliberately NOT part of the sync protocol - bank
-- credentials stay on the server and never reach a device.
CREATE TABLE bank_connections (
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id            BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider           VARCHAR(30)   NOT NULL,
    credentials        VARCHAR(2048) NOT NULL,
    label              VARCHAR(255)  NULL,
    -- Who the credentials belong to at the bank, so a renewed consent finds its row.
    external_client_id VARCHAR(100)  NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    last_verified_at   TIMESTAMP     NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bank_conn_user   ON bank_connections (user_id);
CREATE INDEX idx_bank_conn_client ON bank_connections (user_id, provider, external_client_id);

-- Which slice of the global catalog is in use, and in what order.
CREATE TABLE user_currency_settings (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    uuid        VARCHAR(36)                 NOT NULL UNIQUE,
    user_id     BIGINT                      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    currency_id BIGINT                      NOT NULL REFERENCES currencies (id),
    active      BOOLEAN                     NOT NULL DEFAULT FALSE,
    sort_order  INT                         NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN                     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_ucs_user_currency UNIQUE (user_id, currency_id)
);

CREATE INDEX idx_ucs_sync ON user_currency_settings (user_id, updated_at);

-- user_id IS NULL is a globally fetched rate (Frankfurter); a populated owner is a
-- manual override of the same pair. Rates are refetchable, so they are not synced.
CREATE TABLE exchange_rates (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    base_code  VARCHAR(10)    NOT NULL,
    quote_code VARCHAR(10)    NOT NULL,
    rate       NUMERIC(24,10) NOT NULL,
    rate_date  DATE           NOT NULL,
    source     VARCHAR(20)    NOT NULL,
    user_id    BIGINT         NULL REFERENCES users (id) ON DELETE CASCADE,
    updated_at TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fx_pair ON exchange_rates (base_code, quote_code, rate_date);

-- ── Accounts ────────────────────────────────────────────────────────────────
CREATE TABLE accounts (
    id                    BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    uuid                  VARCHAR(36)                 NOT NULL UNIQUE,
    user_id               BIGINT                      NOT NULL REFERENCES users (id),
    name                  VARCHAR(255)                NOT NULL,
    type                  VARCHAR(20)                 NOT NULL,
    -- Opening balance. The current balance is initial + SUM(ledger); see the header.
    initial_balance_minor BIGINT                      NOT NULL DEFAULT 0,
    currency_id           BIGINT                      NOT NULL REFERENCES currencies (id),
    description           VARCHAR(500),
    icon                  VARCHAR(50)                 NOT NULL DEFAULT 'wallet',
    color                 VARCHAR(20)                 NOT NULL DEFAULT '#4F46E5',
    archived              BOOLEAN                     NOT NULL DEFAULT FALSE,
    sort_order            INT                         NOT NULL DEFAULT 0,
    include_in_total      BOOLEAN                     NOT NULL DEFAULT TRUE,
    -- Which bank connection feeds this account, and the bank's own id for it.
    bank_connection_id    BIGINT                      NULL REFERENCES bank_connections (id) ON DELETE SET NULL,
    external_account_id   VARCHAR(100)                NULL,
    created_at            TIMESTAMP                   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN                     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_accounts_sync     ON accounts (user_id, updated_at);
CREATE INDEX idx_accounts_archived ON accounts (archived);

-- ── Categories ──────────────────────────────────────────────────────────────
-- Deliberately no UNIQUE (owner, name, type): sub-categories under different parents
-- legitimately share a name, and a uniqueness failure during a sync push is much worse
-- than a duplicate name. CategoryService still rejects same-name siblings over REST.
CREATE TABLE categories (
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    uuid               VARCHAR(36)                 NOT NULL UNIQUE,
    user_id            BIGINT                      NOT NULL REFERENCES users (id),
    name               VARCHAR(255)                NOT NULL,
    type               VARCHAR(10)                 NOT NULL,
    icon               VARCHAR(50)                 NOT NULL DEFAULT 'tag',
    color              VARCHAR(20)                 NOT NULL DEFAULT '#6366F1',
    description        VARCHAR(500),
    parent_id          BIGINT                      NULL REFERENCES categories (id),
    archived           BOOLEAN                     NOT NULL DEFAULT FALSE,
    sort_order         INT                         NOT NULL DEFAULT 0,
    is_default         BOOLEAN                     NOT NULL DEFAULT FALSE,
    -- Monthly spending limit in the base currency, expense categories only.
    budget_limit_minor BIGINT                      NULL,
    -- The import source's own id, so a second import finds what the first created.
    external_id        VARCHAR(100)                NULL,
    created_at         TIMESTAMP                   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            BOOLEAN                     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_categories_sync     ON categories (user_id, updated_at);
CREATE INDEX idx_categories_parent   ON categories (parent_id);
CREATE INDEX idx_categories_archived ON categories (archived);
CREATE INDEX idx_categories_external ON categories (user_id, external_id);

-- MCC codes learned from imported card transactions, used to suggest a category.
CREATE TABLE category_mcc_codes (
    category_id BIGINT NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    mcc_code    INT    NOT NULL,
    PRIMARY KEY (category_id, mcc_code)
);

-- ── Subscriptions ───────────────────────────────────────────────────────────
-- Recurring charges. A scheduled job walks next_charge_date forward and writes a
-- transaction per due charge, stamping it with subscription_id.
CREATE TABLE subscriptions (
    id                      BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    uuid                    VARCHAR(36)                 NOT NULL UNIQUE,
    user_id                 BIGINT                      NOT NULL REFERENCES users (id),
    name                    VARCHAR(255)                NOT NULL,
    account_id              BIGINT                      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    category_id             BIGINT                      NULL REFERENCES categories (id),
    amount_minor            BIGINT                      NOT NULL,
    currency_code           VARCHAR(10)                 NOT NULL,
    frequency               VARCHAR(20)                 NOT NULL,
    interval_count          INT                         NOT NULL DEFAULT 1,
    next_charge_date        DATE                        NOT NULL,
    start_date              DATE                        NOT NULL,
    end_date                DATE                        NULL,
    active                  BOOLEAN                     NOT NULL DEFAULT TRUE,
    reminder_minutes_before INT                         NULL,
    icon                    VARCHAR(50)                 NOT NULL DEFAULT 'subscriptions',
    color                   VARCHAR(20)                 NOT NULL DEFAULT '#008080',
    note                    VARCHAR(500)                NULL,
    created_at              TIMESTAMP                   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 BOOLEAN                     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_sub_sync   ON subscriptions (user_id, updated_at);
CREATE INDEX idx_sub_next   ON subscriptions (next_charge_date);
CREATE INDEX idx_sub_active ON subscriptions (active);

-- ── Transactions ────────────────────────────────────────────────────────────
-- A transfer is *two* rows sharing a transfer_group_id: a negative debit on the source
-- account and a positive credit on the destination.
--
-- Sign convention, matching the Android client's balance query:
--   INCOME   -> account_amount_minor is a positive magnitude, added
--   EXPENSE  -> account_amount_minor is a positive magnitude, subtracted
--   TRANSFER -> account_amount_minor is already signed, added as-is
CREATE TABLE transactions (
    id                              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    uuid                            VARCHAR(36)                 NOT NULL UNIQUE,
    user_id                         BIGINT                      NOT NULL REFERENCES users (id),
    account_id                      BIGINT                      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    -- Nullable: transfer rows have no category, and a deleted category must not take
    -- its history with it.
    category_id                     BIGINT                      NULL REFERENCES categories (id),
    amount_minor                    BIGINT                      NOT NULL,
    currency_code                   VARCHAR(10)                 NOT NULL,
    account_amount_minor            BIGINT                      NOT NULL,
    fx_rate_to_account              NUMERIC(24,10)              NOT NULL DEFAULT 1,
    type                            VARCHAR(10)                 NOT NULL,
    occurred_at                     TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    description                     VARCHAR(255),
    subscription_id                 BIGINT                      NULL REFERENCES subscriptions (id),
    transfer_group_id               VARCHAR(36)                 NULL,
    transfer_counterpart_account_id BIGINT                      NULL,
    transfer_fee_minor              BIGINT                      NULL,
    -- The bank's own id for this operation, used to avoid importing it twice.
    external_id                     VARCHAR(100)                NULL,
    created_at                      TIMESTAMP                   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                         BOOLEAN                     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_tx_sync           ON transactions (user_id, updated_at);
CREATE INDEX idx_tx_account        ON transactions (account_id);
CREATE INDEX idx_tx_category       ON transactions (category_id);
CREATE INDEX idx_tx_occurred_at    ON transactions (occurred_at);
CREATE INDEX idx_tx_transfer_group ON transactions (transfer_group_id);
CREATE INDEX idx_tx_subscription   ON transactions (subscription_id);
CREATE INDEX idx_tx_external       ON transactions (user_id, external_id);
```

Before saving, compare every table against its entity in `domain/` (column names via `@Column(name=...)` or Hibernate's snake_case naming, lengths, nullability) and fix the DDL, not the entity, wherever they differ. In particular check `Account.description`, `Transaction.description` (plain `@Column` means `VARCHAR(255)`), and any `length =` attributes.

In `V2__seed_currency_catalog.sql` change only the header comment: drop the sentence about "the legacy single-rate column the React client still reads" so it reads "exchange_rate_to_base is left at 1.0 for every row; real rates live in exchange_rates, refreshed from Frankfurter."

- [ ] **Step 6: Teach the sync cursor the Postgres result type**

Native scalar queries on Postgres return `OffsetDateTime` for `TIMESTAMP WITH TIME ZONE`. In `repository/SyncCursorRepository.kt` extend the `when`:

```kotlin
        return when (val raw = em.createNativeQuery(sql).setParameter("uid", userId).singleResult) {
            null -> null
            is Timestamp -> raw.toInstant()
            is Instant -> raw
            is java.time.OffsetDateTime -> raw.toInstant()
            else -> null
        }
```

In `service/UserService.kt` replace the comment `// categories.parent_id points at categories: MySQL enforces FKs row by row,` / `// so a single DELETE can hit a parent before its child. Break the links first.` with `// categories.parent_id points at categories; break the links before deleting.`

- [ ] **Step 7: Run the full suite**

Run: `./mvnw -B -Dfrontend.skip=true test`
Expected: PASS. If Hibernate's `validate` rejects a column (`wrong column type encountered in column [x] in table [y]; found [...], but expecting [...]`), change that column's type in `V1__baseline.sql` to what Hibernate expects and run again. `PostgresSchemaTest` is skipped without Docker; if Docker is available locally, start it and run `-Dtest=PostgresSchemaTest` once — Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A pom.xml src/main/resources src/test src/main/kotlin
git commit -m "Move the schema to Postgres with one clean baseline migration"
```

---
### Task 3: Households — data model, scoping and isolation tests

The widest task in the plan: every ledger query changes owner. It is one task because the build cannot be green halfway through (entities, repositories and services change together). The isolation tests are written first and are the acceptance gate.

**Files:**
- Create: `src/main/kotlin/com/homebudget/app/domain/Household.kt`, `domain/HouseholdMember.kt`, `repository/HouseholdRepository.kt`, `repository/HouseholdMemberRepository.kt`, `service/HouseholdService.kt`
- Modify: `src/main/resources/db/migration/V1__baseline.sql`; entities `domain/{User,Account,Category,Subscription,Transaction,UserCurrencySetting,ExchangeRate,BankConnection}.kt`; repositories `repository/{Account,Category,Subscription,Transaction,UserCurrencySetting,ExchangeRate,BankConnection,User,SyncCursor}Repository.kt`; `security/{SecurityUtils,UserPrincipal,CustomUserDetailsService}.kt`; services `service/{Account,Category,Subscription,Sync,Transaction,Transfer,User,Fx,Currency}Service.kt`, `service/Mappers.kt`; controllers `controller/{Auth,User,Fx,Bank,CsvImport}Controller.kt`; `bank/BankConnectionService.kt`; `importer/homemoney/*.kt`
- Test (create): `src/test/kotlin/com/homebudget/app/TestIdentity.kt`, `src/test/kotlin/com/homebudget/app/HouseholdIsolationTest.kt`, `src/test/kotlin/com/homebudget/app/PostgresHouseholdIsolationTest.kt`
- Test (modify): every test that builds a `User` with `baseCurrency`, calls `UserPrincipal(`, or builds a ledger entity with `user =` — find them with `grep -rln "UserPrincipal(\|baseCurrency = \|user = " src/test/kotlin`

**Interfaces:**
- Consumes: `PostgresContainerConfig` (Task 2).
- Produces (used by Tasks 4–7):
  - `class Household(id: Long, uuid: String, name: String, baseCurrency: Currency?, createdAt: Instant, updatedAt: Instant)`
  - `enum class HouseholdRole { OWNER, MEMBER }`, `class HouseholdMember(id, household: Household, user: User, role: HouseholdRole, joinedAt: Instant)`
  - `HouseholdService.createFor(owner: User, baseCurrency: Currency?): Household`, `HouseholdService.householdOf(user: User): Household`
  - `HouseholdRepository.baseCurrencyCodes(): List<String>`
  - `HouseholdMemberRepository.findFirstByUserId(userId: Long): HouseholdMember?`, `countByHouseholdId(householdId: Long): Long`, `findAllByHouseholdId(householdId: Long): List<HouseholdMember>`
  - `UserPrincipal(user: User, household: Household)`
  - `SecurityUtils.getCurrentUser(): User`, `SecurityUtils.getCurrentHousehold(): Household`
  - `User.lastSignInAt: Instant?`; `User` no longer has `baseCurrency`
  - `Transaction.household`, `Transaction.createdBy: User?`; `BankConnection.household`, `BankConnection.connectedBy: User?`; `ExchangeRate.household: Household?`; every other ledger entity's owner field is `household`
  - `UserService.deleteUser(user: User)` (Task 7's admin delete uses it)
  - Test helper `TestIdentity` with `newMember(prefix: String, baseCurrency: String = "USD"): UserPrincipal`, `joinAs(prefix: String, household: Household): UserPrincipal`, `signIn(principal: UserPrincipal)`

- [ ] **Step 1: Write the test identity helper**

`src/test/kotlin/com/homebudget/app/TestIdentity.kt`:

```kotlin
package com.homebudget.app

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.HouseholdMember
import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.HouseholdService
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * Users the way the application makes them: each with a household of their own.
 *
 * Registered in a test with `@Import(TestIdentity::class)`. [joinAs] stands in for the
 * invitation flow that sub-project 2 adds, so tests can already put two logins in one
 * household.
 */
class TestIdentity(
    private val users: UserRepository,
    private val currencies: CurrencyRepository,
    private val households: HouseholdService,
    private val members: HouseholdMemberRepository
) {
    fun newMember(prefix: String, baseCurrency: String = "USD"): UserPrincipal {
        val user = newUser(prefix)
        return UserPrincipal(user, households.createFor(user, currencies.findByCode(baseCurrency)))
    }

    fun joinAs(prefix: String, household: Household): UserPrincipal {
        val user = newUser(prefix)
        members.save(HouseholdMember(household = household, user = user, role = HouseholdRole.MEMBER))
        return UserPrincipal(user, household)
    }

    fun signIn(principal: UserPrincipal) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
    }

    private fun newUser(prefix: String): User = users.save(
        User(
            email = "$prefix-${UUID.randomUUID()}@example.com",
            passwordHash = "x",
            firstName = prefix,
            lastName = "Test",
            role = Role.USER
        )
    )
}
```

- [ ] **Step 2: Write the isolation tests**

`src/test/kotlin/com/homebudget/app/HouseholdIsolationTest.kt`:

```kotlin
package com.homebudget.app

import com.homebudget.app.bank.BankConnectionService
import com.homebudget.app.domain.BankConnection
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.ExchangeRate
import com.homebudget.app.domain.RateSource
import com.homebudget.app.domain.SubscriptionFrequency
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.*
import com.homebudget.app.repository.*
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The regression net for scoping the ledger by household: nothing one household owns is
 * readable or writable from another, by REST services or by sync - and everything is
 * shared between two logins of the same household.
 *
 * Open so [PostgresHouseholdIsolationTest] can run the same cases on Postgres.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
open class HouseholdIsolationTest {

    @Autowired lateinit var identity: TestIdentity
    @Autowired lateinit var syncService: SyncService
    @Autowired lateinit var accountService: AccountService
    @Autowired lateinit var categoryService: CategoryService
    @Autowired lateinit var subscriptionService: SubscriptionService
    @Autowired lateinit var transactionService: TransactionService
    @Autowired lateinit var bankConnectionService: BankConnectionService
    @Autowired lateinit var accountRepository: AccountRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var subscriptionRepository: SubscriptionRepository
    @Autowired lateinit var transactionRepository: TransactionRepository
    @Autowired lateinit var bankConnectionRepository: BankConnectionRepository
    @Autowired lateinit var exchangeRateRepository: ExchangeRateRepository

    private lateinit var alice: UserPrincipal
    private lateinit var bob: UserPrincipal

    private val accountUuid = uuid()
    private val categoryUuid = uuid()
    private val subscriptionUuid = uuid()
    private val transactionUuid = uuid()

    @BeforeEach
    fun setUp() {
        alice = identity.newMember("alice")
        bob = identity.newMember("bob")
        identity.signIn(alice)
        val pushed = syncService.push(
            SyncPushRequest(
                SyncPayload(
                    currencySettings = listOf(SyncCurrencySetting(uuid = uuid(), currencyCode = "EUR", active = true)),
                    accounts = listOf(SyncAccount(uuid = accountUuid, name = "Alice cash", currencyCode = "USD")),
                    categories = listOf(SyncCategory(uuid = categoryUuid, name = "Alice food", type = CategoryType.EXPENSE)),
                    subscriptions = listOf(
                        SyncSubscription(
                            uuid = subscriptionUuid, name = "Alice gym", accountUuid = accountUuid,
                            amount = BigDecimal("30.00"), currencyCode = "USD",
                            frequency = SubscriptionFrequency.MONTHLY,
                            nextChargeDate = LocalDate.of(2026, 11, 1), startDate = LocalDate.of(2026, 1, 1)
                        )
                    ),
                    transactions = listOf(
                        SyncTransaction(
                            uuid = transactionUuid, accountUuid = accountUuid, categoryUuid = categoryUuid,
                            amount = BigDecimal("12.50"), currencyCode = "USD", accountAmount = BigDecimal("12.50"),
                            type = TransactionType.EXPENSE, occurredAt = Instant.parse("2026-10-01T10:00:00Z")
                        )
                    )
                )
            )
        )
        assertTrue(pushed.rejected.isEmpty(), "setup push was rejected: ${pushed.rejected}")
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun uuid() = UUID.randomUUID().toString()
    private fun aliceAccountId() = accountRepository.findByUuidAndHousehold(accountUuid, alice.household)!!.id
    private fun aliceCategoryId() = categoryRepository.findByUuidAndHousehold(categoryUuid, alice.household)!!.id
    private fun aliceSubscriptionId() = subscriptionRepository.findByUuidAndHousehold(subscriptionUuid, alice.household)!!.id
    private fun aliceTransactionId() = transactionRepository.findByUuidAndHousehold(transactionUuid, alice.household)!!.id

    @Test
    fun `another household cannot read any of the ledger through the services`() {
        identity.signIn(bob)
        assertTrue(accountService.findAll().isEmpty())
        assertThrows<NoSuchElementException> { accountService.findById(aliceAccountId()) }
        assertThrows<NoSuchElementException> { categoryService.findById(aliceCategoryId()) }
        assertThrows<NoSuchElementException> { subscriptionService.findById(aliceSubscriptionId()) }
        assertThrows<NoSuchElementException> { transactionService.findById(aliceTransactionId()) }
    }

    @Test
    fun `another household cannot change or delete the ledger through the services`() {
        identity.signIn(bob)
        assertThrows<NoSuchElementException> { accountService.update(aliceAccountId(), UpdateAccountRequest(name = "mine now")) }
        assertThrows<NoSuchElementException> { accountService.delete(aliceAccountId()) }
        assertThrows<NoSuchElementException> { transactionService.delete(aliceTransactionId()) }
        assertThrows<NoSuchElementException> { categoryService.delete(aliceCategoryId()) }
        assertThrows<NoSuchElementException> { subscriptionService.delete(aliceSubscriptionId()) }

        identity.signIn(alice)
        assertEquals("Alice cash", accountService.findById(aliceAccountId()).name)
    }

    @Test
    fun `a pull from another household sees nothing`() {
        identity.signIn(bob)
        val changes = syncService.pull(Instant.EPOCH, 500).changes
        assertTrue(changes.accounts.isEmpty())
        assertTrue(changes.categories.isEmpty())
        assertTrue(changes.subscriptions.isEmpty())
        assertTrue(changes.transactions.isEmpty())
        assertTrue(changes.currencySettings.isEmpty())
    }

    @Test
    fun `a push from another household cannot point at this household's rows`() {
        identity.signIn(bob)
        val response = syncService.push(
            SyncPushRequest(
                SyncPayload(
                    transactions = listOf(
                        SyncTransaction(
                            uuid = uuid(), accountUuid = accountUuid,
                            amount = BigDecimal("999.00"), currencyCode = "USD", accountAmount = BigDecimal("999.00"),
                            type = TransactionType.EXPENSE, occurredAt = Instant.parse("2026-10-02T10:00:00Z")
                        )
                    )
                )
            )
        )
        assertEquals(0, response.applied)
        assertEquals(SyncRejectionReason.UNKNOWN_REFERENCE, response.rejected.single().reason)
    }

    @Test
    fun `bank connections and manual rates stay in their household`() {
        val connection = bankConnectionRepository.save(
            BankConnection(household = alice.household, connectedBy = alice.user, provider = "MONOBANK", credentials = "x", label = "Alice bank")
        )
        exchangeRateRepository.save(
            ExchangeRate(
                baseCode = "XAU", quoteCode = "USD", rate = BigDecimal("2400"),
                rateDate = LocalDate.of(2026, 10, 1), source = RateSource.MANUAL, household = alice.household
            )
        )

        identity.signIn(bob)
        assertTrue(bankConnectionService.findAll().none { it.id == connection.id })
        assertThrows<NoSuchElementException> { bankConnectionService.findById(connection.id) }
        assertTrue(exchangeRateRepository.latestFor(bob.household).none { it.household != null })
    }

    @Test
    fun `a second login in the same household sees and edits the same ledger`() {
        val partner = identity.joinAs("partner", alice.household)
        identity.signIn(partner)

        assertEquals(listOf("Alice cash"), accountService.findAll().map { it.name })
        assertEquals(1, syncService.pull(Instant.EPOCH, 500).changes.transactions.size)
        accountService.update(aliceAccountId(), UpdateAccountRequest(name = "Shared cash"))

        identity.signIn(alice)
        assertEquals("Shared cash", accountService.findById(aliceAccountId()).name)
    }

    @Test
    fun `a transaction records who wrote it, by sync and over REST`() {
        val synced = transactionRepository.findByUuidAndHousehold(transactionUuid, alice.household)!!
        assertEquals(alice.user.id, synced.createdBy?.id)

        val partner = identity.joinAs("partner", alice.household)
        identity.signIn(partner)
        val created = transactionService.create(restExpense(aliceAccountId(), aliceCategoryId()))
        assertEquals(partner.user.id, transactionRepository.findById(created.id).orElseThrow().createdBy?.id)
    }
}
```

`restExpense(accountId, categoryId)` is a private helper in the test class returning a `CreateTransactionRequest` for a 5.00 expense today; build it with the parameter names from `dto/Dtos.kt` (find an existing construction with `grep -rn "CreateTransactionRequest(" src/test src/main` and copy its shape). Add one more case the same way for `TransferService` if it has a public create method: both legs carry the signed-in user as `createdBy`.

Before running, check three names against the code and adjust the test, not the code: the `RateSource` constant for hand-typed rates (open `domain/ExchangeRate.kt`; use whatever `FxService.setOverride` writes), `UpdateAccountRequest`'s parameter names (all optional, per `AccountService.update`), and the exception each service's `findById`/`delete` throws for a missing row today (`NoSuchElementException` in `AccountService`; use the actual type if another service differs). `BankConnection`'s `household` and `connectedBy` parameters are defined in Step 7.

`src/test/kotlin/com/homebudget/app/PostgresHouseholdIsolationTest.kt`:

```kotlin
package com.homebudget.app

import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

/** The same isolation cases on the engine production runs. */
@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresContainerConfig::class, TestIdentity::class)
@TestPropertySource(properties = ["spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"])
class PostgresHouseholdIsolationTest : HouseholdIsolationTest()
```

- [ ] **Step 3: Confirm the tests fail**

Run: `./mvnw -B -Dfrontend.skip=true test-compile`
Expected: FAIL to compile — `Household`, `HouseholdService`, `findByUuidAndHousehold`, `household =`, `createdBy` do not exist yet.

- [ ] **Step 4: Change the schema**

Edit `src/main/resources/db/migration/V1__baseline.sql`:

1. In `users`, delete the `base_currency_id` line and add after `email_verified`:

```sql
    -- Shown to the administrator; set on every successful sign-in.
    last_sign_in_at  TIMESTAMP(3) WITH TIME ZONE NULL,
```

2. Directly after the `users` table, add:

```sql
-- ── Households ──────────────────────────────────────────────────────────────
-- The ledger belongs to a household, not to a login: a family shares one set of
-- accounts and categories. Every user has exactly one household from registration on.
CREATE TABLE households (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    uuid             VARCHAR(36)                 NOT NULL UNIQUE,
    name             VARCHAR(255)                NOT NULL,
    -- The currency totals are reported in.
    base_currency_id BIGINT                      NULL REFERENCES currencies (id),
    created_at       TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE household_members (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    household_id BIGINT                      NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    user_id      BIGINT                      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role         VARCHAR(20)                 NOT NULL,
    joined_at    TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_household_member UNIQUE (household_id, user_id)
);

CREATE INDEX idx_household_member_user ON household_members (user_id);
```

3. `bank_connections`: replace the `user_id` line with

```sql
    household_id         BIGINT        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    -- Who connected it. Any member may import from or remove it.
    connected_by_user_id BIGINT        NULL REFERENCES users (id) ON DELETE SET NULL,
```

and its two indexes with

```sql
CREATE INDEX idx_bank_conn_household ON bank_connections (household_id);
CREATE INDEX idx_bank_conn_client    ON bank_connections (household_id, provider, external_client_id);
```

4. Rename `user_currency_settings` to `household_currency_settings`; replace its `user_id` line with `household_id BIGINT NOT NULL REFERENCES households (id) ON DELETE CASCADE,`, its unique constraint with `CONSTRAINT uq_hcs_household_currency UNIQUE (household_id, currency_id)`, and its index with `CREATE INDEX idx_hcs_sync ON household_currency_settings (household_id, updated_at);`.

5. `exchange_rates`: replace the `user_id` line with `household_id BIGINT NULL REFERENCES households (id) ON DELETE CASCADE,` and "user_id IS NULL" in the comment above it with "household_id IS NULL".

6. In `accounts`, `categories`, `subscriptions` and `transactions`, replace `user_id BIGINT NOT NULL REFERENCES users (id),` with `household_id BIGINT NOT NULL REFERENCES households (id),`, and in every index on those tables replace `user_id` with `household_id`.

7. In `transactions`, after `household_id`, add:

```sql
    -- Who wrote it. Null for rows the server writes itself (subscription charges) and
    -- after the author's login is deleted; the history stays with the household.
    created_by_user_id              BIGINT                      NULL REFERENCES users (id) ON DELETE SET NULL,
```

- [ ] **Step 5: Add the household entities, repositories and service**

`src/main/kotlin/com/homebudget/app/domain/Household.kt`:

```kotlin
package com.homebudget.app.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Whose ledger it is. Accounts, categories, subscriptions, transactions, currency
 * settings, manual rates and bank connections all belong to a household, and every login
 * in it sees and edits the same books.
 */
@Entity
@Table(name = "households")
data class Household(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36, updatable = false)
    val uuid: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    var name: String,

    /** The currency totals are reported in. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "base_currency_id")
    var baseCurrency: Currency? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
```

`src/main/kotlin/com/homebudget/app/domain/HouseholdMember.kt`:

```kotlin
package com.homebudget.app.domain

import jakarta.persistence.*
import java.time.Instant

enum class HouseholdRole { OWNER, MEMBER }

/** A login's place in a household. One per user for now; invitations add more. */
@Entity
@Table(name = "household_members")
data class HouseholdMember(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "household_id", nullable = false)
    val household: Household,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: HouseholdRole,

    @Column(name = "joined_at", nullable = false, updatable = false)
    val joinedAt: Instant = Instant.now()
)
```

`src/main/kotlin/com/homebudget/app/repository/HouseholdRepository.kt`:

```kotlin
package com.homebudget.app.repository

import com.homebudget.app.domain.Household
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface HouseholdRepository : JpaRepository<Household, Long> {

    /** The currencies totals are reported in, which is half of what the FX job quotes. */
    @Query("SELECT DISTINCT h.baseCurrency.code FROM Household h WHERE h.baseCurrency IS NOT NULL")
    fun baseCurrencyCodes(): List<String>
}
```

`src/main/kotlin/com/homebudget/app/repository/HouseholdMemberRepository.kt`:

```kotlin
package com.homebudget.app.repository

import com.homebudget.app.domain.HouseholdMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface HouseholdMemberRepository : JpaRepository<HouseholdMember, Long> {
    fun findFirstByUserId(userId: Long): HouseholdMember?
    fun countByHouseholdId(householdId: Long): Long
    fun findAllByHouseholdId(householdId: Long): List<HouseholdMember>
}
```

`src/main/kotlin/com/homebudget/app/service/HouseholdService.kt`:

```kotlin
package com.homebudget.app.service

import com.homebudget.app.domain.Currency
import com.homebudget.app.domain.Household
import com.homebudget.app.domain.HouseholdMember
import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.domain.User
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.HouseholdRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class HouseholdService(
    private val households: HouseholdRepository,
    private val members: HouseholdMemberRepository
) {
    /** Every user owns a household from the moment they exist, named after them. */
    fun createFor(owner: User, baseCurrency: Currency?): Household {
        val household = households.save(Household(name = owner.firstName, baseCurrency = baseCurrency))
        members.save(HouseholdMember(household = household, user = owner, role = HouseholdRole.OWNER))
        return household
    }

    @Transactional(readOnly = true)
    fun householdOf(user: User): Household =
        members.findFirstByUserId(user.id)?.household
            ?: throw IllegalStateException("User ${user.id} belongs to no household")
}
```

- [ ] **Step 6: Carry the household on the principal**

`security/UserPrincipal.kt` — change the class header to

```kotlin
/**
 * The signed-in login and the household whose ledger it works on, resolved once per
 * request when the token is checked.
 */
class UserPrincipal(val user: User, val household: Household) : UserDetails {
```

and import `com.homebudget.app.domain.Household`.

`security/CustomUserDetailsService.kt`:

```kotlin
@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository,
    private val householdService: HouseholdService
) : UserDetailsService {
    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UsernameNotFoundException("User not found with email: $email") }
        return UserPrincipal(user, householdService.householdOf(user))
    }
}
```

`security/SecurityUtils.kt`:

```kotlin
@Component
class SecurityUtils {
    fun getCurrentUser(): User = principal().user

    /** The household whose ledger this request reads and writes. */
    fun getCurrentHousehold(): Household = principal().household

    private fun principal(): UserPrincipal =
        SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
            ?: throw IllegalStateException("No authenticated user found")
}
```

`controller/AuthController.kt`: the refresh endpoint builds `UserPrincipal(user)` for `jwtUtils.generateToken`; inject `HouseholdService` and pass `UserPrincipal(user, householdService.householdOf(user))`. In `authenticateUser`, after `userPrincipal` is known, record the sign-in:

```kotlin
        userRepository.save(userPrincipal.user.apply { lastSignInAt = Instant.now() })
```

- [ ] **Step 7: Change the entities' owner**

`domain/User.kt`: delete `baseCurrency` (field with its `@ManyToOne`/`@JoinColumn`) and add

```kotlin
    /** Shown to the administrator. */
    @Column(name = "last_sign_in_at")
    var lastSignInAt: Instant? = null,
```

In `Account`, `Category`, `Subscription`, `Transaction`, `UserCurrencySetting` replace

```kotlin
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
```

with

```kotlin
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    var household: Household,
```

`UserCurrencySetting`: change `@Table(name = "user_currency_settings")` to `@Table(name = "household_currency_settings")` (the class keeps its name; renaming it is churn with no behaviour).

`Transaction`: after `household`, add

```kotlin
    /**
     * Who wrote it. Null for rows the server writes itself - subscription charges - and
     * after the author's login is deleted; the history stays with the household.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    var createdBy: User? = null,
```

`BankConnection`: replace the `user` field with

```kotlin
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    var household: Household,

    /** Who connected it. Any member may import from it or remove it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_by_user_id")
    var connectedBy: User? = null,
```

`ExchangeRate`: replace `user` with

```kotlin
    /** Null for a fetched rate; a household's own override otherwise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id")
    var household: Household? = null,
```

- [ ] **Step 8: Rewrite the ledger repositories**

Replace each interface body as follows (keep the existing KDoc on surviving methods; imports change from `User` to `Household`).

`AccountRepository`:

```kotlin
@Repository
interface AccountRepository : JpaRepository<Account, Long> {
    fun findAllByHousehold(household: Household): List<Account>
    fun findByIdAndHousehold(id: Long, household: Household): Account?
    fun findByUuidAndHousehold(uuid: String, household: Household): Account?

    @Query("SELECT DISTINCT a.currency.code FROM Account a")
    fun currencyCodesInUse(): List<String>

    fun findAllByHouseholdAndExternalAccountIdIsNotNull(household: Household): List<Account>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Account a SET a.bankConnection = null, a.externalAccountId = null WHERE a.bankConnection.id = :connectionId")
    fun unlinkConnection(connectionId: Long): Int

    @Query(
        """
        SELECT new com.homebudget.app.repository.AccountBalanceRow(
            a.id,
            a.initialBalanceMinor + COALESCE((
                SELECT SUM(CASE
                    WHEN t.type = com.homebudget.app.domain.TransactionType.INCOME  THEN  t.accountAmountMinor
                    WHEN t.type = com.homebudget.app.domain.TransactionType.EXPENSE THEN -t.accountAmountMinor
                    ELSE t.accountAmountMinor
                END)
                FROM Transaction t WHERE t.account = a
            ), 0L)
        )
        FROM Account a
        WHERE a.household = :household
        """
    )
    fun balancesOf(household: Household): List<AccountBalanceRow>

    @Query(
        value = "SELECT * FROM accounts WHERE household_id = :householdId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(householdId: Long, since: Instant, limit: Int): List<Account>

    @Query(value = "SELECT * FROM accounts WHERE uuid = :uuid AND household_id = :householdId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, householdId: Long): Account?
}
```

`CategoryRepository`:

```kotlin
@Repository
interface CategoryRepository : JpaRepository<Category, Long> {
    fun findAllByHousehold(household: Household): List<Category>
    fun findByHouseholdAndType(household: Household, type: CategoryType): List<Category>
    fun existsByNameAndTypeAndHousehold(name: String, type: CategoryType, household: Household): Boolean
    fun findByIdAndHousehold(id: Long, household: Household): Category?
    fun findByUuidAndHousehold(uuid: String, household: Household): Category?
    fun findAllByHouseholdAndExternalIdIsNotNull(household: Household): List<Category>

    @Query("SELECT DISTINCT c FROM Category c JOIN c.mccCodes m WHERE c.household = :household AND m = :mcc and c.type = :type")
    fun findByHouseholdAndMccCodeAndType(household: Household, mcc: Int, type: CategoryType): List<Category>

    @Query(
        value = "SELECT * FROM categories WHERE household_id = :householdId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(householdId: Long, since: Instant, limit: Int): List<Category>

    @Query(value = "SELECT * FROM categories WHERE uuid = :uuid AND household_id = :householdId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, householdId: Long): Category?
}
```

`TransactionRepository`: `findByIdAndHousehold(id: Long, household: Household)`, `findByUuidAndHousehold(uuid: String, household: Household)`, `findAllByTransferGroupIdAndHousehold(transferGroupId: String, household: Household)`; in `findFiltered` and `findTransferDebitLegs` the first parameter becomes `household: Household` and `WHERE t.user = :user` becomes `WHERE t.household = :household`; the two native queries read `household_id = :householdId` with parameter `householdId: Long`. `existsByAccountAndExternalId`, `findByAccountAndExternalId`, `countByCategoryId`, `tombstoneByAccount`, `detachCategory` are unchanged.

`SubscriptionRepository`: `findAllByHousehold`, `findByIdAndHousehold`, `findByUuidAndHousehold`; native queries on `household_id`; `countByCategoryId`, `tombstoneByAccount`, `findDue` unchanged.

`UserCurrencySettingRepository`: `findAllByHousehold(household: Household)`, `findByHouseholdAndCurrencyId(household: Household, currencyId: Long)`; native queries read `FROM household_currency_settings WHERE household_id = :householdId ...`; `activeCurrencyCodes` unchanged.

`ExchangeRateRepository`: `findByBaseCodeAndQuoteCodeAndRateDateAndHouseholdIsNull(...)`, `findByHouseholdAndBaseCodeAndQuoteCode(household: Household, baseCode: String, quoteCode: String)`, and `latestFor(household: Household)` with every `r.user`/`r2.user` in the JPQL replaced by `r.household`/`r2.household` and `:user` by `:household`. In `preferringOverrides` replace `it.user != null` with `it.household != null`.

`BankConnectionRepository`: `findAllByHousehold(household: Household)`, `findByIdAndHousehold(id: Long, household: Household)`.

`UserRepository`: delete `baseCurrencyCodes()` and `findTop50ByEmailContainingIgnoreCaseOrderByIdAsc` (its only caller was the deleted entitlement admin controller).

`SyncCursorRepository`: rename the function to `latestUpdatedAt(householdId: Long)`, replace every `WHERE user_id = :uid` with `WHERE household_id = :hid`, the table `user_currency_settings` with `household_currency_settings`, and `setParameter("uid", userId)` with `setParameter("hid", householdId)`; the KDoc says "a household has" instead of "a user has".

- [ ] **Step 9: Move the services, controllers and importers onto the household**

Compile (`./mvnw -B -q -Dfrontend.skip=true compile`) and fix every error with these rules:

| Before | After |
| --- | --- |
| `securityUtils.getCurrentUser()` used to scope ledger data | `securityUtils.getCurrentHousehold()` |
| `repo.findAllByUser(user)`, `findByIdAndUser(id, user)`, `findByUuidAndUser(uuid, user)`, any `...AndUser(...)` | the `...Household(...)` method with the household |
| `findChangedSince(user.id, …)`, `findByUuidIncludingDeleted(uuid, user.id)`, `cursorRepo.latestUpdatedAt(user.id)` | `household.id` |
| `Account(…, user = user, …)` (and `Category`, `Subscription`, `UserCurrencySetting`) | `household = household` |
| `Transaction(…, user = user, …)` written for a person (REST create, transfer, sync push, bank/CSV import, HomeMoney) | `household = household, createdBy = user` — the signed-in user, or the importing user in the HomeMoney runner |
| `Transaction(…)` written by `SubscriptionService.generateDueCharges` | `household = subscription.household, createdBy = null` |
| `BankConnection(user = …)` | `household = securityUtils.getCurrentHousehold(), connectedBy = securityUtils.getCurrentUser()` |
| `ExchangeRate(… user = user)` | `household = household` |
| `user.baseCurrency` | `household.baseCurrency` |
| `fxService.rate(user, …)`, `setOverride(user, …)`, `clearOverride(user, …)` | take `household: Household` |
| `userRepo.baseCurrencyCodes()` in `FxService` | `householdRepo.baseCurrencyCodes()` (inject `HouseholdRepository`) |
| `categoryService.seedDefaultCategories(user)` | `seedDefaultCategories(household: Household)` |
| a private helper taking `user: User` only to scope queries (e.g. `SyncService.applyAccounts(user, …)`) | take `household: Household`; helpers that write transactions also take `actor: User` |
| `HomeMoneyImportService`/`Runner`: `val user = requireUser(userId)` | keep it, then `val household = householdService.householdOf(user)` and scope by `household` |

Beyond the table:

- `UserService.updateBaseCurrency` changes the household and returns it (inject `HouseholdRepository`):

```kotlin
    fun updateBaseCurrency(currencyId: Long): Household {
        val household = householdRepository.findById(securityUtils.getCurrentHousehold().id).orElseThrow()
        household.baseCurrency = currencyService.findById(currencyId)
        household.updatedAt = Instant.now()
        return householdRepository.save(household)
    }
```

- `Mappers.kt`: `fun User.toResponse(household: Household)` sets `baseCurrency = household.baseCurrency?.toResponse()`. `UserController` (inject `SecurityUtils`):

```kotlin
    @GetMapping("/me")
    fun getCurrentUser() = userService.getCurrentUser().toResponse(securityUtils.getCurrentHousehold())

    @PutMapping("/me/base-currency")
    fun updateBaseCurrency(@RequestParam currencyId: Long) =
        userService.getCurrentUser().toResponse(userService.updateBaseCurrency(currencyId))
```

- `UserService` deletion becomes per user, and the household goes when its last member goes (inject `HouseholdMemberRepository`):

```kotlin
    fun deleteCurrentUser() = deleteUser(getCurrentUser())

    /**
     * Erases a login. The household and its whole ledger go with it when it was the
     * household's last member; otherwise the books stay with the others and the rows it
     * wrote lose their author (`ON DELETE SET NULL`).
     *
     * Native statements in child-before-parent order: soft deletes are updates, so the
     * FK cascades that would clean up after a real DELETE never fire for tombstoned
     * rows, and JPQL would not see those rows at all thanks to @SQLRestriction.
     */
    fun deleteUser(user: User) {
        val membership = memberRepository.findFirstByUserId(user.id)
        if (membership != null && memberRepository.countByHouseholdId(membership.household.id) == 1L) {
            deleteHousehold(membership.household.id)
        }
        listOf(
            "DELETE FROM household_members WHERE user_id = :id",
            "DELETE FROM refresh_tokens WHERE user_id = :id",
            "DELETE FROM auth_tokens WHERE user_id = :id",
            "DELETE FROM users WHERE id = :id"
        ).forEach { em.createNativeQuery(it).setParameter("id", user.id).executeUpdate() }
    }

    private fun deleteHousehold(householdId: Long) {
        listOf(
            "DELETE FROM category_mcc_codes WHERE category_id IN (SELECT id FROM categories WHERE household_id = :id)",
            "DELETE FROM transactions WHERE household_id = :id",
            "DELETE FROM subscriptions WHERE household_id = :id",
            // categories.parent_id points at categories; break the links before deleting.
            "UPDATE categories SET parent_id = NULL WHERE household_id = :id",
            "DELETE FROM categories WHERE household_id = :id",
            "DELETE FROM accounts WHERE household_id = :id",
            "DELETE FROM bank_connections WHERE household_id = :id",
            "DELETE FROM household_currency_settings WHERE household_id = :id",
            "DELETE FROM exchange_rates WHERE household_id = :id",
            "DELETE FROM household_members WHERE household_id = :id",
            "DELETE FROM households WHERE id = :id"
        ).forEach { em.createNativeQuery(it).setParameter("id", householdId).executeUpdate() }
    }
```

- `AuthController.registerUser`: drop `baseCurrency = baseCurrency` from the `User(...)` call, and after saving:

```kotlin
        val household = householdService.createFor(savedUser, baseCurrency)
        categoryService.seedDefaultCategories(household)
```

(Task 5 moves registration into `RegistrationService`.)

- `BankConnectionService`'s pending-consent map stays keyed by user id: a consent is completed by the person who started it.

- [ ] **Step 10: Move the existing tests onto `TestIdentity`**

For each file from the grep in **Files**: add `@Import(TestIdentity::class)` and `@Autowired private lateinit var identity: TestIdentity`; replace the local `newUser(...)`/`signIn(...)` helpers with `identity.newMember(prefix)` (returns a `UserPrincipal`; use `.user` and `.household` where needed) and `identity.signIn(principal)`; in hand-built ledger entities replace `user = someUser` with `household = principal.household` (plus `createdBy = principal.user` on `Transaction`). `NoAccountCapTest` from Task 1 changes the same way.

- [ ] **Step 11: Run the full suite**

Run: `./mvnw -B -Dfrontend.skip=true test`
Expected: PASS, including all seven `HouseholdIsolationTest` cases (`PostgresHouseholdIsolationTest` is skipped without Docker).

- [ ] **Step 12: Commit**

```bash
git add -A src/main src/test
git commit -m "Scope the ledger by household instead of by user"
```

---

### Task 4: Keep sync stamps strictly increasing within a household

Two logins can now push into the same ledger at once. `nextStamp` reads the newest `updated_at` and stamps one millisecond later; two concurrent pushes can read the same value and stamp the same instant, and a pull whose cursor falls between their commits then skips one of them for good. Serialising pushes per household with a row lock closes that.

**Files:**
- Modify: `src/main/kotlin/com/homebudget/app/repository/HouseholdRepository.kt`, `src/main/kotlin/com/homebudget/app/service/SyncService.kt` (`push`, `nextStamp`)
- Test: `src/test/kotlin/com/homebudget/app/service/ConcurrentPushTest.kt`

**Interfaces:**
- Consumes: `TestIdentity.newMember`, `TestIdentity.joinAs`, `HouseholdRepository` (Task 3).
- Produces: `HouseholdRepository.lockById(id: Long): Household?` (pessimistic write lock).

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/homebudget/app/service/ConcurrentPushTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run it to see it fail**

Run: `./mvnw -B -Dfrontend.skip=true test -Dtest=ConcurrentPushTest`
Expected: FAIL with "two pushes shared a stamp". It is a race, so run it up to three times; if it never fails on this machine, continue — the lock is required by the spec and the test guards against regressions.

- [ ] **Step 3: Add the lock**

`HouseholdRepository` — add (imports `jakarta.persistence.LockModeType`, `org.springframework.data.jpa.repository.Lock`):

```kotlin
    /**
     * Takes the household's row lock for the rest of the transaction.
     *
     * Sync pushes into one household go one at a time behind it, so each stamps strictly
     * after everything committed before it - see SyncService.nextStamp.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Household h WHERE h.id = :id")
    fun lockById(id: Long): Household?
```

`SyncService`: inject `private val householdRepo: HouseholdRepository`; at the top of `push`, after resolving the household and before `nextStamp`:

```kotlin
        // One push per household at a time: two members pushing together would otherwise
        // read the same latest stamp and write the same instant, and a pull whose cursor
        // fell between their commits would skip one of them for good.
        householdRepo.lockById(household.id)
```

Change `nextStamp`'s KDoc from "anything this user already has" to "anything this household already has".

- [ ] **Step 4: Run the sync tests**

Run: `./mvnw -B -Dfrontend.skip=true test -Dtest='ConcurrentPushTest,SyncServiceTest,HouseholdIsolationTest'`
Expected: PASS; run `ConcurrentPushTest` three times, it must pass every time.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/homebudget/app/repository/HouseholdRepository.kt src/main/kotlin/com/homebudget/app/service/SyncService.kt src/test/kotlin/com/homebudget/app/service/ConcurrentPushTest.kt
git commit -m "Serialise sync pushes per household so stamps never collide"
```

---

### Task 5: Instance endpoint, first-run administrator, registration modes, optional mail

**Files:**
- Create: `src/main/kotlin/com/homebudget/app/domain/InstanceSetting.kt`, `repository/InstanceSettingRepository.kt`, `service/MailAvailability.kt`, `service/RegistrationService.kt`, `instance/InstanceInfo.kt`, `instance/InstanceController.kt`
- Modify: `src/main/resources/db/migration/V1__baseline.sql`, `controller/AuthController.kt` (`registerUser`), `controller/GlobalExceptionHandler.kt`, `security/SecurityConfig.kt`, `service/MailService.kt` (base-url default), `src/main/resources/application.yml`, `src/main/resources/application-prod.yml` (mail block), `pom.xml` (build-info, `revision`)
- Test: `src/test/kotlin/com/homebudget/app/service/RegistrationPolicyTest.kt`, `src/test/kotlin/com/homebudget/app/instance/FirstRunTest.kt`, `src/test/kotlin/com/homebudget/app/instance/OpenRegistrationTest.kt`

**Interfaces:**
- Consumes: `HouseholdService.createFor`, `CategoryService.seedDefaultCategories(household)` (Task 3).
- Produces:
  - `object RegistrationPolicy { fun isOpen(environment: String, stored: String?, userCount: Long): Boolean; fun lockedByEnvironment(environment: String): Boolean }`
  - `RegistrationService.setupRequired(): Boolean`, `registrationOpen(): Boolean`, `registrationLockedByEnvironment(): Boolean`, `setRegistrationOpen(open: Boolean)`, `register(req: RegisterRequest): User`
  - `class RegistrationClosedException : RuntimeException` → HTTP 403
  - `MailAvailability.enabled: Boolean`
  - `GET /api/instance` → `InstanceInfo(name, version, apiVersion, setupRequired, registrationOpen, mailEnabled, bankProviders)`; `InstanceController.API_VERSION = 1`
  - table `instance_settings(key, value)`; key `registration` holds `open` | `closed`

- [ ] **Step 1: Write the policy unit test**

`src/test/kotlin/com/homebudget/app/service/RegistrationPolicyTest.kt`:

```kotlin
package com.homebudget.app.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationPolicyTest {

    @Test
    fun `unset means open until somebody exists`() {
        assertTrue(RegistrationPolicy.isOpen(environment = "", stored = null, userCount = 0))
        assertFalse(RegistrationPolicy.isOpen(environment = "", stored = null, userCount = 1))
    }

    @Test
    fun `an administrator's choice beats the default`() {
        assertTrue(RegistrationPolicy.isOpen(environment = "", stored = "open", userCount = 5))
        assertFalse(RegistrationPolicy.isOpen(environment = "", stored = "closed", userCount = 0))
    }

    @Test
    fun `the environment beats everything and locks the toggle`() {
        assertTrue(RegistrationPolicy.isOpen(environment = "open", stored = "closed", userCount = 5))
        assertFalse(RegistrationPolicy.isOpen(environment = " CLOSED ", stored = "open", userCount = 0))
        assertTrue(RegistrationPolicy.lockedByEnvironment("open"))
        assertFalse(RegistrationPolicy.lockedByEnvironment(""))
        assertFalse(RegistrationPolicy.lockedByEnvironment("sometimes"))
    }
}
```

- [ ] **Step 2: Write the first-run integration tests**

Each class runs on its own empty in-memory database (the suite's shared one already has users).

`src/test/kotlin/com/homebudget/app/instance/FirstRunTest.kt`:

```kotlin
package com.homebudget.app.instance

import com.homebudget.app.domain.Role
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.service.RegistrationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

/** A brand-new instance: the first account is the administrator, then the door closes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:first-run;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
])
class FirstRunTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var registration: RegistrationService

    private fun register(email: String) = mvc.perform(
        post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
            """{"email":"$email","password":"a-long-enough-password","firstName":"Ada","lastName":"Admin"}"""
        )
    )

    @Test
    fun `setup, first administrator, closed door, reopened by the administrator`() {
        mvc.perform(get("/api/instance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.setupRequired").value(true))
            .andExpect(jsonPath("$.registrationOpen").value(true))
            .andExpect(jsonPath("$.mailEnabled").value(false))
            .andExpect(jsonPath("$.apiVersion").value(1))
            .andExpect(jsonPath("$.bankProviders[?(@ == 'MONOBANK_CORPORATE')]").isEmpty)

        register("admin@example.com").andExpect(status().isOk)
        val admin = users.findByEmail("admin@example.com").orElseThrow()
        assertEquals(Role.ADMIN, admin.role)
        assertTrue(admin.emailVerified, "with no mail configured nobody can be asked to verify")

        mvc.perform(get("/api/instance"))
            .andExpect(jsonPath("$.setupRequired").value(false))
            .andExpect(jsonPath("$.registrationOpen").value(false))
        register("stranger@example.com").andExpect(status().isForbidden)

        registration.setRegistrationOpen(true)
        register("family@example.com").andExpect(status().isOk)
        assertEquals(Role.USER, users.findByEmail("family@example.com").orElseThrow().role)
    }
}
```

`src/test/kotlin/com/homebudget/app/instance/OpenRegistrationTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./mvnw -B -Dfrontend.skip=true test -Dtest='RegistrationPolicyTest,FirstRunTest,OpenRegistrationTest'`
Expected: FAIL — `RegistrationPolicy`/`RegistrationService` do not exist (compile error).

- [ ] **Step 4: Add the settings table and entity**

Append to `V1__baseline.sql`:

```sql
-- ── Instance settings ───────────────────────────────────────────────────────
-- What an administrator changed at runtime. An environment variable for the same
-- setting always wins; see RegistrationPolicy.
CREATE TABLE instance_settings (
    key   VARCHAR(100) PRIMARY KEY,
    value VARCHAR(500) NOT NULL
);
```

`domain/InstanceSetting.kt`:

```kotlin
package com.homebudget.app.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "instance_settings")
data class InstanceSetting(
    @Id
    @Column(name = "key", length = 100)
    val key: String,

    @Column(name = "value", nullable = false, length = 500)
    var value: String
)
```

`repository/InstanceSettingRepository.kt`:

```kotlin
package com.homebudget.app.repository

import com.homebudget.app.domain.InstanceSetting
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface InstanceSettingRepository : JpaRepository<InstanceSetting, String>
```

`key` and `value` are reserved words in H2; if H2 rejects the table, quote them in both the DDL (`"key"`, `"value"`) and the `@Column(name = "\"key\"")` mappings, or rename the columns to `setting_key` / `setting_value` in both places.

- [ ] **Step 5: Add mail availability and the registration service**

`service/MailAvailability.kt`:

```kotlin
package com.homebudget.app.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Whether this instance can send mail at all.
 *
 * A self-hosted instance often has no SMTP server. Without one, nobody is asked to verify
 * an address they cannot receive mail at, and the clients hide "forgot password" in
 * favour of an administrator-issued reset link. A link in a mail is useless without an
 * absolute address, so APP_BASE_URL is part of "configured".
 */
@Component
class MailAvailability(
    @Value("\${spring.mail.host:}") host: String,
    @Value("\${app.mail.log-only:false}") logOnly: Boolean,
    @Value("\${app.base-url:}") baseUrl: String
) {
    val enabled: Boolean = host.isNotBlank() && baseUrl.isNotBlank() && !logOnly
}
```

`service/RegistrationService.kt`:

```kotlin
package com.homebudget.app.service

import com.homebudget.app.domain.InstanceSetting
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.dto.RegisterRequest
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.InstanceSettingRepository
import com.homebudget.app.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Registration on this instance is closed. */
class RegistrationClosedException : RuntimeException(
    "Registration on this instance is closed. Ask its administrator for an account."
)

/**
 * Who may register, in order of authority: the `REGISTRATION` environment variable, then
 * the administrator's toggle, then the default - open only while the instance has nobody,
 * so the person who installed it can create the first account and nobody else can.
 */
object RegistrationPolicy {
    private val EXPLICIT = setOf("open", "closed")

    fun isOpen(environment: String, stored: String?, userCount: Long): Boolean {
        val env = environment.trim().lowercase()
        if (env in EXPLICIT) return env == "open"
        if (stored != null) return stored == "open"
        return userCount == 0L
    }

    fun lockedByEnvironment(environment: String): Boolean = environment.trim().lowercase() in EXPLICIT
}

@Service
class RegistrationService(
    private val users: UserRepository,
    private val settings: InstanceSettingRepository,
    private val currencies: CurrencyRepository,
    private val households: HouseholdService,
    private val categories: CategoryService,
    private val authFlow: AuthFlowService,
    private val mail: MailAvailability,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.instance.registration:}") private val registrationEnv: String
) {
    companion object {
        const val REGISTRATION_KEY = "registration"
    }

    fun setupRequired(): Boolean = users.count() == 0L

    fun registrationOpen(): Boolean = RegistrationPolicy.isOpen(
        environment = registrationEnv,
        stored = settings.findById(REGISTRATION_KEY).map { it.value }.orElse(null),
        userCount = users.count()
    )

    fun registrationLockedByEnvironment(): Boolean = RegistrationPolicy.lockedByEnvironment(registrationEnv)

    @Transactional
    fun setRegistrationOpen(open: Boolean) {
        check(!registrationLockedByEnvironment()) {
            "Registration is set by the REGISTRATION environment variable"
        }
        settings.save(InstanceSetting(REGISTRATION_KEY, if (open) "open" else "closed"))
    }

    /**
     * Creates the login, its household and the default categories in one transaction.
     *
     * The very first account is the instance's administrator: whoever installed it is the
     * only person who can reach a fresh instance before the door closes behind them.
     */
    @Transactional
    fun register(req: RegisterRequest): User {
        if (!registrationOpen()) throw RegistrationClosedException()
        require(!users.existsByEmail(req.email)) { "Email is already in use" }
        val first = setupRequired()
        val user = users.save(
            User(
                email = req.email,
                passwordHash = passwordEncoder.encode(req.password),
                firstName = req.firstName,
                lastName = req.lastName,
                role = if (first) Role.ADMIN else Role.USER,
                // Nobody can be asked to prove an address the instance cannot mail.
                emailVerified = first || !mail.enabled
            )
        )
        val baseCurrency = req.baseCurrencyId?.let { currencies.findById(it).orElse(null) }
            ?: currencies.findByCode("USD")
        categories.seedDefaultCategories(households.createFor(user, baseCurrency))
        if (mail.enabled && !user.emailVerified) authFlow.sendVerification(user)
        return user
    }
}
```

- [ ] **Step 6: Route registration through it**

`controller/AuthController.kt` — replace the body of `registerUser` with:

```kotlin
        registrationService.register(registerRequest)
        return ResponseEntity.ok(mapOf("message" to "User registered successfully!"))
```

inject `RegistrationService` and drop constructor parameters that are now unused (`currencyRepository`, `categoryService`, `householdService` if only `registerUser` used them; keep what `refresh` and `authenticateUser` need).

`controller/GlobalExceptionHandler.kt` — add:

```kotlin
    /** The instance is not taking new accounts; only its administrator can change that. */
    @ExceptionHandler(com.homebudget.app.service.RegistrationClosedException::class)
    fun handleRegistrationClosed(ex: com.homebudget.app.service.RegistrationClosedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(status = 403, error = "Registration Closed", message = ex.message)
        )
```

Check that `IllegalArgumentException` already maps to 400 in this handler (the duplicate-email `require`); if not, the old `"Error: Email is already in use!"` 400 must be preserved by catching it in `registerUser`.

- [ ] **Step 7: Add the instance endpoint**

`instance/InstanceInfo.kt`:

```kotlin
package com.homebudget.app.instance

/** What a client needs to know about an instance before anyone signs in. */
data class InstanceInfo(
    val name: String,
    val version: String,
    /** Bumped on a breaking API change; clients compare it with the range they support. */
    val apiVersion: Int,
    /** Nobody has an account yet: the next registration creates the administrator. */
    val setupRequired: Boolean,
    val registrationOpen: Boolean,
    /** False hides "forgot password"; an administrator issues reset links instead. */
    val mailEnabled: Boolean,
    val bankProviders: List<String>
)
```

`instance/InstanceController.kt`:

```kotlin
package com.homebudget.app.instance

import com.homebudget.app.bank.BankConnectionService
import com.homebudget.app.service.MailAvailability
import com.homebudget.app.service.RegistrationService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/instance")
class InstanceController(
    private val registration: RegistrationService,
    private val mail: MailAvailability,
    private val banks: BankConnectionService,
    private val build: ObjectProvider<BuildProperties>
) {
    companion object {
        const val API_VERSION = 1
    }

    @GetMapping
    fun get() = InstanceInfo(
        name = "PersonalMoney",
        version = build.ifAvailable?.version ?: "dev",
        apiVersion = API_VERSION,
        setupRequired = registration.setupRequired(),
        registrationOpen = registration.registrationOpen(),
        mailEnabled = mail.enabled,
        // Bank providers are beans that exist only when configured (Monobank corporate needs
        // a registered key); CSV files and the HomeMoney import need nothing.
        bankProviders = banks.availableProviders().map { it.id } + listOf("CSV", "HOMEMONEY")
    )
}
```

`security/SecurityConfig.kt` — before `.requestMatchers("/api/**").authenticated()` add:

```kotlin
                    // Read before anyone signs in: whether to show setup, register and
                    // "forgot password", and whether this is a PersonalMoney server at all.
                    .requestMatchers("/api/instance").permitAll()
```

- [ ] **Step 8: Configuration, version and mail defaults**

`src/main/resources/application.yml`, under `app:` add:

```yaml
  # open | closed. Unset: open while the instance has no users, closed afterwards.
  # An administrator can flip it at runtime unless this is set.
  instance:
    registration: ${REGISTRATION:}
```

`src/main/resources/application-prod.yml` — the mail block becomes optional:

```yaml
  mail:
    host: ${MAIL_HOST:}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
```

and under `app:` set `base-url: ${APP_BASE_URL:}` and `mail.from: ${MAIL_FROM:noreply@localhost}`.

`service/MailService.kt`: change the `@Value` defaults to `${app.mail.from:noreply@localhost}` and `${app.base-url:}` (no personalmoney.app anywhere).

`pom.xml`:
- change `<version>0.0.1-SNAPSHOT</version>` to `<version>${revision}</version>` and add `<revision>0.0.0-dev</revision>` to `<properties>` (the release build passes `-Drevision=X.Y.Z`);
- in `spring-boot-maven-plugin` add

```xml
                <executions>
                    <execution>
                        <goals>
                            <goal>build-info</goal>
                        </goals>
                    </execution>
                </executions>
```

- [ ] **Step 9: Run the tests**

Run: `./mvnw -B -Dfrontend.skip=true test`
Expected: PASS, including the three new classes. If other tests registered users through `/api/auth/register` on the shared database, they now get 403 once a user exists; give such a test its own `@TestPropertySource(properties = ["app.instance.registration=open"])`.

- [ ] **Step 10: Commit**

```bash
git add -A src/main src/test pom.xml
git commit -m "Describe the instance at /api/instance and make the first account its administrator"
```

---

### Task 6: Generate missing secrets on first boot

**Files:**
- Create: `src/main/kotlin/com/homebudget/app/config/SecretsEnvironmentPostProcessor.kt`, `src/main/resources/META-INF/spring.factories`
- Modify: `src/main/resources/application.yml` (`app.data-dir`), `src/main/resources/application-local.yml` (`app.data-dir`), `src/main/resources/application-prod.yml` (`app.jwt.secret`, `app.crypto.secret`), `security/JwtUtils.kt:15` (no built-in default secret)
- Test: `src/test/kotlin/com/homebudget/app/config/SecretsEnvironmentPostProcessorTest.kt`

**Interfaces:**
- Produces: properties `app.jwt.secret` and `app.crypto.secret` always non-blank at startup; file `${app.data-dir}/secrets` (Java properties format, keys are the property names).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.homebudget.app.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.SpringApplication
import org.springframework.boot.logging.DeferredLogs
import org.springframework.mock.env.MockEnvironment
import java.nio.file.Files
import java.nio.file.Path

class SecretsEnvironmentPostProcessorTest {

    @TempDir lateinit var dataDir: Path

    private fun boot(vararg properties: Pair<String, String>): MockEnvironment {
        val env = MockEnvironment().withProperty("app.data-dir", dataDir.toString())
        properties.forEach { (k, v) -> env.setProperty(k, v) }
        SecretsEnvironmentPostProcessor(DeferredLogs()).postProcessEnvironment(env, SpringApplication())
        return env
    }

    @Test
    fun `generates both secrets when none are configured and remembers them`() {
        val first = boot("app.jwt.secret" to "", "app.crypto.secret" to "")
        val jwt = first.getProperty("app.jwt.secret")!!
        val crypto = first.getProperty("app.crypto.secret")!!
        assertTrue(jwt.length >= 32)
        assertNotEquals(jwt, crypto)
        assertTrue(Files.exists(dataDir.resolve("secrets")))

        val second = boot("app.jwt.secret" to "", "app.crypto.secret" to "")
        assertEquals(jwt, second.getProperty("app.jwt.secret"), "a restart must not sign everyone out")
        assertEquals(crypto, second.getProperty("app.crypto.secret"), "a restart must not lose bank credentials")
    }

    @Test
    fun `a configured value always wins`() {
        val env = boot("app.jwt.secret" to "set-by-the-operator-and-long-enough-for-hs256", "app.crypto.secret" to "")
        assertEquals("set-by-the-operator-and-long-enough-for-hs256", env.getProperty("app.jwt.secret"))
        assertTrue(env.getProperty("app.crypto.secret")!!.isNotBlank())
    }

    @Test
    fun `nothing is written when everything is configured`() {
        boot("app.jwt.secret" to "a".repeat(40), "app.crypto.secret" to "b".repeat(40))
        assertTrue(Files.notExists(dataDir.resolve("secrets")))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -B -Dfrontend.skip=true test -Dtest=SecretsEnvironmentPostProcessorTest`
Expected: FAIL to compile — the class does not exist.

- [ ] **Step 3: Implement the post-processor**

`src/main/kotlin/com/homebudget/app/config/SecretsEnvironmentPostProcessor.kt`:

```kotlin
package com.homebudget.app.config

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.boot.logging.DeferredLogFactory
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertiesPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties

/**
 * Fills in the signing and storage secrets an operator did not set.
 *
 * A self-hoster should get a working instance from `docker compose up` with nothing but a
 * database password. Both secrets are generated once into `<data dir>/secrets` and read
 * back on every later start, so a restart neither signs everyone out (JWT) nor makes
 * stored bank credentials unreadable (crypto). A value from the environment always wins.
 */
class SecretsEnvironmentPostProcessor(logFactory: DeferredLogFactory) : EnvironmentPostProcessor {

    private val log = logFactory.getLog(javaClass)

    companion object {
        val SECRETS = listOf("app.jwt.secret", "app.crypto.secret")
        private const val FILE_NAME = "secrets"
        private val random = SecureRandom()

        private fun newSecret(): String =
            Base64.getEncoder().encodeToString(ByteArray(32).also(random::nextBytes))
    }

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val missing = SECRETS.filter { environment.getProperty(it).isNullOrBlank() }
        if (missing.isEmpty()) return

        val file = Path.of(environment.getProperty("app.data-dir") ?: "/data").resolve(FILE_NAME)
        val stored = Properties()
        if (Files.exists(file)) Files.newBufferedReader(file).use(stored::load)

        val generated = missing.filter { stored.getProperty(it).isNullOrBlank() }
        if (generated.isNotEmpty()) {
            generated.forEach { stored.setProperty(it, newSecret()) }
            Files.createDirectories(file.parent)
            Files.newBufferedWriter(file).use {
                stored.store(it, "Generated by PersonalMoney. Back this file up: losing it signs everyone out and makes stored bank credentials unreadable.")
            }
            runCatching { Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------")) }
            log.warn("Generated $generated into $file")
        }

        // First, so these beat the blank values the environment resolved to.
        environment.propertySources.addFirst(
            PropertiesPropertySource("generatedSecrets", Properties().apply {
                missing.forEach { setProperty(it, stored.getProperty(it)) }
            })
        )
    }
}
```

`src/main/resources/META-INF/spring.factories`:

```
org.springframework.boot.env.EnvironmentPostProcessor=com.homebudget.app.config.SecretsEnvironmentPostProcessor
```

- [ ] **Step 4: Wire the configuration**

- `application.yml`, under `app:` add `data-dir: ${DATA_DIR:/data}`.
- `application-local.yml`, under `app:` add `data-dir: ./data` (the local profile also sets both secrets, so nothing is generated in development).
- `application-prod.yml`: `jwt.secret: ${JWT_SECRET:}` and `crypto.secret: ${CRYPTO_SECRET:}`.
- `security/JwtUtils.kt:15`: change `@Value("\${app.jwt.secret:defaultSecretKeyWithAtLeast32CharactersLongForHS256}")` to `@Value("\${app.jwt.secret}")` — a hard-coded fallback secret is a forged-token vulnerability, and the post-processor guarantees a value.

- [ ] **Step 5: Run the tests**

Run: `./mvnw -B -Dfrontend.skip=true test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A src/main src/test
git commit -m "Generate the signing and storage secrets on first boot when none are set"
```

---

### Task 7: Administration API and the reset-password command

**Files:**
- Create: `src/main/kotlin/com/homebudget/app/admin/AdminDtos.kt`, `admin/AdminController.kt`, `admin/ResetPasswordCommand.kt`, `docker/personalmoney`
- Modify: `service/AuthFlowService.kt` (admin reset link), `repository/UserRepository.kt` (ordered list)
- Test: `src/test/kotlin/com/homebudget/app/admin/AdminControllerTest.kt`, `src/test/kotlin/com/homebudget/app/admin/ResetPasswordCommandTest.kt`

**Interfaces:**
- Consumes: `RegistrationService.registrationOpen/registrationLockedByEnvironment/setRegistrationOpen` (Task 5), `UserService.deleteUser` and `HouseholdMemberRepository.findFirstByUserId` (Task 3), `TestIdentity` (Task 3).
- Produces (the web admin page in Task 11 calls these):
  - `GET /api/admin/users` → `List<AdminUserResponse(id: Long, email: String, firstName: String, lastName: String, role: Role, createdAt: LocalDateTime, lastSignInAt: Instant?, householdId: Long?)>`
  - `GET /api/admin/registration` → `RegistrationSetting(open: Boolean, lockedByEnvironment: Boolean)`
  - `PUT /api/admin/registration` body `{"open": Boolean}` → `RegistrationSetting`; 409 when locked by the environment
  - `POST /api/admin/users/{id}/reset-link` → `ResetLinkResponse(path: String, expiresAt: Instant)` where `path` is `/reset-password?token=…`
  - `DELETE /api/admin/users/{id}` → 204; 400 for the caller's own account
  - `AuthFlowService.issueAdminResetLink(user: User): ResetLinkResponse` (24-hour token)
  - `ResetPasswordCommand.linkFor(email: String): String?`

- [ ] **Step 1: Write the failing tests**

`src/test/kotlin/com/homebudget/app/admin/AdminControllerTest.kt`:

```kotlin
package com.homebudget.app.admin

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.Role
import com.homebudget.app.repository.InstanceSettingRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.AuthFlowService
import com.homebudget.app.service.RegistrationService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
class AdminControllerTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var settings: InstanceSettingRepository
    @Autowired private lateinit var authFlow: AuthFlowService
    @Autowired private lateinit var objectMapper: ObjectMapper

    private lateinit var admin: UserPrincipal
    private lateinit var member: UserPrincipal

    @BeforeEach
    fun setUp() {
        val made = identity.newMember("admin")
        users.save(made.user.apply { role = Role.ADMIN })
        admin = UserPrincipal(made.user, made.household)
        member = identity.newMember("member")
    }

    @AfterEach
    fun tearDown() = settings.deleteById(RegistrationService.REGISTRATION_KEY).let { }

    private fun asUser(p: UserPrincipal) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

    @Test
    fun `only an administrator reaches the admin API`() {
        mvc.perform(get("/api/admin/users").with(asUser(member))).andExpect(status().isForbidden)
        mvc.perform(get("/api/admin/users").with(asUser(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.email == '${member.user.email}')].householdId").value(member.household.id.toInt()))
    }

    @Test
    fun `an administrator opens and closes registration`() {
        mvc.perform(put("/api/admin/registration").with(asUser(admin))
            .contentType(MediaType.APPLICATION_JSON).content("""{"open":true}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.open").value(true))
            .andExpect(jsonPath("$.lockedByEnvironment").value(false))
        mvc.perform(get("/api/admin/registration").with(asUser(admin)))
            .andExpect(jsonPath("$.open").value(true))
    }

    @Test
    fun `a reset link works once`() {
        val body = mvc.perform(post("/api/admin/users/${member.user.id}/reset-link").with(asUser(admin)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val path = objectMapper.readTree(body)["path"].asText()
        val token = path.substringAfter("token=")

        authFlow.resetPassword(token, "a-brand-new-password")
        assertThrows<RuntimeException> { authFlow.resetPassword(token, "another-new-password") }
    }

    @Test
    fun `an administrator deletes a user but not themselves`() {
        mvc.perform(delete("/api/admin/users/${member.user.id}").with(asUser(admin))).andExpect(status().isNoContent)
        assertFalse(users.existsById(member.user.id))
        mvc.perform(delete("/api/admin/users/${admin.user.id}").with(asUser(admin))).andExpect(status().isBadRequest)
    }
}
```

(If `Role` is a `val` on `User`, the `apply { role = ... }` must become a copy; it is a `var` today.)

`src/test/kotlin/com/homebudget/app/admin/ResetPasswordCommandTest.kt`:

```kotlin
package com.homebudget.app.admin

import com.homebudget.app.TestIdentity
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
class ResetPasswordCommandTest {

    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var command: ResetPasswordCommand

    @Test
    fun `prints a reset link for a known address and nothing for an unknown one`() {
        val user = identity.newMember("locked-out").user
        val link = command.linkFor(user.email)
        assertTrue(link!!.contains("/reset-password?token="))
        assertNull(command.linkFor("nobody@example.com"))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -B -Dfrontend.skip=true test -Dtest='AdminControllerTest,ResetPasswordCommandTest'`
Expected: FAIL to compile — the admin package does not exist.

- [ ] **Step 3: Issue reset links without mail**

`service/AuthFlowService.kt` — in the companion add `val ADMIN_RESET_TTL: Duration = Duration.ofHours(24)` and add:

```kotlin
    /**
     * A reset link handed to an administrator instead of mailed.
     *
     * Many self-hosted instances have no mail at all, so the person who runs the instance
     * passes the link on themselves. Same token, same single use; a longer life because a
     * human carries it across.
     */
    @Transactional
    fun issueAdminResetLink(user: User): ResetLinkResponse {
        val raw = issue(user, AuthTokenPurpose.PASSWORD_RESET, ADMIN_RESET_TTL)
        return ResetLinkResponse(
            path = "/reset-password?token=$raw",
            expiresAt = Instant.now(clock).plus(ADMIN_RESET_TTL)
        )
    }
```

(import `com.homebudget.app.admin.ResetLinkResponse`).

- [ ] **Step 4: Add the DTOs and controller**

`admin/AdminDtos.kt`:

```kotlin
package com.homebudget.app.admin

import com.homebudget.app.domain.Role
import java.time.Instant
import java.time.LocalDateTime

data class AdminUserResponse(
    val id: Long,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: Role,
    val createdAt: LocalDateTime,
    val lastSignInAt: Instant?,
    val householdId: Long?
)

data class RegistrationSetting(val open: Boolean, val lockedByEnvironment: Boolean)

data class SetRegistrationRequest(val open: Boolean)

data class ResetLinkResponse(val path: String, val expiresAt: Instant)
```

`repository/UserRepository.kt` — add `fun findAllByOrderByCreatedAtAsc(): List<User>`.

`admin/AdminController.kt`:

```kotlin
package com.homebudget.app.admin

import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.SecurityUtils
import com.homebudget.app.service.AuthFlowService
import com.homebudget.app.service.RegistrationService
import com.homebudget.app.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

/** What the person who runs the instance can do that its other users cannot. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val users: UserRepository,
    private val members: HouseholdMemberRepository,
    private val registration: RegistrationService,
    private val authFlow: AuthFlowService,
    private val userService: UserService,
    private val securityUtils: SecurityUtils
) {
    @GetMapping("/users")
    @Transactional(readOnly = true)
    fun users(): List<AdminUserResponse> = users.findAllByOrderByCreatedAtAsc().map {
        AdminUserResponse(
            id = it.id,
            email = it.email,
            firstName = it.firstName,
            lastName = it.lastName,
            role = it.role,
            createdAt = it.createdAt,
            lastSignInAt = it.lastSignInAt,
            householdId = members.findFirstByUserId(it.id)?.household?.id
        )
    }

    @GetMapping("/registration")
    fun registration() = RegistrationSetting(
        open = registration.registrationOpen(),
        lockedByEnvironment = registration.registrationLockedByEnvironment()
    )

    @PutMapping("/registration")
    fun setRegistration(@RequestBody req: SetRegistrationRequest): ResponseEntity<Any> {
        if (registration.registrationLockedByEnvironment()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Registration is set by the REGISTRATION environment variable."))
        }
        registration.setRegistrationOpen(req.open)
        return ResponseEntity.ok(registration())
    }

    @PostMapping("/users/{id}/reset-link")
    fun resetLink(@PathVariable id: Long): ResetLinkResponse =
        authFlow.issueAdminResetLink(users.findById(id).orElseThrow { NoSuchElementException("User not found: $id") })

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun delete(@PathVariable id: Long) {
        require(id != securityUtils.getCurrentUser().id) {
            "Delete your own account from Settings, not from the user list"
        }
        userService.deleteUser(users.findById(id).orElseThrow { NoSuchElementException("User not found: $id") })
    }
}
```

Check `GlobalExceptionHandler` maps `NoSuchElementException` to 404 and `IllegalArgumentException` to 400 (the existing REST controllers rely on both).

- [ ] **Step 5: Add the command and its wrapper**

`admin/ResetPasswordCommand.kt`:

```kotlin
package com.homebudget.app.admin

import com.homebudget.app.repository.UserRepository
import com.homebudget.app.service.AuthFlowService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

/**
 * `personalmoney reset-password <email>` inside the container: the way back in for an
 * administrator who has lost their own password on an instance with no mail.
 *
 * Runs as a second, non-web start of the same jar against the same database, prints the
 * link and exits. Does nothing on a normal start.
 */
@Component
class ResetPasswordCommand(
    private val users: UserRepository,
    private val authFlow: AuthFlowService,
    private val context: ConfigurableApplicationContext,
    @Value("\${app.base-url:}") private val baseUrl: String
) : ApplicationRunner {

    fun linkFor(email: String): String? {
        val user = users.findByEmail(email.trim()).orElse(null) ?: return null
        val link = authFlow.issueAdminResetLink(user)
        return if (baseUrl.isBlank()) link.path else baseUrl.trimEnd('/') + link.path
    }

    override fun run(args: ApplicationArguments) {
        val email = args.getOptionValues("reset-password")?.firstOrNull() ?: return
        val link = linkFor(email)
        val code = if (link == null) {
            System.err.println("No account uses the address $email")
            1
        } else {
            println("Password reset link for $email, valid for 24 hours and usable once:")
            println(link)
            if (baseUrl.isBlank()) println("(Prefix it with the address you open PersonalMoney at.)")
            0
        }
        exitProcess(SpringApplication.exit(context, { code }))
    }
}
```

`docker/personalmoney` (make it executable: `git update-index --chmod=+x docker/personalmoney` after adding):

```sh
#!/bin/sh
# Administrative commands inside the container:
#   docker compose exec app personalmoney reset-password <email>
set -e
usage() { echo "usage: personalmoney reset-password <email>" >&2; exit 2; }
case "$1" in
  reset-password)
    [ -n "$2" ] || usage
    exec java -jar /app/app.jar \
      --spring.main.web-application-type=none \
      --spring.main.banner-mode=off \
      --logging.level.root=WARN \
      --reset-password="$2"
    ;;
  *) usage ;;
esac
```

- [ ] **Step 6: Run the tests**

Run: `./mvnw -B -Dfrontend.skip=true test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A src/main src/test docker
git update-index --chmod=+x docker/personalmoney
git commit -m "Let the administrator manage users, registration and reset links"
```

---

### Task 8: Package the instance — health, image, compose, environment

**Files:**
- Modify: `pom.xml` (actuator), `src/main/resources/application.yml` (management), `Dockerfile`, `.gitignore`
- Move: `docker-compose.yml` → `deploy/docker-compose.site.yml` (rewritten for the static site in Task 15), `.env.example` → replaced
- Create: `docker-compose.yml` (self-hoster's), `.env.example` (self-hoster's), `.dockerignore`
- Test: `src/test/kotlin/com/homebudget/app/config/HealthEndpointTest.kt`

**Interfaces:**
- Consumes: `docker/personalmoney` (Task 7), `app.data-dir` (Task 6), `revision` property (Task 5).
- Produces: image layout `/app/app.jar`, `/usr/local/bin/personalmoney`, `/data` volume; `GET /actuator/health` → `{"status":"UP"}`.

- [ ] **Step 1: Write the failing health test**

```kotlin
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
```

Run: `./mvnw -B -Dfrontend.skip=true test -Dtest=HealthEndpointTest`
Expected: FAIL (404 on `/actuator/health`; the SPA fallback may answer instead — any non-200-JSON result is a failure).

- [ ] **Step 2: Add the actuator**

`pom.xml`:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
```

`application.yml` (top level):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

If `SpaFallbackController` swallows `/actuator/**`, exclude that prefix there. Run the test again — Expected: PASS.

- [ ] **Step 3: Rewrite the Dockerfile**

```dockerfile
# Build once on the build machine's own architecture: the jar is platform-independent,
# so emulating arm64 for Maven and Node would only make the build slower.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
ARG APP_VERSION=0.0.0-dev
COPY pom.xml .
COPY .mvn .mvn
COPY src ./src
# The React app is built by frontend-maven-plugin during `package`.
RUN mvn -B -Drevision=${APP_VERSION} -DskipTests clean package

FROM eclipse-temurin:25-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home-dir /data personalmoney \
 && mkdir -p /data /app \
 && chown personalmoney /data
COPY --from=build /src/target/homebudgeting-*.jar /app/app.jar
COPY docker/personalmoney /usr/local/bin/personalmoney
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    DATA_DIR=/data
USER personalmoney
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

`.dockerignore`:

```
target
client
site
src/main/frontend/node_modules
src/main/frontend/build
web-dist
data
postgres
.git
```

- [ ] **Step 4: Write the self-hoster's compose file and environment**

```bash
git mv docker-compose.yml deploy/docker-compose.site.yml
```

New `docker-compose.yml`:

```yaml
# PersonalMoney - self-hosted personal and family finances.
#
#   cp .env.example .env      # set DB_PASSWORD, nothing else is required
#   docker compose up -d
#   open http://<this machine>:8080 and create the administrator account
#
# Put it behind your own reverse proxy (Caddy, Traefik, Nginx Proxy Manager, Tailscale)
# for HTTPS; see the README.
services:
  app:
    image: ghcr.io/htearih/personalmoney:${PERSONALMONEY_VERSION:-latest}
    restart: unless-stopped
    ports:
      - "${PERSONALMONEY_PORT:-8080}:8080"
    env_file: .env
    environment:
      DB_HOST: db
    volumes:
      # Generated secrets live here (and, later, receipt images). Back it up.
      - ./data:/data
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:17
    restart: unless-stopped
    environment:
      POSTGRES_DB: personalmoney
      POSTGRES_USER: personalmoney
      POSTGRES_PASSWORD: ${DB_PASSWORD:?Set DB_PASSWORD in .env}
    volumes:
      - ./postgres:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U personalmoney -d personalmoney"]
      interval: 10s
      timeout: 5s
      retries: 5
```

New `.env.example`:

```bash
# PersonalMoney configuration. Copy to .env next to docker-compose.yml.

# --- Required ---------------------------------------------------------------
# The database password. Pick a long random one; you never type it anywhere else.
#   openssl rand -base64 24
DB_PASSWORD=

# --- Optional ---------------------------------------------------------------
# Image tag and published port.
# PERSONALMONEY_VERSION=latest
# PERSONALMONEY_PORT=8080

# Who may create an account: open | closed. Unset: open until the first account
# (which becomes the administrator) exists, then closed. The administrator can
# toggle it in the app unless it is set here.
# REGISTRATION=

# Secrets. Generated into ./data/secrets on first start when unset; set them here
# only to manage them yourself. Changing JWT_SECRET signs everyone out; changing
# CRYPTO_SECRET makes stored bank connections unreadable.
# JWT_SECRET=
# CRYPTO_SECRET=

# The address people open PersonalMoney at. Needed for links in emails.
# APP_BASE_URL=https://money.example.com

# --- Mail (optional) --------------------------------------------------------
# Without mail nobody is asked to verify an address, and "forgot password" is
# replaced by reset links the administrator issues. Any SMTP provider works.
# MAIL_HOST=smtp.example.com
# MAIL_PORT=587
# MAIL_USERNAME=
# MAIL_PASSWORD=
# MAIL_FROM=personalmoney@example.com

# --- Banks (optional) -------------------------------------------------------
# Monobank with a personal token always works. The consent-based corporate API
# needs a company registration with the bank; see docs/monobank-corporate.md.
# MONOBANK_KEY_ID=
# MONOBANK_PRIVATE_KEY=

# HomeMoney import uses HomeMoney's public demo client unless you register your own.
# HOMEMONEY_CLIENT_ID=
# HOMEMONEY_CLIENT_SECRET=
```

Remove `deploy/init-env.sh` if it only generated the old SaaS `.env` (read it first; keep it only if it still applies to the site deploy). Add `postgres/` to `.gitignore` (`data/` is already ignored; check).

- [ ] **Step 5: Smoke-test the image**

If Docker is available:

```bash
docker build -t personalmoney:dev .
```

Expected: build succeeds. Then in a scratch directory with the new `docker-compose.yml`, a `.env` containing only `DB_PASSWORD=test-password`, and `image:` pointed at `personalmoney:dev`:

```bash
docker compose up -d
```

Expected: within a minute `docker compose ps` shows `app` healthy; `curl -s localhost:8080/api/instance` returns `"setupRequired":true`; `ls data/` shows `secrets`; `docker compose exec app personalmoney reset-password nobody@example.com` prints "No account uses the address" and exits 1. Tear down with `docker compose down -v`.

If Docker is not available, say so in the task report; CI's release workflow (Task 17) builds the image.

- [ ] **Step 6: Run the server suite and commit**

Run: `./mvnw -B -Dfrontend.skip=true test`
Expected: PASS.

```bash
git add -A pom.xml src/main/resources src/test Dockerfile .dockerignore docker-compose.yml .env.example deploy .gitignore
git commit -m "Package the instance: health check, multi-arch-ready image, compose file and environment"
```

---

# Phase B — Web (`src/main/frontend`)

All paths in this phase are relative to `src/main/frontend/`. Run `npm test` and `npm run typecheck` from there.

### Task 9: Remove premium and the landing page from the web app

**Files:**
- Delete: `src/landing/Landing.tsx`, `src/app/features/premium/` (whole directory)
- Modify: `src/App.tsx`, `src/App.test.tsx`, `src/landing/session.ts` (drop `hasSession`), `src/app/AppRoot.tsx:28,106`, `src/app/lib/routes.ts:22,59`, `src/app/shell/AccountMenu.tsx:3,68-70`, `src/app/components/ErrorBanner.tsx`, `src/app/components/components.test.tsx:87-91`, `src/app/api/http.ts:12,23-26`, `src/app/api/errors.test.ts:41-44`, `src/app/api/endpoints.ts:128-130`, `src/app/api/queryClient.ts:25`, `src/app/api/types.ts:442-450`, `src/i18n/locales/{app.en,app.ru,app.uk}.ts` (the 26 `premium_*` keys), `src/i18n/locales/{en,ru,uk}.ts` (the `landing.*` block)

**Interfaces:**
- Produces: `/` redirects to `/app` (`AppRoutes.dashboard`); no `AppRoutes.premium`, no `ApiError.isPlanLimit`, no `BillingStatusDto`.

- [ ] **Step 1: Replace the landing test with a redirect test**

In `src/App.test.tsx` replace the test "shows the landing page to a visitor with no token" with:

```tsx
it('sends a visitor at / to sign-in, since there is no landing page in the app any more', async () => {
    render(<App />);
    expect(await screen.findByText(en['login.title'])).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
});
```

Keep the language test. Fix the imports (`screen` from `@testing-library/react`, `en` from `./i18n/locales/en` as the file already does).

- [ ] **Step 2: Run it to verify it fails**

Run: `npm test -- src/App.test.tsx`
Expected: FAIL — `/` still renders the landing page.

- [ ] **Step 3: Point `/` at the app**

`src/App.tsx`: delete `import Landing from './landing/Landing';` and change `Root` to:

```tsx
const Root: React.FC = () => {
    const { pathname } = useLocation();
    // There is no marketing page in a self-hosted instance: the address is the app.
    if (pathname === '/') return <Navigate to={AppRoutes.dashboard} replace />;
    const target = LEGACY_ROUTES[pathname];
    return <Navigate to={target ?? AppRoutes.dashboard} replace />;
};
```

Update the comment above the lazy `AppRoot` to "Lazy, so the sign-in pages load without the application." Then:

```bash
git rm src/landing/Landing.tsx
git rm -r src/app/features/premium
```

Delete `hasSession` from `src/landing/session.ts` (only Landing used it).

- [ ] **Step 4: Remove every premium entry point**

- `src/app/AppRoot.tsx`: delete the `PremiumPage` lazy import (L28) and `<Route path="premium" …/>` (L106).
- `src/app/lib/routes.ts`: delete `premium: \`${APP}/premium\`,` and the `['premium', () => AppRoutes.premium],` legacy entry.
- `src/app/shell/AccountMenu.tsx`: delete the Premium `<button>` (L68-70) and `Crown` from the lucide import.
- `src/app/components/ErrorBanner.tsx`: delete `const planLimit = …` and the `{planLimit && (…)}` link block, plus any import only they used (`Link`, `AppRoutes`, `ApiError` if unused).
- `src/app/components/components.test.tsx`: delete the test "shows the server's sentence and offers Premium on a plan limit".
- `src/app/api/http.ts`: delete the `isPlanLimit` getter and remove `402` from `USER_FACING`.
- `src/app/api/errors.test.ts`: delete the free-plan 402 / `isPlanLimit` test.
- `src/app/api/endpoints.ts`: delete `billingStatus` and `refreshPurchases` (and the comment line between them).
- `src/app/api/queryClient.ts`: delete `billing: ['billing'] as const,`.
- `src/app/api/types.ts`: delete `BillingStatusDto`.
- Locales: delete the contiguous `premium_*` block in `app.en.ts` (L311-336), `app.ru.ts` (L319-344), `app.uk.ts` (L319-344); delete the `// ─── Landing ───` block in `en.ts` (L121-215), `ru.ts` (L120-209), `uk.ts` (L119-208). Line numbers are from before this task; delete by key prefix, not by line.
- Leave `src/app/prefs.ts`'s `LEGACY_ACCOUNT_SCOPED` list alone: it clears stale `premium` keys from browsers that ran the old app.

- [ ] **Step 5: Verify nothing still refers to them**

Run: `grep -rn "premium\|Premium\|isPlanLimit\|billing\|'landing\.\|hasSession" src --include=*.ts --include=*.tsx`
Expected: only `src/app/prefs.ts` and `src/app/prefs.test.ts` (the legacy key cleanup).

- [ ] **Step 6: Run the web checks**

Run: `npm run typecheck && npm test`
Expected: PASS. If `src/i18n/app-strings.test.ts` fails its "more than 400 snake_case keys" floor, lower the floor to the new count rounded down to the nearest 50 and say so in the commit message.

- [ ] **Step 7: Commit**

```bash
git add -A src
git commit -m "Remove premium and the marketing landing page from the web app"
```

---

### Task 10: Setup screen and instance-aware sign-in pages

**Files:**
- Create: `src/landing/instance.ts`, `src/landing/Login.test.tsx`
- Modify: `src/App.tsx` (route `/setup`), `src/landing/Login.tsx`, `src/landing/Register.tsx`, `src/i18n/locales/{en,ru,uk}.ts`

**Interfaces:**
- Consumes: `GET /api/instance` (Task 5).
- Produces: `interface InstanceInfo { name: string; version: string; apiVersion: number; setupRequired: boolean; registrationOpen: boolean; mailEnabled: boolean; bankProviders: string[] }`, `fetchInstance(): Promise<InstanceInfo>`, `useInstance(): InstanceInfo | null` (all in `src/landing/instance.ts`); `Register` takes `{ setup?: boolean }`.

- [ ] **Step 1: Write the failing tests**

`src/landing/Login.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n';
import { en } from '../i18n/locales/en';
import Login from './Login';

const instance = (over: Partial<Record<string, unknown>> = {}) =>
    new Response(
        JSON.stringify({
            name: 'PersonalMoney', version: '1.0.0', apiVersion: 1, setupRequired: false,
            registrationOpen: false, mailEnabled: false, bankProviders: [], ...over,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
    );

const renderLogin = () =>
    render(
        <MemoryRouter initialEntries={['/login']}>
            <I18nProvider>
                <Routes>
                    <Route path="/login" element={<Login />} />
                    <Route path="/setup" element={<div>setup screen</div>} />
                </Routes>
            </I18nProvider>
        </MemoryRouter>,
    );

afterEach(() => vi.unstubAllGlobals());

it('hides register and forgot-password on a closed instance without mail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(instance()));
    renderLogin();
    expect(await screen.findByText(en['login.title'])).toBeInTheDocument();
    await Promise.resolve();
    expect(screen.queryByText(en['login.createOne'])).toBeNull();
    expect(screen.queryByText(en['login.forgot'])).toBeNull();
});

it('offers both when the instance is open and can send mail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(instance({ registrationOpen: true, mailEnabled: true })));
    renderLogin();
    expect(await screen.findByText(en['login.createOne'])).toBeInTheDocument();
    expect(screen.getByText(en['login.forgot'])).toBeInTheDocument();
});

it('sends the first visitor of a new instance to setup', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(instance({ setupRequired: true, registrationOpen: true })));
    renderLogin();
    expect(await screen.findByText('setup screen')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run them to verify they fail**

Run: `npm test -- src/landing/Login.test.tsx`
Expected: FAIL — the links are always rendered and nothing navigates to `/setup`.

- [ ] **Step 3: Add the instance helper**

`src/landing/instance.ts`:

```ts
import { useEffect, useState } from 'react';

/** What `/api/instance` says about this server; read before anyone signs in. */
export interface InstanceInfo {
    name: string;
    version: string;
    apiVersion: number;
    setupRequired: boolean;
    registrationOpen: boolean;
    mailEnabled: boolean;
    bankProviders: string[];
}

export async function fetchInstance(): Promise<InstanceInfo> {
    const res = await fetch('/api/instance');
    if (!res.ok) throw new Error(res.statusText);
    return res.json();
}

/**
 * Null until the answer arrives, and if it never does - the sign-in pages then show only
 * what always works (signing in), rather than links that might lead nowhere.
 */
export function useInstance(): InstanceInfo | null {
    const [info, setInfo] = useState<InstanceInfo | null>(null);
    useEffect(() => {
        let live = true;
        fetchInstance()
            .then((i) => live && setInfo(i))
            .catch(() => undefined);
        return () => {
            live = false;
        };
    }, []);
    return info;
}
```

- [ ] **Step 4: Make Login follow the instance**

`src/landing/Login.tsx`: import `useEffect` and `{ useInstance } from './instance'`; inside the component:

```tsx
    const instance = useInstance();

    // A new instance has nobody to sign in as: its first visitor creates the administrator.
    useEffect(() => {
        if (instance?.setupRequired) navigate('/setup' + location.search, { replace: true });
    }, [instance, navigate, location.search]);
```

and replace the `footer` prop with:

```tsx
            footer={
                <>
                    {instance?.registrationOpen && (
                        <p className="text-ink-dim">
                            {t('login.noAccount')}{' '}
                            <Link to="/register" className="font-semibold text-brand-light transition-colors hover:text-brand">
                                {t('login.createOne')}
                            </Link>
                        </p>
                    )}
                    {instance?.mailEnabled && (
                        <p className="mt-3">
                            <Link to="/forgot-password" className="text-ink-dim transition-colors hover:text-ink-soft">
                                {t('login.forgot')}
                            </Link>
                        </p>
                    )}
                </>
            }
```

- [ ] **Step 5: Make Register serve setup too**

`src/landing/Register.tsx`:
- change the signature to `const Register: React.FC<{ setup?: boolean }> = ({ setup = false }) => {`;
- add `const instance = useInstance();` and

```tsx
    useEffect(() => {
        if (!setup && instance?.setupRequired) navigate('/setup' + location.search, { replace: true });
    }, [setup, instance, navigate, location.search]);
    const closed = !setup && instance !== null && !instance.registrationOpen;
```

- pass `title={t(setup ? 'setup.title' : 'register.title')}` and `subtitle={t(setup ? 'setup.subtitle' : 'register.subtitle')}` to `AuthShell`;
- when `closed`, render the alert instead of the `<form>`:

```tsx
            {closed ? (
                <div className="alert-error" role="alert">
                    <AlertTriangle size={16} />
                    <span>{t('register.closed')}</span>
                </div>
            ) : (
                /* the existing <form>…</form> */
            )}
```

(match the existing alert markup used for `error` in this file).

`src/App.tsx`: add `<Route path="/setup" element={<Register setup />} />` next to `/register`.

- [ ] **Step 6: Add the strings**

`en.ts` (next to the `register.*` keys):

```ts
    'register.closed': 'Registration on this instance is closed. Ask the person who runs it for an account.',
    'setup.title': 'Create the administrator account',
    'setup.subtitle': 'This instance is new. The first account manages it: who can join, and password resets.',
```

`ru.ts`:

```ts
    'register.closed': 'Регистрация на этом сервере закрыта. Попросите аккаунт у того, кто его администрирует.',
    'setup.title': 'Создайте аккаунт администратора',
    'setup.subtitle': 'Это новый сервер. Первый аккаунт управляет им: кто может присоединиться и сброс паролей.',
```

`uk.ts`:

```ts
    'register.closed': 'Реєстрація на цьому сервері закрита. Попросіть акаунт у того, хто його адмініструє.',
    'setup.title': 'Створіть акаунт адміністратора',
    'setup.subtitle': 'Це новий сервер. Перший акаунт керує ним: хто може приєднатися, і скидання паролів.',
```

- [ ] **Step 7: Run the web checks**

Run: `npm run typecheck && npm test`
Expected: PASS. `App.test.tsx` from Task 9 now also performs an `/api/instance` fetch on the login page; if jsdom's missing `fetch` makes it noisy or fail, stub `fetch` there with the same `instance()` response.

- [ ] **Step 8: Commit**

```bash
git add -A src
git commit -m "Add the first-run setup screen and follow the instance on the sign-in pages"
```

---

### Task 11: Administration page and the real version in settings

**Files:**
- Create: `src/app/features/admin/AdminPage.tsx`, `src/app/features/admin/AdminPage.test.tsx`
- Modify: `src/app/api/types.ts`, `src/app/api/endpoints.ts`, `src/app/api/queryClient.ts`, `src/app/lib/routes.ts`, `src/app/AppRoot.tsx`, `src/app/shell/AccountMenu.tsx`, `src/app/features/settings/SettingsPage.tsx:19-20,116`, `src/i18n/locales/{app.en,app.ru,app.uk}.ts`

**Interfaces:**
- Consumes: `/api/admin/*` (Task 7), `/api/instance` (Task 5), `UserResponse.role` (already on the server).
- Produces: `AppRoutes.admin = '/app/admin'`; types `InstanceDto`, `AdminUserDto`, `RegistrationSettingDto`, `ResetLinkDto`; `UserDto.role?: 'USER' | 'ADMIN'`; endpoints `instance`, `adminUsers`, `adminRegistration`, `setAdminRegistration`, `adminResetLink`, `adminDeleteUser`; query keys `qk.instance`, `qk.adminUsers`, `qk.adminRegistration`.

- [ ] **Step 1: Add the API surface**

`src/app/api/types.ts` — add `role?: 'USER' | 'ADMIN';` to `UserDto`, and:

```ts
export interface InstanceDto {
    name: string;
    version: string;
    apiVersion: number;
    setupRequired: boolean;
    registrationOpen: boolean;
    mailEnabled: boolean;
    bankProviders: string[];
}

export interface AdminUserDto {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
    role: 'USER' | 'ADMIN';
    createdAt: string;
    lastSignInAt?: string | null;
    householdId?: number | null;
}

export interface RegistrationSettingDto {
    open: boolean;
    lockedByEnvironment: boolean;
}

export interface ResetLinkDto {
    path: string;
    expiresAt: string;
}
```

`src/app/api/endpoints.ts` — add before the closing `};`:

```ts
    // ── the instance and its administration ─────────────────────────────────
    instance: () => api<T.InstanceDto>('/instance'),
    adminUsers: () => api<T.AdminUserDto[]>('/admin/users'),
    adminRegistration: () => api<T.RegistrationSettingDto>('/admin/registration'),
    setAdminRegistration: (open: boolean) =>
        api<T.RegistrationSettingDto>('/admin/registration', { method: 'PUT', json: { open } }),
    adminResetLink: (userId: number) => api<T.ResetLinkDto>(`/admin/users/${userId}/reset-link`, { method: 'POST' }),
    adminDeleteUser: (userId: number) => api<void>(`/admin/users/${userId}`, { method: 'DELETE' }),
```

`src/app/api/queryClient.ts` — add to `qk`: `instance: ['instance'] as const,`, `adminUsers: ['admin', 'users'] as const,`, `adminRegistration: ['admin', 'registration'] as const,`.

`src/app/lib/routes.ts` — add `admin: \`${APP}/admin\`,` to `AppRoutes`.

- [ ] **Step 2: Write the failing page test**

`src/app/features/admin/AdminPage.test.tsx`:

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import AdminPage from './AdminPage';

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const users = [
    { id: 1, email: 'admin@example.com', firstName: 'Ada', lastName: 'A', role: 'ADMIN', createdAt: '2026-10-01T10:00:00', lastSignInAt: null, householdId: 1 },
    { id: 2, email: 'kid@example.com', firstName: 'Kid', lastName: 'A', role: 'USER', createdAt: '2026-10-02T10:00:00', lastSignInAt: '2026-10-03T10:00:00Z', householdId: 2 },
];

function stubServer() {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
        if (url.startsWith('/api/admin/users/2/reset-link')) return Promise.resolve(json({ path: '/reset-password?token=abc', expiresAt: '2026-10-04T10:00:00Z' }));
        if (url.startsWith('/api/admin/users')) return Promise.resolve(json(users));
        if (url.startsWith('/api/admin/registration') && init?.method === 'PUT') return Promise.resolve(json({ open: true, lockedByEnvironment: false }));
        if (url.startsWith('/api/admin/registration')) return Promise.resolve(json({ open: false, lockedByEnvironment: false }));
        return Promise.resolve(json({}, 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const renderPage = () =>
    render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <MemoryRouter>
                <I18nProvider>
                    <AdminPage />
                </I18nProvider>
            </MemoryRouter>
        </QueryClientProvider>,
    );

afterEach(() => vi.unstubAllGlobals());

it('lists the users of the instance', async () => {
    stubServer();
    renderPage();
    expect(await screen.findByText('kid@example.com')).toBeInTheDocument();
    expect(screen.getByText('admin@example.com')).toBeInTheDocument();
});

it('issues a reset link the administrator can pass on', async () => {
    stubServer();
    renderPage();
    const buttons = await screen.findAllByRole('button', { name: appEn.admin_reset_link });
    fireEvent.click(buttons[1]);
    expect(await screen.findByDisplayValue(`${window.location.origin}/reset-password?token=abc`)).toBeInTheDocument();
});

it('opens registration', async () => {
    const fetchMock = stubServer();
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: appEn.admin_registration_open }));
    await screen.findByText('kid@example.com');
    expect(fetchMock.mock.calls.some(([url, init]) => String(url).startsWith('/api/admin/registration') && init?.method === 'PUT')).toBe(true);
});
```

`Switch` renders a `<button>` labelled by its `label`; if its accessible name differs, select it by `screen.getByRole('switch')` or whatever role `forms.tsx`'s `Switch` sets, and adjust this one query.

- [ ] **Step 3: Run it to verify it fails**

Run: `npm test -- src/app/features/admin`
Expected: FAIL — `./AdminPage` does not exist.

- [ ] **Step 4: Write the page**

`src/app/features/admin/AdminPage.tsx`:

```tsx
import React, { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Copy, KeyRound, Trash2 } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { queryClient, qk } from '../../api/queryClient';
import type { AdminUserDto } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorBanner, errorText } from '../../components/ErrorBanner';
import { Card, Switch } from '../../components/forms';
import { Page } from '../../shell/Page';

/**
 * What the person who runs this instance needs: who has an account, whether anyone else
 * may create one, and a way back in for somebody who lost their password on an instance
 * that cannot send mail.
 */
const AdminPage: React.FC = () => {
    const t = useT();
    const users = useQuery({ queryKey: qk.adminUsers, queryFn: endpoints.adminUsers });
    const registration = useQuery({ queryKey: qk.adminRegistration, queryFn: endpoints.adminRegistration });
    const [link, setLink] = useState<{ email: string; url: string } | null>(null);
    const [deleting, setDeleting] = useState<AdminUserDto | null>(null);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const toggle = useMutation({
        mutationFn: endpoints.setAdminRegistration,
        onSuccess: (data) => queryClient.setQueryData(qk.adminRegistration, data),
    });
    const reset = useMutation({
        mutationFn: (u: AdminUserDto) => endpoints.adminResetLink(u.id).then((r) => ({ email: u.email, url: window.location.origin + r.path })),
        onSuccess: setLink,
    });
    const remove = useMutation({
        mutationFn: (u: AdminUserDto) => endpoints.adminDeleteUser(u.id),
        onSuccess: () => {
            setDeleting(null);
            queryClient.invalidateQueries({ queryKey: qk.adminUsers });
        },
        onError: (e) => setDeleteError(errorText(e, t)),
    });

    return (
        <Page title={t('admin_title')}>
            <div className="flex flex-col gap-4">
                <Card title={t('admin_registration')}>
                    <div className="px-5 pb-4">
                        <Switch
                            checked={registration.data?.open ?? false}
                            onChange={(open) => toggle.mutate(open)}
                            label={t('admin_registration_open')}
                            description={t(registration.data?.lockedByEnvironment ? 'admin_registration_locked' : 'admin_registration_hint')}
                        />
                        <ErrorBanner error={toggle.error} />
                    </div>
                </Card>

                {link && (
                    <Card title={t('admin_reset_link_ready', { email: link.email })}>
                        <div className="flex items-center gap-2 px-5 pb-4">
                            <input readOnly value={link.url} className="input flex-1" onFocus={(e) => e.currentTarget.select()} />
                            <button type="button" className="btn btn-secondary" onClick={() => navigator.clipboard?.writeText(link.url)}>
                                <Copy size={16} /> {t('admin_copy')}
                            </button>
                        </div>
                    </Card>
                )}

                <Card title={t('admin_users')}>
                    <ErrorBanner error={users.error ?? reset.error} />
                    <ul className="divide-y divide-line">
                        {(users.data ?? []).map((u) => (
                            <li key={u.id} className="flex items-center gap-3 px-5 py-3">
                                <div className="min-w-0 flex-1">
                                    <div className="truncate text-sm font-medium text-ink">{u.email}</div>
                                    <div className="text-xs text-ink-soft">
                                        {u.firstName} {u.lastName}
                                        {u.role === 'ADMIN' && <> · {t('admin_role_admin')}</>}
                                        {' · '}
                                        {t('admin_last_sign_in')}{' '}
                                        {u.lastSignInAt ? new Date(u.lastSignInAt).toLocaleString() : t('admin_never')}
                                    </div>
                                </div>
                                <button type="button" className="btn btn-ghost" aria-label={t('admin_reset_link')} title={t('admin_reset_link')} onClick={() => reset.mutate(u)}>
                                    <KeyRound size={16} />
                                </button>
                                {u.role !== 'ADMIN' && (
                                    <button type="button" className="btn btn-ghost text-bad" aria-label={t('admin_delete_user')} title={t('admin_delete_user')} onClick={() => { setDeleteError(null); setDeleting(u); }}>
                                        <Trash2 size={16} />
                                    </button>
                                )}
                            </li>
                        ))}
                    </ul>
                </Card>
            </div>

            <ConfirmDialog
                open={deleting !== null}
                title={t('admin_delete_user')}
                destructive
                busy={remove.isPending}
                confirmLabel={t('admin_delete_user')}
                message={
                    <>
                        <p>{t('admin_delete_confirm', { email: deleting?.email ?? '' })}</p>
                        {deleteError && <p className="mt-3 text-bad">{deleteError}</p>}
                    </>
                }
                onConfirm={() => deleting && remove.mutate(deleting)}
                onCancel={() => !remove.isPending && setDeleting(null)}
            />
        </Page>
    );
};

export default AdminPage;
```

Check `input`, `btn-secondary`, `btn-ghost` against classes defined in `src/index.css` and use the closest existing ones; check how `t()` takes interpolation variables (`{{email}}` in the string, an object as the second argument per the i18n report) and adjust if the signature differs.

- [ ] **Step 5: Wire it in**

- `src/app/AppRoot.tsx`: `const AdminPage = lazy(() => import('./features/admin/AdminPage'));` and `<Route path="admin" element={<AdminPage />} />` before `settings`.
- `src/app/shell/AccountMenu.tsx`: import `Shield` from lucide and `useMe` from `../api/hooks`; inside the component `const { data: me } = useMe();`; before the Settings item:

```tsx
                    {me?.role === 'ADMIN' && (
                        <button role="menuitem" className={`${item} text-ink-soft hover:text-ink`} onClick={() => go(AppRoutes.admin)}>
                            <Shield size={16} /> {t('nav_admin')}
                        </button>
                    )}
```

- `src/app/features/settings/SettingsPage.tsx`: delete `const VERSION = '0.1.0';`; change `PRIVACY_POLICY_URL` to `'/privacy.html'` (the instance serves its own page; Task 16 rewrites it); add `const { data: instance } = useQuery({ queryKey: qk.instance, queryFn: endpoints.instance, staleTime: Infinity });` (import `useQuery` and `qk`); render `{t('settings_version')} {instance?.version ?? '…'}`.

- [ ] **Step 6: Add the strings**

`app.en.ts` (alphabetical position):

```ts
    admin_copy: "Copy",
    admin_delete_confirm: "Delete {{email}}? If nobody else shares their household, its accounts and history are deleted too.",
    admin_delete_user: "Delete user",
    admin_last_sign_in: "last signed in",
    admin_never: "never",
    admin_registration: "Registration",
    admin_registration_hint: "When off, only existing users can sign in.",
    admin_registration_locked: "Set by the REGISTRATION environment variable; change it there.",
    admin_registration_open: "Anyone can create an account",
    admin_reset_link: "Password reset link",
    admin_reset_link_ready: "Reset link for {{email}} - valid for 24 hours, works once",
    admin_role_admin: "administrator",
    admin_title: "Administration",
    admin_users: "Users",
    nav_admin: "Administration",
```

`app.ru.ts`:

```ts
    admin_copy: "Копировать",
    admin_delete_confirm: "Удалить {{email}}? Если в его домохозяйстве больше никого нет, его счета и история тоже удалятся.",
    admin_delete_user: "Удалить пользователя",
    admin_last_sign_in: "последний вход",
    admin_never: "никогда",
    admin_registration: "Регистрация",
    admin_registration_hint: "Когда выключено, войти могут только существующие пользователи.",
    admin_registration_locked: "Задано переменной окружения REGISTRATION; меняйте там.",
    admin_registration_open: "Любой может создать аккаунт",
    admin_reset_link: "Ссылка для сброса пароля",
    admin_reset_link_ready: "Ссылка для {{email}} - действует 24 часа, срабатывает один раз",
    admin_role_admin: "администратор",
    admin_title: "Администрирование",
    admin_users: "Пользователи",
    nav_admin: "Администрирование",
```

`app.uk.ts`:

```ts
    admin_copy: "Копіювати",
    admin_delete_confirm: "Видалити {{email}}? Якщо в його домогосподарстві більше нікого немає, його рахунки та історія теж видаляться.",
    admin_delete_user: "Видалити користувача",
    admin_last_sign_in: "останній вхід",
    admin_never: "ніколи",
    admin_registration: "Реєстрація",
    admin_registration_hint: "Коли вимкнено, увійти можуть лише наявні користувачі.",
    admin_registration_locked: "Задано змінною оточення REGISTRATION; змінюйте там.",
    admin_registration_open: "Будь-хто може створити акаунт",
    admin_reset_link: "Посилання для скидання пароля",
    admin_reset_link_ready: "Посилання для {{email}} - діє 24 години, спрацьовує один раз",
    admin_role_admin: "адміністратор",
    admin_title: "Адміністрування",
    admin_users: "Користувачі",
    nav_admin: "Адміністрування",
```

- [ ] **Step 7: Run the web checks**

Run: `npm run typecheck && npm test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A src
git commit -m "Add the administration page and show the instance's real version"
```

---

# Phase C — Android (`client/`)

All paths in this phase are relative to `client/composeApp/src/` unless they start with `client/`. Build and test from `client/` with the Android Studio JBR (see "Commands used throughout"); every task ends with `testDebugUnitTest assembleDebug` green.

### Task 12: Remove ads, billing and premium

**Files:**
- Delete: `androidMain/kotlin/com/personalmoney/ads/` (AdsConsentManager, AdsInitializer, InterstitialAdManager, PlatformBannerAd.android), `androidMain/kotlin/com/personalmoney/core/billing/` (CurrentActivity, PlayStoreBilling), `commonMain/kotlin/com/personalmoney/ads/BannerAd.kt`, `commonMain/kotlin/com/personalmoney/core/billing/` (EntitlementRepository, StoreBilling), `commonMain/kotlin/com/personalmoney/feature/premium/`, `commonTest/kotlin/com/personalmoney/core/billing/EntitlementRepositoryTest.kt`
- Modify: `client/composeApp/build.gradle.kts` (L21-29, 155-162, 230, 249-250, 261-262), `client/gradle/libs.versions.toml` (L44-50, 135-138), `client/composeApp/proguard-rules.pro` (L66-69), `androidMain/AndroidManifest.xml` (L9, 55-64), `androidMain/kotlin/com/personalmoney/MainActivity.kt` (L13, 16-18, 29, 35-37, 46-57), `androidMain/kotlin/com/personalmoney/PersonalMoneyApplication.kt` (L4-5, 29, 44-46, 57-63), `androidMain/kotlin/com/personalmoney/core/di/Modules.kt` (L6-8, 57-60, 145-149, 152-156, 186), `commonMain/kotlin/com/personalmoney/core/di/SharedViewModels.kt` (L15, 51), `commonMain/kotlin/com/personalmoney/navigation/{Routes.kt:16, PersonalMoneyNavHost.kt:77,160,334,423,499,587-589, AppHeader.kt:29,93,170,315,376-383}`, `commonMain/kotlin/com/personalmoney/feature/more/MoreScreen.kt` (L20, 44, 67), `commonMain/kotlin/com/personalmoney/feature/accounts/{AccountEditScreen.kt:76-104, AccountEditViewModel.kt:8,51,175}`, `commonMain/kotlin/com/personalmoney/feature/dashboard/DashboardScreen.kt` (L58, 264, 671), `commonMain/kotlin/com/personalmoney/feature/transactions/TransactionsScreen.kt` (L62, 279), `commonMain/kotlin/com/personalmoney/feature/quickadd/QuickAddViewModel.kt` (L231), `commonMain/kotlin/com/personalmoney/core/data/UserPreferences.kt`, `commonMain/kotlin/com/personalmoney/core/network/{HomeBudgetApi.kt:56-72, ApiDtos.kt:61-94, ApiErrors.kt:28-31,36-44}`, `commonTest/kotlin/com/personalmoney/core/network/ApiErrorsTest.kt` (L98-113), `commonMain/composeResources/values{,-ru,-uk}/strings.xml` (the `<!-- Premium -->` block)

Line numbers are from before this task; use them to find the code, then delete by meaning.

**Interfaces:**
- Produces: no `BannerAd`, `EntitlementRepository`, `StoreBilling`, `PremiumScreen`, `Routes.PREMIUM`, `isPlanLimit`, `BillingStatusDto`; `UserPreferences` without `premium`, `premiumExpiresAtMs`, `transactionSaveCount`, `adsConsentObtained`.

- [ ] **Step 1: Delete the whole-file pieces**

```bash
cd client/composeApp/src
git rm -r androidMain/kotlin/com/personalmoney/ads androidMain/kotlin/com/personalmoney/core/billing
git rm -r commonMain/kotlin/com/personalmoney/ads commonMain/kotlin/com/personalmoney/core/billing commonMain/kotlin/com/personalmoney/feature/premium
git rm commonTest/kotlin/com/personalmoney/core/billing/EntitlementRepositoryTest.kt
cd ../../..
```

- [ ] **Step 2: Remove the SDKs from the build**

- `build.gradle.kts`: delete the `admobAppId`, `admobBanner`, `admobInterstitial` vals (keep `privacyPolicyUrl`, Task 16 changes its default); delete `implementation(libs.play.services.ads)`, `implementation(libs.user.messaging.platform)`, `implementation(libs.play.billing)` and the billing comment; delete `manifestPlaceholders["admobAppId"] = admobAppId`; delete the four `ADMOB_*` `buildConfigField` lines.
- `libs.versions.toml`: delete the `playBilling`, `playServicesAds`, `userMessagingPlatform` versions (with the billing comment) and the `play-billing`, `play-services-ads`, `user-messaging-platform` libraries. Keep `google-material`.
- `proguard-rules.pro`: delete the `# --- AdMob ---` block.
- `androidMain/AndroidManifest.xml`: delete the `AD_ID` permission and the three `com.google.android.gms.ads.*` `<meta-data>` elements.

- [ ] **Step 3: Remove the call sites**

- `MainActivity.kt`: delete the ads imports and injected fields, and the whole `lifecycleScope.launch { if (preferences.premium.first()) … }` block; delete `lifecycleScope` and `first` imports if now unused.
- `PersonalMoneyApplication.kt`: delete the billing imports, `entitlements` field, `CurrentActivity.track(this)`, and the `entitlements.syncOwnedPurchases()` launch.
- `core/di/Modules.kt`: delete the ads and billing imports, the `StoreBilling`/`EntitlementRepository` bindings, the whole `adsModule`, and `adsModule` from `appModule`'s `includes(...)`.
- `core/di/SharedViewModels.kt`: delete the `PremiumViewModel` import and `viewModelOf(::PremiumViewModel)`.
- `navigation/Routes.kt`: delete `PREMIUM`.
- `navigation/PersonalMoneyNavHost.kt`: delete the `PremiumScreen` import, `Routes.PREMIUM` from `registeredRoutes`, the `composable(Routes.PREMIUM) { … }` block, and the three `onOpenPremium = …` arguments; fix the two comments that mention premium.
- `navigation/AppHeader.kt`: delete the `onOpenPremium` parameter (both the header's and `AccountChip`'s), its pass-through, the Premium `DropdownMenuItem`, and the `WorkspacePremium` import.
- `feature/more/MoreScreen.kt`: delete the `onOpenPremium` parameter, the Premium `MoreItem`, and the import.
- `feature/accounts/AccountEditScreen.kt`: delete the `onOpenPremium` parameter and KDoc, `seePremium`, `offerPremium`, and the snackbar action that used them — the snackbar shows the plain error message.
- `feature/accounts/AccountEditViewModel.kt`: delete `planLimit` from the state, the `isPlanLimit` import and `planLimit = failure.isPlanLimit()`.
- `feature/dashboard/DashboardScreen.kt` and `feature/transactions/TransactionsScreen.kt`: delete the `BannerAd` import and every `BannerAd()` / `item { BannerAd() }`.
- `feature/quickadd/QuickAddViewModel.kt`: delete `userPreferences.incrementTransactionSaveCount()`.
- `core/data/UserPreferences.kt`: delete `transactionSaveCount`, `adsConsentObtained`, `premium`, `premiumExpiresAtMs`, `incrementTransactionSaveCount`, `setAdsConsentObtained`, `setPremium`, the ads-consent KDoc, and the `TRANSACTION_SAVE_COUNT`, `ADS_CONSENT_OBTAINED`, `PREMIUM`, `PREMIUM_EXPIRES_AT_MS` keys. In `clearAccountScoped()` keep `store.remove("premium", "premium_expires_at_ms")` as literal strings with the comment `// Left by builds that sold premium.` so an upgraded phone does not keep stale values.
- `core/network/HomeBudgetApi.kt`: delete the billing block (`billingStatus`, `registerPurchase`, `refreshPurchases` and their KDoc).
- `core/network/ApiDtos.kt`: delete the billing section (`BillingStatusDto`, `RegisterPurchaseRequestDto`).
- `core/network/ApiErrors.kt`: delete `isPlanLimit` and its KDoc, and remove `HttpStatusCode.PaymentRequired` from `USER_FACING` with its comment.
- `commonTest/.../core/network/ApiErrorsTest.kt`: delete the tests that exercise 402 / `isPlanLimit`.
- `strings.xml` (all three): delete the `<!-- Premium -->` comment and its 26 `premium_*` strings.

- [ ] **Step 4: Verify nothing still refers to them**

Run (from `client/composeApp/src`): `grep -rn "premium\|Premium\|BannerAd\|AdMob\|admob\|billing\|Billing\|Entitlement\|isPlanLimit\|gms.ads\|ump\." --include=*.kt --include=*.xml --include=*.kts .`
Expected: only the two literal legacy keys in `UserPreferences.clearAccountScoped()` and HomeMoney's own `isPremium` field if the client maps it (leave HomeMoney DTOs untouched).

- [ ] **Step 5: Build and test**

Run: `./gradlew.bat testDebugUnitTest assembleDebug --no-daemon` (from `client/`)
Expected: BUILD SUCCESSFUL. `KoinModulesTest` must pass — it proves the DI graph lost nothing it still needs.

- [ ] **Step 6: Commit**

```bash
git add -A client
git commit -m "Remove AdMob, Play Billing and premium from the Android app"
```

---

### Task 13: Server address at sign-in

**Files:**
- Create: `commonMain/kotlin/com/personalmoney/core/auth/ServerAddress.kt`, `androidMain/res/xml/network_security_config.xml`, `commonTest/kotlin/com/personalmoney/core/auth/ServerAddressTest.kt`, `commonTest/kotlin/com/personalmoney/core/auth/ServerCheckTest.kt`
- Delete: `androidDebug/AndroidManifest.xml`, `androidDebug/res/xml/network_security_config.xml`
- Modify: `commonMain/kotlin/com/personalmoney/core/auth/{TokenStore.kt, AuthRepository.kt}`, `androidMain/kotlin/com/personalmoney/core/auth/AndroidTokenStore.kt`, `commonMain/kotlin/com/personalmoney/core/network/{HttpClients.kt, HomeBudgetApi.kt, ApiDtos.kt}`, `androidMain/kotlin/com/personalmoney/core/di/Modules.kt:112`, `commonMain/kotlin/com/personalmoney/feature/auth/{AuthViewModel.kt, AuthScreen.kt}`, `androidMain/AndroidManifest.xml` (application tag), `client/composeApp/build.gradle.kts` (L33-37, 252, 264, 311-319), `commonTest/kotlin/com/personalmoney/core/auth/AuthFakes.kt`, `commonTest/kotlin/com/personalmoney/feature/auth/AuthFormResetTest.kt`, strings (3 files)

**Interfaces:**
- Consumes: `GET /api/instance` (Task 5).
- Produces:
  - `object ServerAddress { const val MIN_API_VERSION = 1; const val MAX_API_VERSION = 1; fun normalize(input: String): String?; fun isCleartext(base: String): Boolean }`
  - `TokenStore.serverUrl: String?` (survives `clear()`)
  - `@Serializable data class InstanceDto(name, version, apiVersion: Int, setupRequired, registrationOpen, mailEnabled, bankProviders)`
  - `HomeBudgetApi.instance(baseUrl: String): InstanceDto`
  - `sealed interface ServerCheck { data class Ok(val baseUrl: String, val instance: InstanceDto); data object Invalid; data object Unreachable; data object NotPersonalMoney; data class Unsupported(val apiVersion: Int) }`
  - `AuthRepository.checkServer(input: String): ServerCheck`, `AuthRepository.lastServer: String?`
  - `createBackendClient(engine, json, baseUrl: () -> String?, tokenStore)`
  - `AuthUiState.serverAddress: String`, `AuthUiState.serverProblem: ServerCheck?`, `AuthUiState.instance: InstanceDto?`; `AuthViewModel.onServerAddressChange(value: String)`

- [ ] **Step 1: Write the failing tests**

`commonTest/kotlin/com/personalmoney/core/auth/ServerAddressTest.kt`:

```kotlin
package com.personalmoney.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerAddressTest {

    @Test
    fun `a bare host becomes an https base url`() {
        assertEquals("https://money.example.com/", ServerAddress.normalize(" money.example.com "))
    }

    @Test
    fun `an explicit scheme, port and path are kept, with one trailing slash`() {
        assertEquals("http://192.168.1.10:8080/", ServerAddress.normalize("http://192.168.1.10:8080"))
        assertEquals("https://nas.local/money/", ServerAddress.normalize("https://nas.local/money//"))
    }

    @Test
    fun `nonsense is refused`() {
        assertNull(ServerAddress.normalize(""))
        assertNull(ServerAddress.normalize("not a url"))
        assertNull(ServerAddress.normalize("https://"))
    }

    @Test
    fun `plain http is flagged`() {
        assertTrue(ServerAddress.isCleartext("http://192.168.1.10:8080/"))
        assertFalse(ServerAddress.isCleartext("https://money.example.com/"))
    }
}
```

`commonTest/kotlin/com/personalmoney/core/auth/ServerCheckTest.kt`:

```kotlin
package com.personalmoney.core.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerCheckTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun repoAnswering(status: HttpStatusCode, body: String, json: Boolean = true) =
        authRepositoryOver(
            MockEngine { request ->
                assertEquals("/api/instance", request.url.encodedPath)
                respond(body, status, if (json) jsonHeaders else headersOf(HttpHeaders.ContentType, "text/html"))
            },
            FakeTokenStore(signedIn = false),
            RecordingWiper()
        )

    @Test
    fun `a PersonalMoney server answers with its instance`() = runTest {
        val check = repoAnswering(
            HttpStatusCode.OK,
            """{"name":"PersonalMoney","version":"1.0.0","apiVersion":1,"setupRequired":false,"registrationOpen":true,"mailEnabled":false,"bankProviders":[]}"""
        ).checkServer("money.example.com")
        assertIs<ServerCheck.Ok>(check)
        assertEquals("https://money.example.com/", check.baseUrl)
        assertEquals(true, check.instance.registrationOpen)
    }

    @Test
    fun `some other web server is recognised as not ours`() = runTest {
        assertIs<ServerCheck.NotPersonalMoney>(repoAnswering(HttpStatusCode.OK, "<html></html>", json = false).checkServer("example.com"))
        assertIs<ServerCheck.NotPersonalMoney>(repoAnswering(HttpStatusCode.NotFound, "{}").checkServer("example.com"))
    }

    @Test
    fun `a server speaking a newer API is refused with its version`() = runTest {
        val check = repoAnswering(
            HttpStatusCode.OK,
            """{"name":"PersonalMoney","version":"9.0.0","apiVersion":99,"setupRequired":false,"registrationOpen":false,"mailEnabled":false,"bankProviders":[]}"""
        ).checkServer("money.example.com")
        assertEquals(ServerCheck.Unsupported(99), check)
    }

    @Test
    fun `an address that is not a url never reaches the network`() = runTest {
        assertEquals(ServerCheck.Invalid, repoAnswering(HttpStatusCode.OK, "{}").checkServer("not a url"))
    }
}
```

(`authRepositoryOver`, `FakeTokenStore`, `RecordingWiper` live in `AuthFakes.kt`; Step 4 updates them.)

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew.bat testDebugUnitTest --no-daemon --tests "com.personalmoney.core.auth.*"`
Expected: FAIL to compile — `ServerAddress`, `ServerCheck`, `checkServer` do not exist.

- [ ] **Step 3: Address normalisation and the instance DTO**

`commonMain/.../core/auth/ServerAddress.kt`:

```kotlin
package com.personalmoney.core.auth

/**
 * Turns what a person types into the base URL every request is resolved against.
 *
 * A self-hosted server is a LAN address as often as a domain, so a bare host means https
 * (the safe default) and an explicit http:// is respected - with a warning in the UI.
 */
object ServerAddress {
    /** The `/api/instance` apiVersion range this build understands. */
    const val MIN_API_VERSION = 1
    const val MAX_API_VERSION = 1

    fun normalize(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        val host = withScheme.substringAfter("://").substringBefore('/').substringBefore(':')
        if (host.isEmpty()) return null
        return "$withScheme/"
    }

    fun isCleartext(base: String): Boolean = base.startsWith("http://")
}

/** What a typed server address turned out to be. */
sealed interface ServerCheck {
    data class Ok(val baseUrl: String, val instance: com.personalmoney.core.network.InstanceDto) : ServerCheck
    data object Invalid : ServerCheck
    data object Unreachable : ServerCheck
    data object NotPersonalMoney : ServerCheck
    data class Unsupported(val apiVersion: Int) : ServerCheck
}
```

`core/network/ApiDtos.kt` — add:

```kotlin
/** `/api/instance`: what a server offers, read before anyone signs in. */
@Serializable
data class InstanceDto(
    val name: String,
    val version: String = "",
    val apiVersion: Int,
    val setupRequired: Boolean = false,
    val registrationOpen: Boolean = false,
    val mailEnabled: Boolean = false,
    val bankProviders: List<String> = emptyList()
)
```

(`name` and `apiVersion` have no defaults on purpose: a JSON body without them is not a PersonalMoney server.)

`core/network/HomeBudgetApi.kt` — add:

```kotlin
    /** Absolute on purpose: it is asked of an address before that address is the client's base. */
    suspend fun instance(baseUrl: String): InstanceDto = client.get("${baseUrl}api/instance").body()
```

`core/network/HttpClients.kt` — `isAuthEndpoint` also exempts the instance call from bearer tokens:

```kotlin
private fun isAuthEndpoint(segments: List<String>): Boolean {
    val parts = segments.filter { it.isNotEmpty() }
    if (parts.size >= 2 && parts[parts.size - 2] == "api" && parts.last() == "instance") return true
    if (parts.size < 2) return false
    return parts[parts.size - 2] == "auth" &&
        parts[parts.size - 1] in setOf("login", "register", "refresh")
}
```

and `createBackendClient` takes the base lazily — change the parameter to `baseUrl: () -> String?` and the line `defaultRequest { url(baseUrl) }` to:

```kotlin
    // Read per request: the address is whatever the user signed in to, and can change
    // without rebuilding the client.
    defaultRequest { baseUrl()?.let { url(it) } }
```

- [ ] **Step 4: Remember the server next to the tokens**

`core/auth/TokenStore.kt` — add to the interface:

```kotlin
    /** The server signed in to. Kept by [clear]: signing out does not forget the address. */
    var serverUrl: String?
```

`androidMain/.../core/auth/AndroidTokenStore.kt` — add `private const val KEY_SERVER = "server_url"`, implement `serverUrl` with `prefs.getString(KEY_SERVER, null)` / `prefs.edit().putString(KEY_SERVER, value).apply()` (remove when null), and change `clear()` from `prefs.edit().clear().apply()` to removing `KEY_ACCESS`, `KEY_REFRESH`, `KEY_EMAIL` only (then update `signedIn` as today).

`commonTest/.../core/auth/AuthFakes.kt` — add `override var serverUrl: String? = null` to `FakeTokenStore`.

`androidMain/.../core/di/Modules.kt:112`:

```kotlin
    single {
        val tokens = get<TokenStore>()
        HomeBudgetApi(createBackendClient(get(), get(), { tokens.serverUrl }, tokens))
    }
```

and delete the `import com.personalmoney.BuildConfig` line if nothing else in the file uses it.

`core/auth/AuthRepository.kt` — add:

```kotlin
    val lastServer: String? get() = tokenStore.serverUrl

    suspend fun checkServer(input: String): ServerCheck {
        val base = ServerAddress.normalize(input) ?: return ServerCheck.Invalid
        return try {
            val instance = api.instance(base)
            if (instance.apiVersion !in ServerAddress.MIN_API_VERSION..ServerAddress.MAX_API_VERSION) {
                ServerCheck.Unsupported(instance.apiVersion)
            } else {
                ServerCheck.Ok(base, instance)
            }
        } catch (e: kotlinx.io.IOException) {
            ServerCheck.Unreachable
        } catch (e: Exception) {
            // A 404, an HTML page, JSON of some other shape: something answered, and it is
            // not a PersonalMoney server.
            ServerCheck.NotPersonalMoney
        }
    }
```

Use the same `IOException` type that `runCatchingAuth` already catches in this file (it may be `io.ktor.utils.io.errors.IOException` or `kotlinx.io.IOException` depending on the Ktor version). Then make `login` and `register` take the checked base URL as their first parameter and set `tokenStore.serverUrl = baseUrl` before calling the API.

- [ ] **Step 5: Ask for the address on the sign-in screen**

`feature/auth/AuthViewModel.kt`:
- `AuthUiState` gains `val serverAddress: String = ""`, `val serverProblem: ServerCheck? = null`, `val instance: InstanceDto? = null`, and `canSubmit` additionally requires `serverAddress.isNotBlank()`; add `val cleartext: Boolean get() = ServerAddress.normalize(serverAddress)?.let(ServerAddress::isCleartext) == true`.
- The initial state and the reset-on-sign-out state prefill `serverAddress = authRepository.lastServer?.removeSuffix("/") ?: ""`.
- `fun onServerAddressChange(value: String) = _state.update { it.copy(serverAddress = value, serverProblem = null, instance = null, error = null) }`.
- `submit()` first runs `authRepository.checkServer(current.serverAddress)`; anything but `Ok` sets `busy = false, serverProblem = check` and returns; on `Ok`, store `instance` and refuse `registerMode` when `!check.instance.registrationOpen && !check.instance.setupRequired` (set `error` to the closed-registration message); then call `login(check.baseUrl, …)` / `register(check.baseUrl, …)` as before.

`feature/auth/AuthScreen.kt` — in `AuthCard`, first field:

```kotlin
            LabelledField(
                label = stringResource(Res.string.auth_server_label),
                value = state.serverAddress,
                onValueChange = viewModel::onServerAddressChange,
                icon = Icons.Outlined.Dns,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                hint = stringResource(Res.string.auth_server_hint),
            )
            if (state.cleartext) {
                Text(
                    stringResource(Res.string.auth_server_cleartext_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.serverProblem?.let { problem ->
                ErrorAlert(
                    when (problem) {
                        ServerCheck.Invalid -> stringResource(Res.string.auth_server_invalid)
                        ServerCheck.Unreachable -> stringResource(Res.string.auth_server_unreachable)
                        ServerCheck.NotPersonalMoney -> stringResource(Res.string.auth_server_not_personalmoney)
                        is ServerCheck.Unsupported -> stringResource(Res.string.auth_server_unsupported, problem.apiVersion)
                        is ServerCheck.Ok -> ""
                    }
                )
            }
```

(import `androidx.compose.material.icons.outlined.Dns`). Show the register/sign-in mode toggle only when `state.instance == null || state.instance.registrationOpen || state.instance.setupRequired`.

Strings — `values/strings.xml`:

```xml
    <string name="auth_server_label">Server address</string>
    <string name="auth_server_hint">The address you open PersonalMoney at, e.g. money.example.com or 192.168.1.10:8080</string>
    <string name="auth_server_cleartext_warning">This address uses plain http: traffic to it is not encrypted. Use it only on a network you trust.</string>
    <string name="auth_server_invalid">That is not a server address.</string>
    <string name="auth_server_unreachable">Could not reach that server. Check the address and that you are on the right network.</string>
    <string name="auth_server_not_personalmoney">Something answered at that address, but it is not a PersonalMoney server.</string>
    <string name="auth_server_unsupported">That server speaks API version %1$d, which this app does not support. Update the app or the server.</string>
    <string name="auth_registration_closed">Registration on this server is closed. Ask the person who runs it for an account.</string>
```

`values-ru/strings.xml`:

```xml
    <string name="auth_server_label">Адрес сервера</string>
    <string name="auth_server_hint">Адрес, по которому открывается PersonalMoney, например money.example.com или 192.168.1.10:8080</string>
    <string name="auth_server_cleartext_warning">Этот адрес использует обычный http: трафик не шифруется. Используйте его только в доверенной сети.</string>
    <string name="auth_server_invalid">Это не адрес сервера.</string>
    <string name="auth_server_unreachable">Не удалось связаться с сервером. Проверьте адрес и что вы в нужной сети.</string>
    <string name="auth_server_not_personalmoney">По этому адресу кто-то отвечает, но это не сервер PersonalMoney.</string>
    <string name="auth_server_unsupported">Сервер использует API версии %1$d, которую это приложение не поддерживает. Обновите приложение или сервер.</string>
    <string name="auth_registration_closed">Регистрация на этом сервере закрыта. Попросите аккаунт у того, кто его администрирует.</string>
```

`values-uk/strings.xml`:

```xml
    <string name="auth_server_label">Адреса сервера</string>
    <string name="auth_server_hint">Адреса, за якою відкривається PersonalMoney, наприклад money.example.com або 192.168.1.10:8080</string>
    <string name="auth_server_cleartext_warning">Ця адреса використовує звичайний http: трафік не шифрується. Використовуйте її лише в довіреній мережі.</string>
    <string name="auth_server_invalid">Це не адреса сервера.</string>
    <string name="auth_server_unreachable">Не вдалося зв'язатися із сервером. Перевірте адресу і що ви в потрібній мережі.</string>
    <string name="auth_server_not_personalmoney">За цією адресою хтось відповідає, але це не сервер PersonalMoney.</string>
    <string name="auth_server_unsupported">Сервер використовує API версії %1$d, яку цей застосунок не підтримує. Оновіть застосунок або сервер.</string>
    <string name="auth_registration_closed">Реєстрація на цьому сервері закрита. Попросіть акаунт у того, хто його адмініструє.</string>
```

(Compose resource strings escape the apostrophe in `зв'язатися` as `\'` if the build complains.)

- [ ] **Step 6: Allow LAN http and user-installed certificates in every build**

Create `androidMain/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    A self-hosted server is often a LAN address with no certificate, or a home domain
    with a certificate from the owner's own CA. So: plain http is allowed (the sign-in
    screen warns about it), and CAs the user installed on the phone are trusted.
-->
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

Add `android:networkSecurityConfig="@xml/network_security_config"` to the `<application>` tag in `androidMain/AndroidManifest.xml`. Delete the debug overlay (`git rm -r client/composeApp/src/androidDebug`) and, in `build.gradle.kts`, the `getByName("debug") { manifest.srcFile(...) }` source-set block with its comment, the `apiBaseUrl`/`apiBaseUrlDebug` vals with their comment, and both `API_BASE_URL` `buildConfigField` lines.

- [ ] **Step 7: Update the existing auth tests**

`commonTest/.../feature/auth/AuthFormResetTest.kt`: make `signInEngine()` answer by path — `/api/instance` with the instance JSON from `ServerCheckTest`, anything else with the existing token JSON:

```kotlin
    private fun signInEngine() = MockEngine { request ->
        val body = if (request.url.encodedPath == "/api/instance") {
            """{"name":"PersonalMoney","version":"1.0.0","apiVersion":1,"setupRequired":false,"registrationOpen":true,"mailEnabled":false,"bankProviders":[]}"""
        } else {
            """{"token":"a","refreshToken":"r","email":"first@example.com"}"""
        }
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }
```

and call `viewModel.onServerAddressChange("https://money.example.com")` before `submit()` in each test. Adjust `androidUnitTest/.../feature/auth/AuthUiStateTest.kt` so states that expect `canSubmit == true` carry a `serverAddress`.

- [ ] **Step 8: Build and test**

Run: `./gradlew.bat testDebugUnitTest assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

Manual check on the emulator (`Pixel_6_API35`), with the server running locally (`mvn spring-boot:run -Dspring-boot.run.profiles=local`): install the debug APK, enter `http://10.0.2.2:8085`, see the cleartext warning, sign in. Then enter `example.com` and see "not a PersonalMoney server". Take a screenshot of each for the task report (see `client/CLAUDE.md` for the adb commands).

- [ ] **Step 9: Commit**

```bash
git add -A client
git commit -m "Ask for the server address at sign-in and check it is a PersonalMoney server"
```

---

### Task 14: Never mix two ledgers on one phone

Signing out already wipes the local database (`AuthRepository.endSession`). The gap is a session that ended on its own — a refresh token that expired clears the tokens but not the data — followed by signing in to a different server or account.

**Files:**
- Modify: `commonMain/.../core/auth/{TokenStore.kt, AuthRepository.kt}`, `androidMain/.../core/auth/AndroidTokenStore.kt`, `commonMain/.../feature/auth/{AuthViewModel.kt, AuthScreen.kt}`, `commonTest/.../core/auth/AuthFakes.kt`, strings (3 files)
- Test: `commonTest/kotlin/com/personalmoney/core/auth/AccountSwitchTest.kt`

**Interfaces:**
- Consumes: `ServerCheck`, `AuthRepository.login(baseUrl, email, password)` (Task 13).
- Produces: `TokenStore.lastAccount: String?` (survives `clear()`); `AuthRepository.wouldSwitchAccount(baseUrl: String, email: String): Boolean`; `AuthUiState.confirmSwitch: Boolean`; `AuthViewModel.confirmSwitch()`, `AuthViewModel.cancelSwitch()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.personalmoney.core.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountSwitchTest {

    private val tokens = """{"token":"a","refreshToken":"r","email":"me@example.com"}"""
    private fun engine() = MockEngine {
        respond(tokens, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }

    @Test
    fun `signing in to another server wipes what the phone held`() = runTest {
        val store = FakeTokenStore(signedIn = false).apply { lastAccount = "https://old.example.com/|me@example.com" }
        val wiper = RecordingWiper()
        val auth = authRepositoryOver(engine(), store, wiper)

        assertTrue(auth.wouldSwitchAccount("https://new.example.com/", "me@example.com"))
        auth.login("https://new.example.com/", "me@example.com", "password-123")

        assertEquals(1, wiper.wipes)
        assertEquals("https://new.example.com/|me@example.com", store.lastAccount)
    }

    @Test
    fun `signing back in to the same account keeps the local data`() = runTest {
        val store = FakeTokenStore(signedIn = false).apply { lastAccount = "https://money.example.com/|me@example.com" }
        val wiper = RecordingWiper()
        val auth = authRepositoryOver(engine(), store, wiper)

        assertFalse(auth.wouldSwitchAccount("https://money.example.com/", "ME@example.com "))
        auth.login("https://money.example.com/", "me@example.com", "password-123")

        assertEquals(0, wiper.wipes)
    }
}
```

Run: `./gradlew.bat testDebugUnitTest --no-daemon --tests "com.personalmoney.core.auth.AccountSwitchTest"`
Expected: FAIL to compile — `lastAccount`, `wouldSwitchAccount` do not exist.

- [ ] **Step 2: Track the last account and wipe on a switch**

- `TokenStore`: add `/** "<server>|<email>" of the last successful sign-in. Kept by [clear]. */ var lastAccount: String?`; implement it in `AndroidTokenStore` (`KEY_LAST_ACCOUNT = "last_account"`, not removed by `clear()`) and in `FakeTokenStore` (`override var lastAccount: String? = null`).
- `AuthRepository`:

```kotlin
    private fun accountKey(baseUrl: String, email: String) = "$baseUrl|${email.trim().lowercase()}"

    /** Whether signing in here would put a different ledger on top of what the phone holds. */
    fun wouldSwitchAccount(baseUrl: String, email: String): Boolean {
        val last = tokenStore.lastAccount ?: return false
        return last != accountKey(baseUrl, email)
    }
```

and in `login`, after the API call succeeds and before `tokenStore.save(...)`:

```kotlin
        if (wouldSwitchAccount(baseUrl, email)) localData.wipe()
        tokenStore.lastAccount = accountKey(baseUrl, email)
```

Run the test again — Expected: PASS.

- [ ] **Step 3: Confirm before wiping**

`AuthViewModel`: `AuthUiState` gains `val confirmSwitch: Boolean = false`. In `submit()`, after a successful `checkServer` and before calling `login`, if `authRepository.wouldSwitchAccount(check.baseUrl, current.email) && !switchConfirmed`, set `busy = false, confirmSwitch = true` and return. Add a private `var switchConfirmed = false`, `fun confirmSwitch() { switchConfirmed = true; _state.update { it.copy(confirmSwitch = false) }; submit() }`, and `fun cancelSwitch() = _state.update { it.copy(confirmSwitch = false) }`; reset `switchConfirmed = false` whenever the server address or email changes.

`AuthScreen`: when `state.confirmSwitch`, show the app's existing `ConfirmDialog` (as used by the delete dialogs in `CategoryEditScreen`) with `auth_switch_title`, `auth_switch_body`, confirm label `auth_switch_confirm`, `onConfirm = viewModel::confirmSwitch`, `onDismiss = viewModel::cancelSwitch`.

Strings — en / ru / uk:

```xml
    <string name="auth_switch_title">Sign in to a different account?</string>
    <string name="auth_switch_body">This phone still holds data from another account or server. It will be removed from the phone (not from that server) before signing in.</string>
    <string name="auth_switch_confirm">Remove and sign in</string>
```

```xml
    <string name="auth_switch_title">Войти в другой аккаунт?</string>
    <string name="auth_switch_body">На телефоне остались данные другого аккаунта или сервера. Перед входом они будут удалены с телефона (не с того сервера).</string>
    <string name="auth_switch_confirm">Удалить и войти</string>
```

```xml
    <string name="auth_switch_title">Увійти в інший акаунт?</string>
    <string name="auth_switch_body">На телефоні залишилися дані іншого акаунта або сервера. Перед входом їх буде видалено з телефона (не з того сервера).</string>
    <string name="auth_switch_confirm">Видалити й увійти</string>
```

- [ ] **Step 4: Build and test**

Run: `./gradlew.bat testDebugUnitTest assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A client
git commit -m "Clear the phone's ledger before signing in to a different server or account"
```

---

# Phase D — Landing site, repository, CI

### Task 15: The landing site in `site/`

personalmoney.app stops serving the app and becomes the project's page: what it is, and how to install it on a NAS or home server.

**Files:**
- Create: `site/package.json`, `site/vite.config.ts`, `site/tsconfig.json`, `site/index.html`, `site/tailwind.config.js`, `site/postcss.config.js`, `site/src/main.tsx`, `site/src/App.tsx`, `site/src/i18n.ts`, `site/src/locales/{en,ru,uk}.ts`, `site/src/index.css`, `site/src/install.ts`, `site/src/App.test.tsx`, `site/public/{favicon.ico,icon.svg,privacy.html}`
- Modify: `deploy/docker-compose.site.yml` (moved here in Task 8), `deploy/personalmoney.caddy`, `deploy/README.md`; delete `deploy/init-env.sh` if Task 8 did not

**Interfaces:**
- Consumes: the old landing page and its strings, from `master` (`git show master:src/main/frontend/src/landing/Landing.tsx`, `git show master:src/main/frontend/src/i18n/locales/en.ts` and the `ru`/`uk` files), the CSS tokens in `src/main/frontend/src/index.css`.
- Produces: `npm run build` in `site/` writes static files to `site/dist/`; a container `personalmoney-site` on the VPS `edge` network serves them.

- [ ] **Step 1: Scaffold the project**

`site/package.json`:

```json
{
  "name": "personalmoney-site",
  "private": true,
  "version": "0.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc --noEmit && vite build",
    "test": "vitest run"
  },
  "dependencies": {
    "lucide-react": "<same version as src/main/frontend/package.json>",
    "react": "<same>",
    "react-dom": "<same>"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "<same>",
    "@testing-library/react": "<same>",
    "@types/react": "<same>",
    "@types/react-dom": "<same>",
    "@vitejs/plugin-react": "<same>",
    "autoprefixer": "<same>",
    "jsdom": "<same>",
    "postcss": "<same>",
    "tailwindcss": "<same>",
    "typescript": "<same>",
    "vite": "<same>",
    "vitest": "<same>"
  }
}
```

Copy each version string from `src/main/frontend/package.json` exactly (one toolchain across both projects), then `cd site && npm install` to produce `package-lock.json`.

`site/vite.config.ts`:

```ts
/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],
    build: { outDir: 'dist', sourcemap: false },
    test: { environment: 'jsdom', globals: true, setupFiles: ['./src/test-setup.ts'] },
});
```

`site/src/test-setup.ts`: `import '@testing-library/jest-dom/vitest';`

Copy `tsconfig.json`, `tailwind.config.js`, `postcss.config.js` from `src/main/frontend/` and point their `content`/`include` globs at `site/`'s `index.html` and `src/`. Copy `src/main/frontend/src/index.css`'s token block (`:root` and the light-theme override) into `site/src/index.css` together with the `@tailwind` directives; drop app-only component classes.

- [ ] **Step 2: Write the install content once, as data**

`site/src/install.ts` — the commands are the same in every language, so they live here and only prose goes through i18n:

```ts
export const REPO = 'https://github.com/hteariH/HomeBudgetingApp';
export const RELEASES = `${REPO}/releases/latest`;

export const COMPOSE_STEPS = [
    'mkdir personalmoney && cd personalmoney',
    `curl -fsSLO ${REPO}/releases/latest/download/docker-compose.yml`,
    `curl -fsSL ${REPO}/releases/latest/download/.env.example -o .env`,
    '# set DB_PASSWORD in .env (e.g. openssl rand -base64 24)',
    'docker compose up -d',
];

export const UPDATE_STEPS = ['docker compose pull', 'docker compose up -d'];

export const BACKUP_STEPS = [
    'docker compose exec db pg_dump -U personalmoney personalmoney > personalmoney-$(date +%F).sql',
    '# and copy ./data (it holds the generated secrets)',
];

export const RESET_PASSWORD = 'docker compose exec app personalmoney reset-password you@example.com';

/** One entry per platform tab; `body` is an i18n key prefix: `<prefix>.1`, `<prefix>.2`, ... */
export const PLATFORMS = [
    { id: 'compose', steps: 4 },
    { id: 'synology', steps: 4 },
    { id: 'unraid', steps: 3 },
    { id: 'truenas', steps: 3 },
    { id: 'portainer', steps: 3 },
] as const;
```

- [ ] **Step 3: Write the failing test**

`site/src/App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import App from './App';
import { en } from './locales/en';
import { ru } from './locales/ru';
import { uk } from './locales/uk';
import { COMPOSE_STEPS } from './install';

it('shows how to install with docker compose', () => {
    render(<App />);
    expect(screen.getByText(en['install.title'])).toBeInTheDocument();
    expect(screen.getByText(COMPOSE_STEPS[4])).toBeInTheDocument();
});

it('has every key in every language', () => {
    const keys = Object.keys(en).sort();
    expect(Object.keys(ru).sort()).toEqual(keys);
    expect(Object.keys(uk).sort()).toEqual(keys);
});

it('never offers a hosted account', () => {
    render(<App />);
    expect(document.body.innerHTML).not.toMatch(/\/register|\/login|Premium/);
});
```

Run: `cd site && npm test` — Expected: FAIL (`./App` does not exist).

- [ ] **Step 4: Build the page**

`site/src/i18n.ts`: a ~30-line provider — `type Lang = 'en' | 'ru' | 'uk'`, language from `localStorage.getItem('lang')` then `navigator.languages`, falling back to `en`; `useT()` returns `(key: keyof typeof en) => dictionary[lang][key] ?? en[key]`; a `LanguageSwitcher` with the three codes. Mirror the behaviour of `src/main/frontend/src/i18n/index.tsx` without its plural machinery (the site needs none).

`site/src/locales/en.ts` exports `en` with these key groups (write every key; `ru.ts`/`uk.ts` export `ru`/`uk` with the same keys, translated in the tone of the old landing strings):

- `meta.title` — "PersonalMoney — self-hosted personal and family finances"
- `hero.title`, `hero.accent`, `hero.subtitle` — keep the old `landing.hero.*` wording where it still holds; the subtitle says: open source (AGPL-3.0), runs on your NAS or home server, your data never leaves it, bring your own AI model for receipts (coming soon).
- `hero.ctaInstall` ("Install it"), `hero.ctaSource` ("Source code")
- `features.title` and `features.<n>.title` / `features.<n>.body` for: accounts and multi-currency with automatic rates; fast entry and transfers; bank import (Monobank) and CSV statements; analytics and budgets; one ledger for the whole family (coming soon); receipts read by your own AI model (coming soon) — reuse the old `landing.features.*` copy for the first four.
- `install.title`, `install.lead` ("You need a machine that runs Docker: a NAS, a mini PC, a VPS."), `install.tab.<platform>` for the five platforms, and `install.<platform>.1..n` step sentences:
  - compose: create a folder; download the compose file and environment; set `DB_PASSWORD`; start it, open `http://<machine>:8080` and create the administrator account.
  - synology: install Container Manager; Project → Create, path `/docker/personalmoney`, upload `docker-compose.yml`; put `.env` next to it with `DB_PASSWORD`; build and open port 8080.
  - unraid: install the Docker Compose Manager plugin; add a stack with the compose file and `.env`; Compose Up.
  - truenas: Apps → Discover → Custom App → "Install via YAML"; paste the compose file with `DB_PASSWORD` filled in; open port 8080.
  - portainer: Stacks → Add stack → upload the compose file; add `DB_PASSWORD` under environment variables; deploy.
- `install.https.title` / `install.https.body` — put it behind your reverse proxy (Caddy, Traefik, Nginx Proxy Manager) or reach it over Tailscale; set `APP_BASE_URL` to the public address if mail is configured.
- `install.update.title`, `install.backup.title`, `install.backup.body` ("The database dump and the ./data folder are everything. Losing ./data/secrets signs everyone out and makes stored bank connections unreadable."), `install.reset.title` ("Locked out?") with `install.reset.body`.
- `android.title`, `android.body` ("Download the APK from the latest release and enter your server's address when signing in."), `android.cta`.
- `ai.title`, `ai.body` — coming soon: connect any OpenAI-compatible endpoint (Ollama, LM Studio, vLLM, OpenRouter), Anthropic or Gemini to read receipts; MCP for your own assistant.
- `footer.license` ("Open source under AGPL-3.0"), `footer.source`, `footer.privacy`.

`site/src/App.tsx`: one page with sections `#hero`, `#features`, `#install` (platform tabs; each step list renders the `install.<platform>.<n>` sentences, and the compose tab also renders `COMPOSE_STEPS` in a `<pre><code>` block, one line per element), `#update` (`UPDATE_STEPS`), `#backup` (`BACKUP_STEPS`, `RESET_PASSWORD`), `#android` (link to `RELEASES`), `#ai`, footer (links to `REPO`, `${REPO}/blob/master/LICENSE`, `/privacy.html`). Take the layout, header, theme toggle and visual style from the old `Landing.tsx` (`git show master:src/main/frontend/src/landing/Landing.tsx`), dropping everything that pointed at `/register`, `/login` or `/app`.

`site/index.html`: title from `meta.title` in English, the favicon/icon copied from `src/main/frontend/public/`.

`site/public/privacy.html`: a short page — PersonalMoney is software you run yourself; whoever runs an instance controls its data; the project collects nothing; the Android app talks only to the server you enter (and to the public Frankfurter exchange-rate API); receipt photos are processed on the device today, and later by the AI provider the instance's owner configures.

Run: `cd site && npm test && npm run build` — Expected: PASS, and `site/dist/index.html` exists.

- [ ] **Step 5: Serve it on the VPS**

`deploy/docker-compose.site.yml`:

```yaml
# The project's page at personalmoney.app: static files behind the shared edge Caddy.
# The files are copied to /opt/personalmoney-site/dist by the site workflow.
services:
  site:
    image: nginx:1.27-alpine
    container_name: personalmoney-site
    restart: unless-stopped
    volumes:
      - ./dist:/usr/share/nginx/html:ro
    networks:
      edge:
        aliases:
          - personalmoney-site

networks:
  edge:
    external: true
```

`deploy/personalmoney.caddy` — delete the `/app-ads.txt` handler (no ads any more) and point the remaining `handle` at the site:

```
	handle {
		reverse_proxy personalmoney-site:80
	}
```

keeping the `encode`, security `header` block and the `www` redirect. Rewrite `deploy/README.md` to describe only this: DNS, the edge network, `site.yml` copying `dist/` and the two config files, and — once — stopping the old app stack on the VPS by hand (`cd /opt/homebudget && sudo docker compose down`; its database volume can be removed after a final `mysqldump` if the owner wants a copy).

- [ ] **Step 6: Commit**

```bash
git add -A site deploy
git commit -m "Move the landing page to its own site with installation instructions"
```

---

### Task 16: Licence, README and the repository's own documents

**Files:**
- Create: `LICENSE`, `CONTRIBUTING.md`, `SECURITY.md`
- Modify: `README.md` (rewrite), `src/main/frontend/public/privacy.html` (rewrite), `client/composeApp/build.gradle.kts` (`privacyPolicyUrl` default), `client/keystore.properties.example`, `client/CLAUDE.md`, `client/docs/{PRIVACY_POLICY.md,DATA_SAFETY.md,RELEASE_CHECKLIST.md,STORE_LISTING.md}` (delete the Play-only ones, see Step 4)

**Interfaces:**
- Produces: the README sections other docs link to — `#install`, `#configuration`, `#updating`, `#backups`, `#development`.

- [ ] **Step 1: Licence**

```bash
curl -fsSL https://www.gnu.org/licenses/agpl-3.0.txt -o LICENSE
```

Check the first line reads `GNU AFFERO GENERAL PUBLIC LICENSE`.

- [ ] **Step 2: README for self-hosters**

Replace `README.md` with these sections, in this order:

1. **PersonalMoney** — one paragraph: self-hosted, open-source personal and family finances; web app and Android app; your server, your data. Link to personalmoney.app.
2. **Features** — the same list as the site (mark family sharing and AI receipts as planned).
3. **Install** (`## Install`) — the `COMPOSE_STEPS` commands from `site/src/install.ts` as one bash block; "open `http://<machine>:8080`; the first account becomes the administrator"; a link to the site for Synology/Unraid/TrueNAS/Portainer.
4. **Configuration** (`## Configuration`) — a table of every variable in `.env.example` with default and meaning; a paragraph on `/data/secrets`.
5. **HTTPS** — reverse proxy or Tailscale; a minimal Caddy example:

```
money.example.com {
	reverse_proxy <machine>:8080
}
```

6. **Updating** (`## Updating`) — `docker compose pull && docker compose up -d`; migrations run on start.
7. **Backups** (`## Backups`) — the `pg_dump` command and `./data`; restoring is `psql < dump.sql` into a fresh `db` container before starting `app`.
8. **Locked out** — the `reset-password` command.
9. **Android** — APK from Releases; enter the server address; plain http warning; installing a CA for self-signed certificates.
10. **Development** (`## Development`) — keep the current "Setup & Run → Development Mode", "Scripts" and "Tests" content, updated: JDK 25 toolchain with bytecode target 21, H2 in PostgreSQL mode for `local`, Docker needed only for the Testcontainers suite, the `client/` Gradle commands from `client/CLAUDE.md`.
11. **Project structure** — the current tree, plus `site/`, `docker/`, `deploy/`.
12. **License** — AGPL-3.0, one sentence on what it means for someone running a modified version as a service.

Delete the old "Environment Variables" (MariaDB) section and the TODO list.

- [ ] **Step 3: CONTRIBUTING and SECURITY**

`CONTRIBUTING.md`: how to run the three test suites (commands from "Commands used throughout"), the conventions already in the code (comments explain why, money in minor units, every schema change is a new Flyway migration once released, every UI string in en/ru/uk), and that contributions are accepted under AGPL-3.0.

`SECURITY.md`: report vulnerabilities privately through GitHub's "Report a vulnerability" (private advisories) on the repository, not in issues; supported versions: the latest release; what is in scope (the server, the web app, the Android app, the image).

- [ ] **Step 4: Privacy and the Play-era documents**

- `src/main/frontend/public/privacy.html`: rewrite as the instance's own page — "This PersonalMoney instance is run by its owner, not by the PersonalMoney project. Your data is stored on their server…", then what the software stores (account email, name, password hash, the ledger, bank connection credentials encrypted with the instance's key), what it sends elsewhere (Frankfurter for exchange rates; your bank's API when you connect one; HomeMoney when you import from it; mail through the owner's SMTP server if configured), and that there are no ads, analytics or trackers. Remove every Google Play / AdMob / billing section.
- `client/composeApp/build.gradle.kts`: `privacyPolicyUrl` default becomes `"https://personalmoney.app/privacy.html"`.
- `client/keystore.properties.example`: delete the AdMob block and the Play Console wording on `privacyPolicyUrl`.
- `client/docs/DATA_SAFETY.md` and `client/docs/STORE_LISTING.md` describe the Play Store listing: delete them (`git rm`). `client/docs/RELEASE_CHECKLIST.md`: rewrite as the GitHub-release checklist (tag, wait for `release.yml`, check the image runs on amd64 and arm64, install the APK over the previous one). `client/docs/PRIVACY_POLICY.md`: delete; the site's `privacy.html` replaces it.
- `client/CLAUDE.md`: replace "Monetised with AdMob" and the Play Billing note with "No ads, no purchases: the app is a client of a self-hosted server whose address the user enters at sign-in"; drop AdMob + UMP from the stack line.

- [ ] **Step 5: Secret scan of the whole history**

If `gitleaks` is installed: `gitleaks detect --source . --log-opts="--all" --redact -v`. Otherwise: `docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:latest detect --source /repo --log-opts="--all" --redact`.
Expected: "no leaks found". If anything is found, stop and report it to the owner with the commit and file — do not rewrite history.

- [ ] **Step 6: Commit**

```bash
git add -A LICENSE README.md CONTRIBUTING.md SECURITY.md src/main/frontend/public/privacy.html client
git commit -m "License the project under AGPL-3.0 and rewrite the docs for self-hosters"
```

---

### Task 17: CI, releases and the site workflow

**Files:**
- Delete: `.github/workflows/deploy.yml`
- Create: `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `.github/workflows/site.yml`
- Modify: `client/composeApp/build.gradle.kts` (`versionCode`/`versionName` from Gradle properties)

**Interfaces:**
- Consumes: the `revision` Maven property (Task 5), the `APP_VERSION` Docker build arg (Task 8), `site/` (Task 15).
- Produces: on a `vX.Y.Z` tag — `ghcr.io/htearih/personalmoney:X.Y.Z` and `:latest` for amd64+arm64, and a GitHub Release with `personalmoney-X.Y.Z.apk`, `docker-compose.yml`, `.env.example`. Repository secrets the owner must create before the first tag: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` (release), `SSH_HOST`, `SSH_USER`, `SSH_KEY`, `SSH_PORT` (site; these exist already for the old deploy).

- [ ] **Step 1: Version the APK from the tag**

`client/composeApp/build.gradle.kts`, in `defaultConfig`:

```kotlin
        // Release builds pass these from the git tag; local builds keep a dev version.
        versionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("appVersionName") as String?) ?: "0.0.0-dev"
```

- [ ] **Step 2: CI on every push and pull request**

```bash
git rm .github/workflows/deploy.yml
```

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [master]
  pull_request:

jobs:
  server:
    name: Server
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      # Backend only: the React bundle is built by the image. Docker is available on the
      # runner, so the Testcontainers Postgres suite runs here.
      - run: ./mvnw -B -Dfrontend.skip=true test

  web:
    name: Web app
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: src/main/frontend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: src/main/frontend/package-lock.json
      - run: npm ci
      - run: npm run typecheck
      - run: npm test

  site:
    name: Landing site
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: site
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: site/package-lock.json
      - run: npm ci
      - run: npm test
      - run: npm run build

  android:
    name: Android
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      # `packages` named explicitly: the action's default also asks for the legacy
      # `tools` package, which current cmdline-tools no longer carry.
      - uses: android-actions/setup-android@v3
        with:
          packages: platform-tools
      - uses: gradle/actions/setup-gradle@v4
      - working-directory: client
        run: ./gradlew testDebugUnitTest assembleDebug

  secrets:
    name: Secret scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

- [ ] **Step 3: Releases on a tag**

`.github/workflows/release.yml`:

```yaml
name: Release

on:
  push:
    tags: ['v*.*.*']

permissions:
  contents: write
  packages: write

jobs:
  image:
    name: Image
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - id: version
        run: echo "value=${GITHUB_REF_NAME#v}" >> "$GITHUB_OUTPUT"
      - uses: docker/setup-qemu-action@v3
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v6
        with:
          context: .
          platforms: linux/amd64,linux/arm64
          push: true
          build-args: APP_VERSION=${{ steps.version.outputs.value }}
          tags: |
            ghcr.io/htearih/personalmoney:${{ steps.version.outputs.value }}
            ghcr.io/htearih/personalmoney:latest
          labels: |
            org.opencontainers.image.source=https://github.com/${{ github.repository }}
            org.opencontainers.image.licenses=AGPL-3.0-only
            org.opencontainers.image.version=${{ steps.version.outputs.value }}

  apk:
    name: Android APK
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: android-actions/setup-android@v3
        with:
          packages: platform-tools
      - uses: gradle/actions/setup-gradle@v4
      - name: Signing key
        working-directory: client
        env:
          KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
          STORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 -d > release.keystore
          printf 'storeFile=release.keystore\nstorePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' \
            "$STORE_PASSWORD" "$KEY_ALIAS" "$KEY_PASSWORD" > keystore.properties
      - name: Build
        working-directory: client
        run: |
          VERSION="${GITHUB_REF_NAME#v}"
          ./gradlew assembleRelease -PappVersionName="$VERSION" -PappVersionCode="${{ github.run_number }}"
          cp "$(find composeApp/build/outputs/apk/release -name '*.apk' | head -n1)" "../personalmoney-$VERSION.apk"
      - uses: actions/upload-artifact@v4
        with:
          name: apk
          path: personalmoney-*.apk

  publish:
    name: GitHub Release
    needs: [image, apk]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/download-artifact@v4
        with:
          name: apk
      - uses: softprops/action-gh-release@v2
        with:
          generate_release_notes: true
          files: |
            personalmoney-*.apk
            docker-compose.yml
            .env.example
```

The GHCR package is private by default the first time it is pushed; the owner makes it public under the package's settings (note this in `client/docs/RELEASE_CHECKLIST.md`).

- [ ] **Step 4: The site workflow**

`.github/workflows/site.yml`:

```yaml
name: Site

on:
  push:
    branches: [master]
    paths: ['site/**', 'deploy/docker-compose.site.yml', 'deploy/personalmoney.caddy']

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: site/package-lock.json
      - working-directory: site
        run: npm ci && npm test && npm run build
      - name: Copy the site
        uses: appleboy/scp-action@v0.1.7
        with:
          host: ${{ secrets.SSH_HOST }}
          username: ${{ secrets.SSH_USER }}
          key: ${{ secrets.SSH_KEY }}
          port: ${{ secrets.SSH_PORT || 22 }}
          source: site/dist,deploy/docker-compose.site.yml
          target: /opt/personalmoney-site
      - name: Copy the edge config
        uses: appleboy/scp-action@v0.1.7
        with:
          host: ${{ secrets.SSH_HOST }}
          username: ${{ secrets.SSH_USER }}
          key: ${{ secrets.SSH_KEY }}
          port: ${{ secrets.SSH_PORT || 22 }}
          source: deploy/personalmoney.caddy
          target: /opt/edge/conf.d
          strip_components: 1
      - name: Start and reload
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.SSH_HOST }}
          username: ${{ secrets.SSH_USER }}
          key: ${{ secrets.SSH_KEY }}
          port: ${{ secrets.SSH_PORT || 22 }}
          script: |
            set -e
            cd /opt/personalmoney-site
            rm -rf dist && mv site/dist dist && rm -rf site
            sudo docker compose -f deploy/docker-compose.site.yml --project-directory . up -d
            sudo docker exec edge-caddy caddy reload --config /etc/caddy/Caddyfile
```

- [ ] **Step 5: Validate the workflows**

If `actionlint` is available, run `actionlint .github/workflows/*.yml` — Expected: no errors. Otherwise check the YAML parses: `python -c "import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]" .github/workflows/*.yml`.

Do not push a tag. The first release is the owner's step.

- [ ] **Step 6: Commit**

```bash
git add -A .github client/composeApp/build.gradle.kts
git commit -m "Run CI on every push, release images and the APK on tags, deploy the site"
```

---

## After the last task

- Run all three suites once more from a clean checkout of the branch: `./mvnw -B -Dfrontend.skip=true test`, `cd src/main/frontend && npm ci && npm test`, `cd site && npm ci && npm test && npm run build`, and the Android build.
- Then use superpowers:finishing-a-development-branch. Merging to `master` triggers `site.yml`, which repoints personalmoney.app at the site; the old app stack on the VPS keeps running unrouted until the owner stops it (`deploy/README.md`).
- Owner-only steps, never done by an agent: creating the release secrets, making the repository and the GHCR package public, pushing the first `v1.0.0` tag, stopping the old stack on the VPS.
