# MCP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A user's own AI assistant connects to `/mcp` with a personal token or through OAuth, reads the household's books and records transactions and transfers (with receipts it read itself), marked "via assistant".

**Architecture:** Server package `mcp` (SDK wiring, six tools over the existing services, token filter, rate limit, enable switch), package `token` (personal access tokens, hashed), package `oauth` (a small authorization server: metadata, open registration, authorize + consent API, token, revoke). Web gets Settings → Assistants, an OAuth consent page and the admin switch; web and Android show "via assistant" and receipts without photos.

**Tech Stack:** Kotlin/Spring Boot 3.4.3/Flyway/Postgres (H2 PostgreSQL mode in tests); MCP Java SDK `io.modelcontextprotocol.sdk:mcp-core` + `mcp-json-jackson2` 2.1.0 (servlet Streamable HTTP transport, Jackson 2, Java 17+); React 19/TanStack Query/Vitest; Compose Multiplatform/Ktor/Room.

**Spec:** [docs/superpowers/specs/2026-10-17-mcp-server-design.md](../specs/2026-10-17-mcp-server-design.md)

## Global Constraints

- Migration `V7__mcp.sql` (V1–V6 are released and not edited). Android Room 6 → 7.
- Tools exactly: `list_accounts`, `list_categories`, `search_transactions`, `summarize_spending`, `create_transaction`, `create_transfer`. No edit/delete tools, no resources, no prompts.
- Money in tool input/output: decimal string + ISO currency code.
- Personal token format: `pm_` + 40 random base62 characters (`SecureRandom`); stored as SHA-256 hex only; `prefix` = first 7 characters. Personal and OAuth tokens are accepted **only on `/mcp`**.
- OAuth: authorization code + PKCE S256 only, public clients (`token_endpoint_auth_method: none`), one scope `ledger:write`; access token 1 h, refresh 90 days rotated, code 60 s single use; all hashed (SHA-256). Redirect URIs: `https://…`, or `http://localhost[:port]` / `http://127.0.0.1[:port]`. Unused clients deleted 24 h after registration. OAuth endpoints and metadata answer 404 when `APP_BASE_URL` is empty.
- Enable switch: environment `MCP_ENABLED` (`true`/`false`, wins) over instance setting key `mcp.enabled` (absent = false). Disabled → `/mcp`, `/oauth/**`, `/.well-known/oauth-*` answer 404.
- Rate limit: 60 tool calls per minute per token → 429.
- `transactions.source`: `APP` (default) | `MCP`; set by MCP create tools only; pulled through REST/sync, never changed by a push.
- Logs: tool name, user id, duration — never arguments, amounts, notes or tokens.
- UI strings in en, ru and uk. Do not push, tag or deploy.

## Commands

- Server: `./mvnw -B -Dfrontend.skip=true test` (`-Dtest=Name`). Docker is not running; Testcontainers tests skip.
- Web: `cd src/main/frontend && npm run typecheck && npm test`.
- Site: `cd site && npm run build && npm test`.
- Android (Git Bash, from `client/`): `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; ./gradlew testDebugUnitTest assembleDebug --no-daemon`.

## Key facts about the existing code

- `SecurityUtils.getCurrentUser()` / `getCurrentHousehold()` read the Spring `SecurityContext` principal (`UserPrincipal`). Services (`AccountService.findAll()`, `CategoryService.findAll(type, includeArchived)`, `TransactionService.create(CreateTransactionRequest)`, `TransferService.create(CreateTransferRequest)`) scope by it. MCP tools must run with that context set to the token owner (see Task 1) so these services work unchanged.
- `SecurityConfig.filterChain` is one stateless chain: `/api/**` authenticated via `JwtAuthenticationFilter`; `AuthRateLimitFilter` is the pattern for a per-key limiter.
- `RegistrationService` + `AdminController` `/api/admin/registration` (GET/PUT, 409 when locked by environment) and `InstanceController`/`InstanceInfo` are the pattern for the MCP switch; `instance_settings` is a key-value table.
- `APP_BASE_URL` already exists in configuration (find its property name in `application.yml`).
- Receipts: `receipt/Receipt.kt`, `ReceiptItem.kt`, `ReceiptService.kt`, `ReceiptDtos.kt`; transactions link by a receipt id column (`Transaction.receiptId` after the recent polish) — read the current code.

