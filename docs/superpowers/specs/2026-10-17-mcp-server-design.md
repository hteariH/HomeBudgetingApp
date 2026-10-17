# MCP server

Sub-project 4 of the [self-hosted pivot](2026-10-15-self-hosted-pivot-overview.md): a user's
own AI assistant (Claude Desktop/Code, Cursor, claude.ai, ChatGPT, a local client) connects
to their PersonalMoney instance, reads the household's books and records entries, including
receipts it read itself.

## Decisions

- **Clients:** personal access tokens for clients that take an `Authorization` header, and
  a built-in OAuth 2.1 authorization server for web connectors (claude.ai, ChatGPT) that
  connect by URL alone.
- **Read and create only.** Tools list, search, summarise, create transactions and
  transfers. No editing or deleting: an assistant's mistake is fixed by hand; it cannot
  erase history.
- **The assistant reads receipts itself.** `create_transaction` accepts merchant and line
  items; the transaction gets a `READY` receipt without a photo. No server AI provider is
  needed for this path, and no image bytes cross MCP.
- **Approach:** the official MCP Java SDK (`io.modelcontextprotocol.sdk:mcp-core` with
  `mcp-json-jackson2`, its servlet Streamable HTTP transport registered at `/mcp`) inside
  the existing application. Tools are thin wrappers over the existing services, so household
  scoping and validation are shared with REST.
- **A small OAuth server of our own**, not Spring Authorization Server (decided after
  planning research: SAS needs an initial access token for client registration while
  connectors register anonymously, stores issued tokens in plain text, and authorises
  through a session login our stateless JWT web app does not have). It implements exactly
  what MCP clients use: authorization code + PKCE S256, open dynamic registration,
  refresh rotation, the two metadata documents.
- **Off by default.** The administrator enables MCP on the admin page; `MCP_ENABLED`
  (environment) wins over the page.

## Tools

Every tool acts in the token owner's current household, through the same services as REST.
Money is a decimal string with an explicit ISO currency code, as in REST.

