# Open-source baseline

The first sub-project of the [self-hosted pivot](2026-10-15-self-hosted-pivot-overview.md):
turn PersonalMoney from a hosted service with a paid tier into something a stranger can
install on a NAS with `docker compose up`, administer, and use with their family.

## Why

Today the server, the deploy and the clients assume one operator (us), one domain
(`personalmoney.app`), one mail provider (Resend), one payment rail (Google Play) and one
user per ledger. A self-hosted instance has none of those: its operator is its user, its
address is a LAN IP or their own domain, mail may not exist, nothing is sold, and a family
shares one ledger.

## Decisions

- **Monetisation goes, entirely.** No flag keeps it alive for a hosted edition.
- **Households from the first migration.** Every piece of ledger data belongs to a
  household. Here each user gets exactly one household, created with them; sharing it with
  others is sub-project 2. Building it into the baseline means installed instances never
  need a user-to-household data migration.
- **Postgres only.** One clean `V1` replaces `V1`–`V6`; no data is carried over.
- **The landing page leaves the app.** It moves to `site/` and is deployed on its own to
  personalmoney.app. The self-hosted image serves only the app and its sign-in pages.
- **Zero-config start.** Required secrets are generated on first boot if absent.
- **AGPL-3.0.**

## Removed

Server:

- `billing/` (all seven files), `play_purchases`, `PLAY_*` settings.
- `Entitlement`, `EntitlementSource`, `PlanLimits`, `User.isPremium`, the entitlement
  columns on `users`, `PlanLimitException` and its three call sites (REST account
  creation, sync push, HomeMoney import).
- `docs/PREMIUM.md`.
- `flyway-mysql`, `mysql-connector-j`, `mariadb-java-client`.
- Resend as the default mail host.

Web (`src/main/frontend`):

- `app/features/premium` and every entry point to it.
- `landing/Landing.tsx` and the marketing copy in the dictionaries; `/` redirects to
  `/app` (which redirects to sign-in when there is no session).

Android (`client/`):

- `ads/`, `core/billing/`, `feature/premium/`, the AdMob, UMP and Play Billing
  dependencies, the consent flow, the ad unit ids in `build.gradle.kts`.
- `API_BASE_URL` build config.
- The advertising and purchase sections of `privacy.html`; the privacy page becomes a
  statement that an instance's data stays with whoever runs it.

## Optional integrations

Each is off unless its settings are present, and `/api/instance` says which are on so the
clients only show what works.

| Integration | Enabled by | Without it |
| --- | --- | --- |
| Mail | `MAIL_HOST` (plus `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`) | Email verification is not asked for. "Forgot password" is hidden; an administrator issues a reset link instead. |
| Monobank corporate | `MONOBANK_KEY_ID`, `MONOBANK_PRIVATE_KEY` | The provider is not offered. Monobank with a personal token always works. |
| HomeMoney | `HOMEMONEY_CLIENT_ID`, `HOMEMONEY_CLIENT_SECRET` | Falls back to HomeMoney's published demo client, as today. |

## Data model

New tables:

- `households` — `id`, `uuid`, `name`, `base_currency_id`, `created_at`, `updated_at`.
- `household_members` — `household_id`, `user_id`, `role` (`OWNER` | `MEMBER`),
  `joined_at`; primary key `(household_id, user_id)`. In this sub-project a user is a
  member of exactly one household.

Moved from `user_id` to `household_id`:

- `accounts`, `categories`, `subscriptions`, `transactions`
- `user_currency_settings`, renamed `household_currency_settings`
- manual rows of `exchange_rates` (global Frankfurter rows keep a null owner)
- `bank_connections`, which also gains `connected_by_user_id`. Any member may import
  from or remove a connection.

Stays on `user_id`: `refresh_tokens`, `auth_tokens`.

Also:

- `transactions.created_by_user_id` (not null), filled from the authenticated user on
  every write path (REST, sync push, bank and CSV import, HomeMoney). Shown in the UI from
  sub-project 2.
- `users` loses the entitlement columns and `base_currency_id` (now on the household).
  `role` stays: `ADMIN` means administrator of the instance.