---

# Phase A — Server (`src/main/kotlin/com/homebudget/app/`)

### Task 1: MCP endpoint, personal tokens, switch, rate limit, read-only listing tools

**Files:**
- Modify `pom.xml`: `io.modelcontextprotocol.sdk:mcp-core:2.1.0` and `io.modelcontextprotocol.sdk:mcp-json-jackson2:2.1.0` (property `mcp-sdk.version`). Boot manages Jackson 2.18 and Tomcat's Servlet 6.0; the SDK is built against Jackson 2.21 and Servlet 6.1 — the smoke test in Step 1 is the compatibility gate. If it fails on a missing Jackson or servlet method, try `1.1.5`; if that also fails, let Boot's `jackson-bom.version` property rise to the SDK's Jackson only if the whole server suite still passes; record which in the report.
- Create `src/main/resources/db/migration/V7__mcp.sql` (whole schema for this plan, below).
- Create `token/AccessToken.kt` (entity), `token/AccessTokenRepository.kt`, `token/AccessTokenService.kt`, `token/TokenController.kt` (`/api/assistants/tokens`), `token/TokenDtos.kt`, `token/TokenHashing.kt` (`fun newToken(prefix: String): String`, `fun sha256Hex(s: String): String`).
- Create `mcp/McpSettings.kt` (`enabled(): Boolean`, `lockedByEnvironment(): Boolean`, `setEnabled(Boolean)`), `mcp/McpAuthFilter.kt`, `mcp/McpRateLimiter.kt`, `mcp/McpServerConfig.kt` (transport servlet + `McpSyncServer` + tool registration), `mcp/McpTool.kt` (small interface: `name`, `description`, `inputSchema: String` JSON, `call(args: Map<String, Any?>): CallToolResult`), `mcp/McpPrincipalScope.kt`, `mcp/tools/ListAccountsTool.kt`, `mcp/tools/ListCategoriesTool.kt`, `mcp/tools/References.kt` (uuid-or-name resolution, Task 2 and 3 reuse it).
- Modify `security/SecurityConfig.kt` (permit `/mcp` at the Spring layer — `McpAuthFilter` authenticates it; make `JwtAuthenticationFilter` reject `pm_`/OAuth tokens on `/api/**` simply by never accepting them: they are not JWTs, so confirm a `pm_…` bearer on `/api/accounts` is 401 and test it), `admin/AdminController.kt` (`GET/PUT /api/admin/mcp` → `{ enabled, lockedByEnvironment }`, 409 when locked), `instance/InstanceInfo.kt` + `InstanceController.kt` (`mcpEnabled: Boolean`, `oauthAvailable: Boolean` = enabled && APP_BASE_URL set), `application.yml` (`app.mcp.enabled: ${MCP_ENABLED:}`), `docker-compose.yml` and `.env.example` (`MCP_ENABLED: ${MCP_ENABLED:-}`), `service/UserService.kt` only if account deletion does not cascade tokens through the FK.
- Tests: `token/AccessTokenServiceTest.kt`, `mcp/McpEndpointTest.kt` (real HTTP, `@SpringBootTest(webEnvironment = RANDOM_PORT)`), `mcp/McpSwitchTest.kt`.

**Schema (`V7__mcp.sql`):**