| Tool | Input | Output |
| --- | --- | --- |
| `list_accounts` | `includeArchived?` | uuid, name, type, currency, balance, archived |
| `list_categories` | `type?` (`INCOME`/`EXPENSE`) | uuid, name, type, parent uuid |
| `search_transactions` | `from?`, `to?` (dates), `account?`, `category?`, `type?` (`INCOME`/`EXPENSE`/`TRANSFER`), `text?` (note or receipt merchant, case-insensitive), `minAmount?`, `maxAmount?`, `limit?` (default 50, max 100), `cursor?` | transactions newest first with account, category, amount, currency, date, note, author, source, receipt merchant and items when present; `nextCursor` |
| `summarize_spending` | `from`, `to`, `groupBy` (`CATEGORY` default, `ACCOUNT`, `MONTH`), `type?` (`EXPENSE` default, `INCOME`) | groups with totals **per currency** (no conversion), plus per-currency grand totals |
| `create_transaction` | `account`, `type` (`INCOME`/`EXPENSE`), `amount`, `date?` (today in the household's zone), `category?`, `note?`, `receipt?` `{ merchant?, total?, items: [{ name, quantity?, price }] }` | the created transaction |
| `create_transfer` | `fromAccount`, `toAccount`, `amount`, `toAmount?` (required when currencies differ), `date?`, `note?` | the created transfer |

- **References by uuid or name.** `account`, `category`, `fromAccount`, `toAccount` accept a
  uuid or an exact name (case-insensitive). An ambiguous or unknown name is a tool error
  listing the candidates, e.g. `Account "Card" matches 2 accounts: Card (UAH) a1b2…,
  Card (USD) c3d4… — pass the uuid`. Archived accounts cannot receive new entries.
- **Category type** must match the transaction type.
- **Receipt from the assistant:** a `receipts` row with `status = READY`, no `image_path`,
  merchant, date (the transaction's), currency (the account's), `total_minor` (given total,
  else the items' sum), items with minor-unit prices, and `total_mismatch` computed as for
  scanned receipts. It is linked to the new transaction in the same database transaction.
  Clients already handle a receipt without a photo: the web and Android receipt views show
  the items and skip the thumbnail when `GET /api/receipts/{uuid}` reports `hasImage:
  false` (new field).
- **`summarize_spending`** is a new aggregate query in the service layer (clients compute
  summaries today). Transfers are excluded; tombstoned rows are excluded.
- **Source marker:** `transactions.source` (`APP` default | `MCP`) — new column, set to
  `MCP` by the two create tools. REST `TransactionResponse` and `SyncTransaction` carry it
  (pulled only; a push never changes it). The author stays the token owner.
- **Sync:** created rows get a `SyncClock` stamp like any server write and reach phones on
  their next sync.
- Tools only — no MCP resources or prompts.

## Access

### Personal access tokens

- Created in web Settings → **Assistants**: a name ("Claude Desktop") and an expiry (30 days,
  90 days, 1 year, never). Shown once: `pm_` + 40 random base62 characters.
- Stored as SHA-256 only. Valid **only on `/mcp`**; every other endpoint rejects them, so a
  leaked token cannot change a password or delete the account.

### OAuth 2.1 (web connectors)

- Authorization code flow, **PKCE S256 required**, public clients only
  (`token_endpoint_auth_method: none`); registrations asking for another method get
  `none`.
- Endpoints: `GET /oauth/authorize` (validates the request, then redirects the browser to
  the web app's `/oauth/consent?...`), `POST /api/oauth/authorize` (JWT-authenticated, called
  by the consent page; returns the redirect URL with `code` and `state`, or with
  `error=access_denied`), `POST /oauth/token` (`authorization_code`, `refresh_token`; form
  encoded), `POST /oauth/register`, `POST /oauth/revoke` (RFC 7009).
- Authorization codes: 32 random bytes, stored hashed, single use, 60 s, bound to client,
  redirect URI, code challenge, user and household. Reuse of a code revokes the grant it
  produced.
- Dynamic client registration (RFC 7591) is open, as connectors require. Accepted redirect
  URIs: `https://…`, and `http://localhost` / `http://127.0.0.1` with any port. Clients that
  never obtained a token are deleted 24 h after registration.
- Metadata: `/.well-known/oauth-authorization-server` (RFC 8414) and
  `/.well-known/oauth-protected-resource` (RFC 9728). `/mcp` answers 401 with
  `WWW-Authenticate: Bearer resource_metadata="…/.well-known/oauth-protected-resource"`.
- **Consent** is a page of the web app: the normal sign-in if needed, then "**{client}**
  wants to read your finances and add entries in **{household}**. Allow / Deny". The client
  name is shown as registered, marked "unverified".
- Access tokens are opaque, 1 hour; refresh tokens 90 days, rotated on use. Both are stored
  hashed so revocation is immediate.
- OAuth needs the instance's external HTTPS address: `APP_BASE_URL`. Without it the
  metadata endpoints answer 404 and only personal tokens work; Settings says so.
- One scope: `ledger:write` (read and create).

### Storage — migration `V7__mcp.sql`

- `access_tokens` — `id`, `user_id` (cascade), `kind` (`PERSONAL` | `OAUTH_ACCESS` |
  `OAUTH_REFRESH`), `name`, `token_hash` (unique), `prefix` (first 7 characters, shown in
  lists), `oauth_grant_id` (nullable, cascade), `created_at`, `expires_at`
  (nullable), `last_used_at`.
- `oauth_clients` — `id`, `client_id` (unique, random), `client_name`, `redirect_uris`
  (newline-separated), `created_at`, `last_used_at`.
- `oauth_grants` — `id`, `uuid`, `client_id` (cascade), `user_id` (cascade), `scope`,
  `created_at`, `revoked_at`. One grant per consent; its access and refresh tokens live in
  `access_tokens` with `oauth_grant_id` (cascade). Settings lists grants.
- `oauth_codes` — `id`, `code_hash` (unique), `client_id`, `grant_id`, `redirect_uri`,
  `code_challenge`, `expires_at`, `used_at`.
- `transactions.source` `VARCHAR(8) NOT NULL DEFAULT 'APP'`.
- The admin switch is the key `mcp.enabled` (`true`/`false`, absent = false) in the
  existing key-value `instance_settings` table; no schema change.

### Shared rules

- Members and owners are equal: an assistant can do what its user can in the books, minus
  editing and deleting.
- Rate limit: 60 tool calls per minute per token (429 above).
- MCP disabled → `/mcp` and the OAuth endpoints answer 404.
- Deleting a user deletes their tokens and grants; leaving or switching households keeps
  them (they act in the current household).

## Web

- **Settings → Assistants** (any user, shown when MCP is enabled):
  - The endpoint URL `{APP_BASE_URL or window origin}/mcp` with a copy button.
  - "Create token" → name + expiry → the token once, with ready-made snippets:
    Claude Code `claude mcp add --transport http personalmoney {url} --header
    "Authorization: Bearer {token}"`, Claude Desktop / Cursor JSON.
  - "Connect from claude.ai or ChatGPT": the URL to paste as a custom connector (only when
    `APP_BASE_URL` is set; otherwise a sentence saying it needs the HTTPS address).
  - One list of personal tokens and OAuth grants: name or client, created, last used,
    expiry, **Revoke**.
- **OAuth consent page** at `/oauth/consent`.
- **Admin page:** "Allow assistants (MCP)" switch, or "Set by the environment".
- **Transactions:** "via assistant" next to the author when `source = MCP`.
- Receipt view without a photo: items only.
- en, ru, uk.

## Android

- `source` pulled through sync (Room migration 6 → 7, column `source`), "via assistant"
  next to the author in the transaction editor.
- Receipt dialog skips the photo when `hasImage` is false.
- No token management on Android.

## Docs

- README: "Connect your AI assistant" — enabling, tokens, the Claude Code / Desktop
  snippets, claude.ai/ChatGPT connectors and the `APP_BASE_URL` requirement.
- `site/`: one line in the features list.

## Errors

| Case | Result |
| --- | --- |
| No or bad token, expired, revoked | 401 with `WWW-Authenticate` |
| Personal or OAuth token on a REST endpoint | 401 |
| MCP disabled | 404 |
| Over 60 calls/min | 429 |
| Unknown or ambiguous account/category | tool result `isError: true`, message lists candidates |
| Category type does not match | tool error "Category "Salary" is an income category" |
| Bad amount, currency mismatch, archived account, transfer without `toAmount` across currencies | tool error with the reason |
| Non-https redirect URI at registration | 400 `invalid_redirect_uri` |

Logs record tool name, user id and duration — never arguments, amounts, notes or tokens.

## Security

- Tokens: 238 bits of entropy, hashed at rest, shown once, `/mcp`-only, revocable, expiring.
- OAuth: PKCE required, exact redirect URI match, https-or-loopback redirects, consent on
  every new client, refresh rotation with reuse detection (a reused refresh token revokes
  the grant).
- No tool can edit, delete, export credentials, manage members or change settings.
- The consent page is not frameable (`frame-ancestors 'none'`).

## Testing

Server:
- MCP over real HTTP (`@SpringBootTest` random port): `initialize` → `tools/list` → each
  tool; another household's data is never returned; ambiguous names give a tool error;
  `create_transaction` with a receipt creates a `READY` receipt without a photo visible
  through REST (`hasImage: false`) and sync, with `source = MCP`.
- `summarize_spending` across two currencies and the three groupings; transfers excluded.
- Tokens: hashing, expiry, revocation, rejected on REST, `last_used_at`.
- OAuth: both metadata documents, DCR with redirect-URI rules, the full code + PKCE flow
  through MockMvc, refresh rotation and reuse detection, revocation, 404 without
  `APP_BASE_URL`, cleanup of unused clients.
- Rate limit, the enable switch and the environment lock.

Web: Assistants page (create shows the token once, revoke, snippets), consent page, admin
switch, "via assistant" marker, receipt without photo.

Android: Room migration 6 → 7, sync mapper for `source`, receipt dialog without photo.

## Out of scope

- Editing and deleting through MCP.
- Uploading receipt photos through MCP.
- MCP resources and prompts.
- Per-tool scopes or read-only tokens.
- Token management on Android.