Migrations become `V1__baseline.sql` (Postgres DDL) and `V2__seed_currency_catalog.sql`.
Types are Postgres-native: `BIGINT GENERATED ALWAYS AS IDENTITY`, `TIMESTAMPTZ` for
instants, `NUMERIC` for rates, `BOOLEAN`.

## Server

**Household scoping.** `SecurityUtils` exposes the current household id alongside the
user id. Every repository query that filtered by user filters by household, and every
service resolves ownership through it. A request for another household's row answers
`404`, as another user's row does today.

**Sync.** Pull and push work on the household's data. `SyncCursorRepository` computes the
latest `updated_at` per household rather than per user. Because two members can now push
at the same moment, a push takes a row lock on its `households` row
(`SELECT … FOR UPDATE`) before stamping, so stamps within a household stay strictly
increasing and a pull with `updated_at > cursor` cannot skip a write.

**Registration and first run.**

- `REGISTRATION` = `open` | `closed`. Unset means: open while there are no users,
  closed afterwards. (An `invite` mode arrives with sub-project 2.)
- While there are no users, registration creates the first account with role `ADMIN`.
  The web app detects this through `/api/instance` (`setupRequired: true`) and shows
  "Create the administrator account" instead of the sign-in page.
- Registering creates the user, their household (named after them, base currency chosen
  in onboarding as today) and an `OWNER` membership in one transaction.

**Secrets.** `JWT_SECRET` and `CRYPTO_SECRET` are read from the environment; if either is
empty, the server generates 32 random bytes, writes them to `/data/secrets` (mode `600`),
and reads them from there on every later start. An environment value always wins. Losing
`/data/secrets` with no env value signs everyone out and makes stored bank credentials
unreadable; the README says so next to the backup instructions.

**Instance endpoint.** `GET /api/instance`, unauthenticated:

```json
{
  "name": "PersonalMoney",
  "version": "1.0.0",
  "apiVersion": 1,
  "setupRequired": false,
  "registrationOpen": false,
  "mailEnabled": true,
  "bankProviders": ["MONOBANK_PERSONAL", "CSV", "HOMEMONEY"]
}
```

`apiVersion` is an integer the clients compare against the range they support.

**Administration.** `ADMIN`-only endpoints under `/api/admin`:

- list users (email, name, created, last sign-in, household);
- set the registration mode at runtime (stored in the database, overriding the default
  but not an explicit `REGISTRATION` env value);
- issue a one-time password reset link for a user (returned to the administrator, valid
  for 24 hours, works without mail);
- delete a user. Deleting the last `OWNER` of a household deletes the household and its
  data, behind the same confirmation as account deletion today.

The existing `AdminEntitlementController` is deleted along with billing.

**CLI.** `docker compose exec app personalmoney reset-password <email>` prints a reset
link, for an administrator who has lost their own password. Implemented as a Spring
`ApplicationRunner` behind a command-line flag, with a small wrapper script in the image.

**Health.** `spring-boot-starter-actuator` with only `/actuator/health` exposed.

## Web

- `/` redirects to `/app`.
- A setup screen when `setupRequired` is true.
- The register link is shown only when `registrationOpen` is true; "Forgot password" only
  when `mailEnabled` is true.
- `/app/admin` for administrators: users table, registration toggle, "Issue reset link",
  delete user.
- The app's version from `/api/instance` in the settings screen footer.
- The premium screen, paywall and the upgrade entries are gone.

## Landing site

`site/` is a separate Vite project (static output) that reuses the app's CSS tokens and
the en/ru/uk dictionary format. Pages:

- what PersonalMoney is, with screenshots;
- installation: Docker Compose, then Synology, Unraid, TrueNAS and Portainer variants;
  putting it behind a reverse proxy; updating; backups;
- connecting the Android app (APK link to the latest GitHub release);
- AI receipt providers (added with sub-project 3);
- links to the GitHub repository and the licence.

It is deployed as static files to the VPS behind the existing edge Caddy, replacing the
app stack there.

## Packaging