```sql
-- V7: assistants over MCP — personal and OAuth tokens, OAuth clients/grants/codes, and
-- which entries an assistant recorded.
CREATE TABLE oauth_clients (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    client_id     VARCHAR(64)  NOT NULL UNIQUE,
    client_name   VARCHAR(200) NOT NULL,
    redirect_uris VARCHAR(4000) NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    last_used_at  TIMESTAMP    NULL
);

CREATE TABLE oauth_grants (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    uuid       VARCHAR(36) NOT NULL UNIQUE,
    client_id  BIGINT      NOT NULL REFERENCES oauth_clients (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope      VARCHAR(100) NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    revoked_at TIMESTAMP   NULL
);

CREATE TABLE oauth_codes (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    code_hash      VARCHAR(64)  NOT NULL UNIQUE,
    grant_id       BIGINT       NOT NULL REFERENCES oauth_grants (id) ON DELETE CASCADE,
    redirect_uri   VARCHAR(1000) NOT NULL,
    code_challenge VARCHAR(128) NOT NULL,
    expires_at     TIMESTAMP    NOT NULL,
    used_at        TIMESTAMP    NULL
);

CREATE TABLE access_tokens (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind           VARCHAR(20)  NOT NULL,          -- PERSONAL | OAUTH_ACCESS | OAUTH_REFRESH
    name           VARCHAR(100) NOT NULL,
    token_hash     VARCHAR(64)  NOT NULL UNIQUE,
    prefix         VARCHAR(10)  NOT NULL,
    oauth_grant_id BIGINT       NULL REFERENCES oauth_grants (id) ON DELETE CASCADE,
    created_at     TIMESTAMP    NOT NULL,
    expires_at     TIMESTAMP    NULL,
    last_used_at   TIMESTAMP    NULL,
    used_at        TIMESTAMP    NULL               -- refresh tokens: set when rotated
);
CREATE INDEX idx_access_tokens_user ON access_tokens (user_id);

ALTER TABLE transactions ADD COLUMN source VARCHAR(8) NOT NULL DEFAULT 'APP';
```

**Behaviour:**

- `AccessTokenService`:
  - `createPersonal(name: String, expiresInDays: Int?): CreatedToken(token, view)` — `expiresInDays` ∈ {30, 90, 365, null}; name 1–100 chars trimmed; else 400.
  - `listFor(user): List<TokenView>` — personal tokens and OAuth grants (Task 4 adds grants; in this task the list holds personal tokens only) as `TokenView(uuid-or-id, kind: PERSONAL|OAUTH, name, prefix, createdAt, lastUsedAt, expiresAt)`.
  - `revoke(id)` — only the caller's own; 404 otherwise.
  - `authenticate(raw: String): AuthenticatedToken?` — hash, find, reject expired/used/revoked (grant `revoked_at`), update `last_used_at` at most once a minute; returns `user` and token id.
- `TokenController`: `GET /api/assistants/tokens`, `POST /api/assistants/tokens` `{ name, expiresInDays }` → `{ token, item }` (the only time the raw token is returned), `DELETE /api/assistants/tokens/{id}`. All 404 when MCP is disabled.
- `McpAuthFilter` (a `OncePerRequestFilter` registered only for `/mcp`):
  - MCP disabled → 404 empty body.
  - Missing/invalid bearer → 401 with `WWW-Authenticate: Bearer resource_metadata="{APP_BASE_URL}/.well-known/oauth-protected-resource"` when `APP_BASE_URL` is set, else plain `Bearer`.
  - Valid → loads the user's `UserPrincipal` exactly as `CustomUserDetailsService` does (current household included), puts it in the request attribute `McpPrincipalScope.ATTRIBUTE` and in the `SecurityContext`.
- `McpPrincipalScope`: the SDK may run tool handlers off the servlet thread, so tools never rely on the thread's `SecurityContext`. `McpServerConfig` builds the transport with a context extractor that copies the request attribute into the SDK's transport context; every tool call goes through `McpPrincipalScope.runAs(principal) { tool.call(args) }`, which sets and then clears `SecurityContextHolder` around the call. Read the SDK 2.1.0 API (`HttpServletStreamableServerTransportProvider.builder().contextExtractor(...)`, `McpTransportContext`, `exchange.transportContext()`) from the jar sources; if the API differs, keep the same guarantee and say how in the report.
- `McpRateLimiter`: a fixed one-minute window per token id, 60 calls (`tools/call` only); over → the filter answers 429 for that request. In-memory map, entries older than two minutes pruned.
- Every tool call logs `mcp tool={name} user={id} ms={duration} error={bool}` at info.
- `McpServerConfig`: server info `personalmoney` / app version; registers every `McpTool` bean as a sync tool with its JSON schema. Tool exceptions: `ToolInputException(message)` → `CallToolResult` with `isError = true` and the message; any other exception → `isError = true`, "Something went wrong on the server" (logged with class name only).
- Tool output: `CallToolResult` with one text content holding pretty JSON of the result (and `structuredContent` with the same object if the SDK version supports it).
- `References.account(ref, includeArchived = false)` / `References.category(ref, type)`: uuid match first, then case-insensitive exact name among the household's items; 0 matches → `ToolInputException("Account \"X\" not found — list_accounts shows the accounts")`; >1 → `Account "Card" matches 2 accounts: Card (UAH) <uuid>, Card (USD) <uuid> — pass the uuid` (categories: `Category "Food" matches 2 categories: Food (EXPENSE) <uuid>, …`).
- `list_accounts` `{ includeArchived?: boolean }` → `[{ uuid, name, type, currency, balance, archived }]` (balance as decimal string, same value REST `/api/accounts` shows).
- `list_categories` `{ type?: "INCOME"|"EXPENSE" }` → `[{ uuid, name, type, parentUuid }]`, non-archived only.
- `McpSettings`: `MCP_ENABLED` true/false wins (`lockedByEnvironment`), else `instance_settings['mcp.enabled'] == "true"`.