**Image.** Multi-arch (`linux/amd64`, `linux/arm64`), published to
`ghcr.io/htearih/personalmoney` as `latest` and `vX.Y.Z`. Built on a Java 25 JRE to match
the toolchain. Runs as a non-root user, listens on `8080`, keeps its state in `/data`,
has a `HEALTHCHECK` on `/actuator/health`.

**`docker-compose.yml`** at the repository root is the self-hoster's file:

```yaml
services:
  app:
    image: ghcr.io/htearih/personalmoney:latest
    ports: ["8080:8080"]
    env_file: .env
    environment:
      DB_HOST: db
    volumes: ["./data:/data"]
    depends_on:
      db: { condition: service_healthy }
    restart: unless-stopped
  db:
    image: postgres:17
    environment:
      POSTGRES_DB: personalmoney
      POSTGRES_USER: personalmoney
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes: ["./postgres:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U personalmoney"]
    restart: unless-stopped
```

`.env.example` has `DB_PASSWORD` as the only required value, then the optional settings
in commented groups. The current VPS compose file with the external `edge` network moves
to `deploy/` and is used only for the landing site.

**Profiles.** `local` keeps a zero-setup H2 file database, now in `MODE=PostgreSQL`.
`prod` reads `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.

## Android

- The sign-in screen gains a "Server address" field, remembered in DataStore. On submit
  the app calls `GET /api/instance`: an unreachable address, a response that is not ours,
  or an `apiVersion` outside the supported range each get their own message.
- Plain `http://` is allowed, with a visible "traffic to this server is not encrypted"
  warning; the network security config trusts user-installed CAs so self-signed
  certificates work once installed on the phone.
- Signing in to a different server or account clears the local Room database after a
  confirmation, so two ledgers never mix.
- Registration and "forgot password" follow `/api/instance`, as on the web.
- Removed: everything listed under "Removed" above. Tesseract stays until sub-project 3.
- Released as a signed APK on GitHub Releases. The application id is unchanged.

## Repository

- `LICENSE` (AGPL-3.0), a README rewritten for self-hosters (install, configure, update,
  back up, develop), `CONTRIBUTING.md`, `SECURITY.md`.
- gitleaks over the full history before the repository is made public. A first check
  found only `keystore.properties.example` files.
- Making the repository public is a separate, explicit step by the owner, after the
  README and the gitleaks report have been reviewed.

## CI/CD

The current `deploy.yml` is replaced by:

- `ci.yml`, on every pull request and push: `mvn test` (including the Postgres
  Testcontainers suite), the frontend's `npm test` and `npm run typecheck`, `site/` build,
  `gradlew testDebugUnitTest assembleDebug`, gitleaks.
- `release.yml`, on a `v*` tag: the multi-arch image to GHCR; a GitHub Release with the
  signed APK, `docker-compose.yml` and `.env.example` attached and generated notes.
- `site.yml`, on changes under `site/`: build and deploy the landing site.

## Testing

Server:

- **Household isolation.** A member of household A cannot read, change or delete
  household B's accounts, categories, subscriptions, transactions, currency settings,
  manual rates or bank connections, through REST or sync. This is the regression net for
  the scoping refactor and is written before it.
- Concurrent pushes into one household produce distinct, increasing stamps, and a pull
  from the earlier cursor returns both.
- `created_by_user_id` is set on every write path.
- First run: the first registration becomes `ADMIN`; registration closes afterwards
  unless `REGISTRATION=open`; `setupRequired` flips.
- Secrets: generated when absent, reused on restart, environment wins.
- `/api/instance` reflects mail, Monobank corporate and registration settings.
- Admin endpoints refuse non-admins; the reset link works once and expires.
- A Testcontainers suite runs the Flyway migrations and the isolation tests against
  `postgres:17`; the rest of the suite runs on H2 in PostgreSQL mode.

Web: the setup screen, conditional register and forgot-password links, the admin page.

Android: server address validation outcomes, and the local database being cleared on a
server or account switch.

## Out of scope

- Invitations and more than one member per household (sub-project 2).
- AI receipt scanning and removing Tesseract (sub-project 3).
- MCP and personal access tokens (sub-project 4).
- F-Droid packaging, iOS, a hosted demo instance.
- Importing data from the old personalmoney.app instance.