- [ ] **Step 1:** Write `McpEndpointTest` first: with MCP enabled and a personal token, a JSON-RPC `initialize` POST to `/mcp` (headers `Accept: application/json, text/event-stream`, `Content-Type: application/json`) succeeds and returns `Mcp-Session-Id`; `notifications/initialized`; `tools/list` returns exactly `list_accounts` and `list_categories` (this task); `tools/call list_accounts` returns only the caller household's accounts; no token → 401 with `WWW-Authenticate`; token of a user in another household sees only theirs; revoked token → 401; expired token → 401; 61st call within a minute → 429; ambiguous account name through a test-only call of `References` gives the exact message. Parse SSE (`data:` lines) or JSON, whichever the transport returns. Run it — FAIL.
- [ ] **Step 2:** `AccessTokenServiceTest` (hash stored, raw never stored, prefix, expiry options, 400 on other values, revoke someone else's → 404, `pm_…` bearer on `GET /api/accounts` → 401) and `McpSwitchTest` (disabled → `/mcp` 404 and `/api/assistants/tokens` 404; `MCP_ENABLED=false` beats the page; admin PUT 409 when locked; `/api/instance` reports `mcpEnabled`). FAIL.
- [ ] **Step 3:** Implement. Run the three tests, then the whole server suite.
- [ ] **Step 4:** Commit "Serve MCP with personal access tokens and list accounts and categories".

### Task 2: Search and summary tools

**Files:** create `mcp/tools/SearchTransactionsTool.kt`, `mcp/tools/SummarizeSpendingTool.kt`, `service/SpendingSummaryService.kt` (aggregate query; repository methods on `TransactionRepository` or a JPQL/criteria query in the service); test `mcp/McpReadToolsTest.kt` (calls the tools through `McpTool.call` inside `McpPrincipalScope.runAs`, plus one end-to-end call over HTTP to each).

**Interfaces:** consumes `McpTool`, `ToolInputException`, `References`, `McpPrincipalScope` from Task 1.

- `search_transactions` input `{ from?: "YYYY-MM-DD", to?, account?, category?, type?: "INCOME"|"EXPENSE"|"TRANSFER", text?, minAmount?, maxAmount?, limit?: 1..100 (default 50), cursor? }`. Excludes tombstoned rows; newest first by (date desc, id desc); `cursor` is an opaque base64 of the last (date, id); `text` matches note or the linked receipt's merchant, case-insensitive substring; amounts compare on absolute value in the row's own currency. Output `{ transactions: [{ uuid, date, type, account: {uuid,name}, toAccount?: {uuid,name}, category?: {uuid,name}, amount, currency, toAmount?, toCurrency?, note?, author?, source, receipt?: { merchant?, total?, items: [{ name, quantity?, price }] } }], nextCursor? }`.
- `summarize_spending` input `{ from (required), to (required), groupBy?: "CATEGORY"|"ACCOUNT"|"MONTH" (default CATEGORY), type?: "EXPENSE"|"INCOME" (default EXPENSE) }`; `from > to` or range over 5 years → tool error. Transfers and tombstones excluded. Output `{ groups: [{ key: { uuid?, name } | "YYYY-MM", totals: [{ currency, amount }] }], totals: [{ currency, amount }] }`, groups sorted by the largest total descending (MONTH: chronological); uncategorised entries grouped as name "Uncategorised".
- Tests: two currencies, three groupings, transfer excluded, tombstone excluded, another household's rows never counted, pagination with cursor returns disjoint pages covering all rows, `text` matches a receipt merchant, invalid date → tool error.
- [ ] Steps: tests first (FAIL), implement, server suite, commit "Let assistants search transactions and summarise spending".

### Task 3: Create tools, source marker, receipts without photos

**Files:** create `mcp/tools/CreateTransactionTool.kt`, `mcp/tools/CreateTransferTool.kt`; modify `domain/Transaction.kt` (`source: String = "APP"`, column `source`), `dto/Dtos.kt` (`TransactionResponse.source`), `dto/SyncDtos.kt` (`SyncTransaction.source: String? = null`, pulled; ignored on push), `service/Mappers.kt`, `service/SyncService.kt` (toSync sets it; apply never changes it; new rows from a push are `APP`), `service/TransactionService.kt` + `TransferService.kt` (an optional `source` parameter, default `APP`, so REST callers are unchanged), `receipt/ReceiptService.kt` (`createFromAssistant(householdId, userId, merchant, date, currency, totalMinor?, items): Receipt` → `READY`, no image, `total_mismatch` computed by the same rule scanned receipts use, linked inside the caller's transaction), `receipt/ReceiptDtos.kt` (`hasImage: Boolean`), `receipt/ReceiptController.kt` (`GET /api/receipts/{uuid}/image` → 404 when no image). Tests: `mcp/McpCreateToolsTest.kt`, additions to `receipt/ReceiptLinkTest.kt` or a new `receipt/AssistantReceiptTest.kt`, `service/SyncServiceTest.kt` (source pulled; a push carrying `source: "MCP"` for a new row stores `APP`; for an existing MCP row keeps `MCP`).

- `create_transaction` input `{ account, type: "INCOME"|"EXPENSE", amount (positive decimal string), date?: "YYYY-MM-DD" (default today in the household's zone — use whatever REST create uses for "today"), category?, note? (≤ 500), receipt?: { merchant? (≤ 255), total?, items: [{ name (1–255), quantity?, price }] (≤ 200 items) } }`. Category type must equal `type` → else `Category "Salary" is an income category`. Archived account → `Account "Old card" is archived`. Amount precision beyond the currency's decimals → tool error. Receipt currency = the account's; `total` defaults to the items' sum. Output: the created transaction in the `search_transactions` item shape.
- `create_transfer` input `{ fromAccount, toAccount, amount, toAmount?, date?, note? }`; same account → error; different currencies without `toAmount` → `The accounts use UAH and USD — pass toAmount, the amount that arrives`; same currency with a different `toAmount` → error. Output in the same shape.
- Both set `source = MCP` and the author to the token owner; the rows get a `SyncClock` stamp like REST creates (verify by a sync pull in the test).
- Tests: created entry visible through REST with `source: "MCP"` and in a sync pull; receipt `READY`, `hasImage: false`, items in minor units, `totalMismatch` true when items don't add up; image endpoint 404 for it; another household's account by uuid → "not found"; every error message above exactly.
- [ ] Steps: tests first (FAIL), implement, server suite, commit "Let assistants record transactions, transfers and the receipts they read".

### Task 4: OAuth for web connectors

**Files:** create `oauth/OAuthClient.kt`, `oauth/OAuthGrant.kt`, `oauth/OAuthCode.kt` (+ repositories), `oauth/OAuthService.kt`, `oauth/OAuthMetadataController.kt`, `oauth/OAuthRegistrationController.kt`, `oauth/OAuthAuthorizeController.kt` (`GET /oauth/authorize` and `POST /api/oauth/authorize`, `GET /api/oauth/clients/{clientId}` for the consent page), `oauth/OAuthTokenController.kt` (`/oauth/token`, `/oauth/revoke`), `oauth/OAuthCleanup.kt` (hourly: clients with no grant older than 24 h; codes expired over an hour ago); modify `token/AccessTokenService.kt` (`listFor` includes active grants as `kind: OAUTH` with the client name; `revoke` of an OAuth item sets `revoked_at` on the grant), `security/SecurityConfig.kt` (permit `/oauth/**`, `/.well-known/oauth-*`; `/api/oauth/**` authenticated by JWT), `mcp/McpAuthFilter.kt` (OAuth access tokens accepted). Tests: `oauth/OAuthFlowTest.kt` (MockMvc), `oauth/OAuthRegistrationTest.kt`, `oauth/OAuthCleanupTest.kt`.

All OAuth endpoints answer 404 when MCP is disabled or `APP_BASE_URL` is empty. `{base}` below is `APP_BASE_URL` without a trailing slash.

- `GET /.well-known/oauth-protected-resource` → `{ "resource": "{base}/mcp", "authorization_servers": ["{base}"], "scopes_supported": ["ledger:write"], "bearer_methods_supported": ["header"] }`. Also serve it at `/.well-known/oauth-protected-resource/mcp`.
- `GET /.well-known/oauth-authorization-server` → `{ issuer: "{base}", authorization_endpoint: "{base}/oauth/authorize", token_endpoint: "{base}/oauth/token", registration_endpoint: "{base}/oauth/register", revocation_endpoint: "{base}/oauth/revoke", response_types_supported: ["code"], grant_types_supported: ["authorization_code","refresh_token"], code_challenge_methods_supported: ["S256"], token_endpoint_auth_methods_supported: ["none"], scopes_supported: ["ledger:write"] }`.
- `POST /oauth/register` (JSON, RFC 7591): requires `redirect_uris` (1–10, each ≤ 1000 chars, https or loopback http with an optional port, no fragment); `client_name` optional (≤ 200, default "Unnamed assistant"); returns 201 `{ client_id, client_name, redirect_uris, grant_types: ["authorization_code","refresh_token"], response_types: ["code"], token_endpoint_auth_method: "none", client_id_issued_at }`. Bad URI → 400 `{ "error": "invalid_redirect_uri", "error_description": "…" }`. Registration is rate limited with the existing `AuthRateLimitFilter` pattern (10 per hour per IP).
- `GET /oauth/authorize?response_type=code&client_id&redirect_uri&code_challenge&code_challenge_method=S256&state&scope?&resource?`: unknown client or redirect URI not registered exactly → 400 HTML/plain text page (never redirect to an unverified URI); other errors (`response_type`≠code, missing/plain PKCE, unknown scope) → redirect to `redirect_uri?error=invalid_request&state=…`. Valid → 302 to `{base}/oauth/consent?` + the same query parameters.
- `GET /api/oauth/clients/{clientId}` (JWT) → `{ clientName, redirectHost }` for the consent page.
- `POST /api/oauth/authorize` (JWT) `{ clientId, redirectUri, codeChallenge, state, scope, approve: boolean }` re-validates everything; deny → `{ redirect: "{redirectUri}?error=access_denied&state=…" }`; approve → creates a grant (user, client, scope `ledger:write`) and a code (32 random bytes base64url, hash stored, 60 s) → `{ redirect: "{redirectUri}?code=…&state=…" }`. Append with `?` or `&` correctly.
- `POST /oauth/token` (form): `grant_type=authorization_code` with `code`, `redirect_uri`, `client_id`, `code_verifier` — checks hash, unused, unexpired, same client and redirect URI, `BASE64URL(SHA256(verifier)) == challenge`; marks the code used; issues access (`pma_…`, 1 h) and refresh (`pmr_…`, 90 days) tokens in `access_tokens` (kind `OAUTH_ACCESS`/`OAUTH_REFRESH`, name = client name, `oauth_grant_id`). A second use of a code → revoke its grant and return `invalid_grant`. `grant_type=refresh_token` with `refresh_token`, `client_id` — valid unused refresh → mark it used, issue a new pair; a used refresh token presented again → revoke the grant, `invalid_grant`. Response `{ access_token, token_type: "Bearer", expires_in: 3600, refresh_token, scope: "ledger:write" }`, headers `Cache-Control: no-store`. Errors per RFC 6749 (`invalid_request`, `invalid_grant`, `invalid_client`, `unsupported_grant_type`) as 400 JSON.
- `POST /oauth/revoke` (form `token`): revokes the grant of an access or refresh token; always 200.
- Revoking from Settings (`DELETE /api/assistants/tokens/{id}` for an OAuth item) sets `revoked_at`; all its tokens stop working at once.
- Tests: both metadata documents (and 404 without `APP_BASE_URL`); registration rules (https ok, loopback with port ok, plain http host rejected, fragment rejected); the full flow — register → authorize redirect → `POST /api/oauth/authorize` as the user → token exchange with the right verifier → `tools/list` over `/mcp` with the access token works → refresh rotates → old refresh reuse revokes the grant → the access token then 401; wrong verifier → `invalid_grant`; code reuse revokes; expired code; deny → `access_denied`; unknown redirect on authorize → 400 without redirect; Settings list shows the grant and revoking it kills the tokens; cleanup deletes an unused client after 24 h but keeps one with a grant.
- [ ] Steps: tests first (FAIL), implement, server suite, commit "Let claude.ai and ChatGPT connect through OAuth".

---

# Phase B — Web (`src/main/frontend/`)

### Task 5: Assistants settings, consent page, admin switch, markers, docs

**Files:** modify `app/api/{types,endpoints}.ts` (`TokenView`, `CreatedToken`, `InstanceDto.mcpEnabled`/`oauthAvailable`, `TransactionResponse.source`, `ReceiptDto.hasImage`, `assistantTokens`, `createAssistantToken`, `revokeAssistantToken`, `adminMcp`, `setAdminMcp`, `oauthClient`, `oauthAuthorize`), `app/api/queryClient.ts` (`qk.assistantTokens`, `qk.adminMcp`); create `app/features/assistants/AssistantsCard.tsx` (+ test), `app/features/assistants/CreateTokenDialog.tsx`, `app/features/assistants/snippets.ts` (+ test), `app/features/oauth/ConsentPage.tsx` (+ test); modify `app/features/settings/SettingsPage.tsx` (Assistants card when `instance.mcpEnabled`), `app/features/admin/AdminPage.tsx` (switch card, "Set by the environment" when locked), the router (route `/oauth/consent`, requires sign-in and returns to the consent URL after sign-in using the existing redirect-after-login mechanism; if none exists, store the URL in `sessionStorage` before redirecting to sign-in), the transaction list/edit views ("via assistant" next to the author when `source === 'MCP'`), `app/features/receipts/ReceiptView.tsx` (no thumbnail request when `hasImage` is false), locales `app.{en,ru,uk}.ts`; `README.md` (section "Connect your AI assistant": enable it, create a token, the Claude Code command, Claude Desktop/Cursor JSON, claude.ai/ChatGPT custom connector with the URL, `APP_BASE_URL` needed for OAuth, `MCP_ENABLED`); `site/` features list (one line, en and any other locales the site has).

- Assistants card: the endpoint URL (`instance` base URL if exposed, else `window.location.origin` + `/mcp`) with copy; "Create token" dialog (name, expiry 30/90/365/never) → the token shown once with copy and the snippets:
  - Claude Code: `claude mcp add --transport http personalmoney {url} --header "Authorization: Bearer {token}"`
  - Claude Desktop / Cursor JSON: `{ "mcpServers": { "personalmoney": { "type": "http", "url": "{url}", "headers": { "Authorization": "Bearer {token}" } } } }`
  - claude.ai / ChatGPT: "Add a custom connector with this URL: {url}" when `oauthAvailable`, else "Connecting from claude.ai or ChatGPT needs the server's public HTTPS address (APP_BASE_URL)".
  - The list: name (or client name with an "OAuth" badge), prefix, created, last used, expiry, Revoke with `ConfirmDialog`.
- Consent page: loads `oauthClient(clientId)`; shows "**{clientName}** (unverified) wants to read your finances and add entries in **{householdName}**. It will be able to see accounts, categories and transactions, and record new ones. It cannot edit or delete anything." with Allow / Deny; calls `oauthAuthorize` and `window.location.assign(redirect)`. Invalid parameters or unknown client → an error message, no buttons. The page must not be frameable: the server sets `Content-Security-Policy: frame-ancestors 'none'` for `/oauth/consent` (add to the SPA fallback/headers config in this task, with a server test if it lives in Kotlin).
- Tests first: AssistantsCard creates a token and shows it once (gone after closing), revoke asks for confirmation and calls DELETE; snippets embed URL and token exactly; consent Allow posts `approve: true` and navigates to the returned redirect, Deny posts `approve: false`; admin switch PUTs and shows the locked state; "via assistant" appears for `source: 'MCP'`; ReceiptView with `hasImage: false` makes no image request and shows the items.
- [ ] Steps: tests first (FAIL), implement, `npm run typecheck && npm test`, site build + test, commit "Add assistant tokens, the OAuth consent page and the MCP switch to the web app".

---

# Phase C — Android (`client/composeApp/src/`)

### Task 6: Source marker and receipts without photos

**Files:** modify the domain `Transaction` (`source: String = "APP"`), `TransactionEntity` (`source` column, `@ColumnInfo(defaultValue = "APP")`), `Migrations.kt` (`MIGRATION_6_7`: `ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'APP'`, added to `ALL_MIGRATIONS`), `AppDatabase` version 7 + exported `7.json`, `SyncTransactionDto.source: String? = null` (pulled; `toDto` sends nothing — push never sets it), `toEntity` stores `source ?: "APP"`, `RoomTransactionRepository` keeps `source` on edit, the transaction editor shows "via assistant" next to the author when `source == "MCP"`, `ReceiptDto.hasImage: Boolean = true` and the receipt dialog skips loading the photo when false, strings `transaction_via_assistant` en/ru/uk. Tests: `SyncMappers` pull carries `source` and push omits it; repository edit keeps it; `MigrationTest.migrate6To7_addsTheSourceColumn` (compiles; run if a device exists); receipt dialog state without a photo (VM or loader test, whichever exists).
- [ ] Steps: tests first, implement, Android build + tests, commit "Show entries recorded by an assistant and receipts without photos".

---

## After the last task

Every suite once more (server, web, site, Android), then superpowers:finishing-a-development-branch.
