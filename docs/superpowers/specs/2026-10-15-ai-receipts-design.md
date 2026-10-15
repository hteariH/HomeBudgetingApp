# AI receipt scanning

Sub-project 3 of the [self-hosted pivot](2026-10-15-self-hosted-pivot-overview.md): a photo
of a receipt becomes a filled-in expense, read by an AI model the instance's owner chose.

## Why

The Android app reads receipts on the phone with Tesseract: a heuristic finds a total, a
date and a shop name, often badly, and the web has no scanning at all. A vision-capable
language model reads receipts far better, including line items, and a self-hoster can run
one locally (Ollama, LM Studio) or use a hosted API with their own key.

## Decisions

- **One transaction plus its items.** A scan fills the expense form (amount, date, merchant
  as description, suggested category, currency); the receipt's line items and a compressed
  photo are kept with the transaction and shown on it. No splitting into several
  transactions.
- **The server talks to the model.** Clients upload the photo; keys never reach a device,
  and a model on the home network is reachable from a phone outside it.
- **Asynchronous.** Upload returns at once with a receipt in `PENDING`; the client polls
  until `READY` or `FAILED`. A local model on a NAS CPU can take minutes, and reverse proxies
  cut synchronous requests at 60–100 s.
- **Providers at two levels.** The instance administrator sets a default (admin page or
  environment); a household's owner may add their own, which wins for that household.
  Arbitrary model URLs (a LAN Ollama) are administrator-only; household owners choose from
  hosted presets with fixed URLs.
- **Photos are kept**, compressed, next to the transaction, and deleted with it.
- **Tesseract goes.** Without a configured provider there is no scan button.

## Providers

Three adapters behind one interface:

| Type | Wire format | Base URL |
| --- | --- | --- |
| `OPENAI_COMPATIBLE` | `POST {base}/chat/completions` with an `image_url` data-URI content part, `response_format: { type: "json_object" }` where supported | administrator: any http(s) URL (Ollama `http://host:11434/v1`, LM Studio, vLLM, OpenRouter, OpenAI); household: presets `https://api.openai.com/v1`, `https://openrouter.ai/api/v1` |
| `ANTHROPIC` | `POST https://api.anthropic.com/v1/messages` with an `image` content block (base64) | fixed |
| `GEMINI` | `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent` with `inline_data`, `responseMimeType: application/json` | fixed |

```kotlin
interface ReceiptExtractor {
    /** Reads one compressed JPEG; throws ExtractionException with a user-facing reason. */
    fun extract(jpeg: ByteArray, context: ExtractionContext): ExtractedReceipt
}
```

`ExtractionContext` carries the household's category names (by type `EXPENSE`) and active
currency codes so the model can pick from them. The prompt asks for JSON only:

```json
{
  "merchant": "string|null",
  "date": "YYYY-MM-DD|null",
  "currency": "ISO 4217 code|null",
  "total": "decimal string|null",
  "items": [{ "name": "string", "quantity": "decimal string|null", "price": "decimal string" }],
  "suggestedCategory": "one of the given category names|null"
}
```

A reply that is not valid JSON of this shape gets one retry with a "reply with the JSON
object only" reminder, then fails.

**Storage** — new table `ai_providers`:

| Column | Notes |
| --- | --- |
| `id` | identity |
| `scope` | `INSTANCE` \| `HOUSEHOLD` |
| `household_id` | null for `INSTANCE`; `UNIQUE (household_id)` for household rows, and at most one `INSTANCE` row (a partial unique index `WHERE scope = 'INSTANCE'`, since NULLs never collide in a plain unique constraint) |
| `type` | `OPENAI_COMPATIBLE` \| `ANTHROPIC` \| `GEMINI` |
| `base_url` | only for `OPENAI_COMPATIBLE` |
| `model` | e.g. `llava:13b`, `gpt-4o-mini`, `claude-sonnet-5-5`, `gemini-2.5-flash` |
| `api_key` | encrypted with `SecretCrypto`; nullable (a local Ollama needs none) |
| `updated_at` | |

**Environment** (instance level, wins over the admin page): `AI_PROVIDER`, `AI_BASE_URL`,
`AI_MODEL`, `AI_API_KEY`, `AI_TIMEOUT` (default `5m`), `AI_CONCURRENCY` (default `2`).

**Resolution:** the household's provider if it has one, else the instance's (environment,
then admin page), else none.

## Receipts

New migration `V4__receipts.sql`:

- `receipts` — `id`, `uuid`, `household_id` (cascade), `created_by_user_id` (set null),
  `status` (`PENDING` | `READY` | `FAILED`), `failure_reason`, `merchant`, `receipt_date`,
  `currency_code`, `total_minor`, `total_mismatch` (boolean), `suggested_category_id`
  (set null), `image_path`, `created_at`, `updated_at`.
- `receipt_items` — `id`, `receipt_id` (cascade), `position`, `name`, `quantity` (numeric,
  nullable), `price_minor`.
- `transactions.receipt_id` — nullable, references `receipts` (set null), unique.

Flow:

1. `POST /api/receipts` (multipart `image`; JPEG, PNG, WebP or HEIC; up to 15 MB; 413 /
   415 otherwise; 409 when no provider applies). The server decodes, downscales to at most
   1600 px on the long side, re-encodes as JPEG (quality 85, EXIF dropped), writes
   `<data dir>/receipts/<household uuid>/<receipt uuid>.jpg`, saves a `PENDING` receipt and
   returns `{ uuid, status }` at once.
2. A bounded executor (`AI_CONCURRENCY` threads, queue) runs the household's extractor with
   `AI_TIMEOUT`. It validates the reply: currency must be in the catalogue (else dropped);
   amounts become minor units in that currency (or the household's base currency);
   `total_mismatch` when the items' sum differs from the total by more than one minor unit
   per item; `suggestedCategory` is matched case-insensitively to an expense category.
   Status becomes `READY` with the fields and items, or `FAILED` with a reason.
3. `GET /api/receipts/{uuid}` → `{ uuid, status, failureReason?, merchant?, date?,
   currency?, total?, totalMismatch, suggestedCategoryId?, items: [{ name, quantity?,
   price }] }`. `GET /api/receipts/{uuid}/image` → the JPEG. Both only for the receipt's
   household (404 otherwise).
4. The transaction links it: `CreateTransactionRequest`/`UpdateTransactionRequest` gain
   `receiptUuid`; `SyncTransaction` gains `receiptUuid` (pushed and pulled);
   `TransactionResponse` gains `receiptUuid`. A receipt from another household is
   rejected like any foreign reference.
5. Cleanup: a scheduled job deletes receipts not linked to a transaction 24 hours after
   creation, file included. Deleting a transaction (tombstone) deletes its receipt and
   file.

`/api/instance` gains `receiptScan: boolean` (an instance-level provider exists);
`GET /api/household/ai-provider` tells a client whether its household can scan.

## Provider settings API

| Endpoint | Who |
| --- | --- |
| `GET/PUT/DELETE /api/admin/ai-provider` | administrator; `PUT` refused (409) when the environment sets the provider |
| `GET/PUT/DELETE /api/household/ai-provider` | `GET` any member; `PUT`/`DELETE` owner only |
| `POST /api/admin/ai-provider/test`, `POST /api/household/ai-provider/test` | same as `PUT`; runs a built-in sample receipt image through the given (unsaved or saved) settings and returns what came back or the reason it failed |

`GET` responses: `{ source: "HOUSEHOLD" | "INSTANCE" | "ENVIRONMENT" | "NONE", type?,
baseUrl?, model?, hasKey, host? }` — the key is never returned. `host` is what the privacy
note shows ("photos are sent to …").

Household `PUT` with type `OPENAI_COMPATIBLE` accepts only the preset URLs; any other URL
is 400.

## Web

- Quick add and the transaction editor: "Scan receipt" (`<input type="file"
  accept="image/*" capture="environment">`), visible when the household can scan. While
  the receipt is read: "Reading the receipt…" with a cancel. `READY` fills amount, date,
  description (merchant), category (suggested), currency (if active); `totalMismatch` shows
  "The items don't add up to the total — check the amount"; items appear in a collapsible
  list; saving sends `receiptUuid`. `FAILED` shows the reason and leaves the form editable.
- Transaction view with a receipt: thumbnail (click for full size, loaded with the
  session) and the item list.
- Settings → "Receipt recognition": which provider applies and where photos go; for the
  owner, the household provider form (preset type, model, key) with Test and Remove. Admin
  page: the instance provider form (any type and URL) with Test and Remove, or "set by the
  environment".

## Android

- Quick add's scan button keeps the system camera capture and uploads the photo instead of
  running Tesseract, then polls and fills the form (adding the category). Without a server
  connection: "Scanning needs a connection to your server". Hidden when the household
  cannot scan.
- The local transaction stores `receiptUuid` (Room migration 5 → 6) and sync pushes it.
- Transaction editor: a "Receipt" row, online only, opens the photo and the items.
- Provider settings are web-only; Android shows "Set up receipt recognition in the web
  app" when the household cannot scan.
- Removed: `tesseract4android`, the bundled language data, `ReceiptParser`, its tests and
  the instrumented recognition test.

## Errors

| Case | Result |
| --- | --- |
| Model unreachable | `FAILED`: "Could not reach the model at {host}" |
| Model rejects images | `FAILED`: "This model cannot read images" |
| Not JSON / wrong shape after one retry | `FAILED`: "The model's answer could not be read" |
| Timeout | `FAILED`: "The model took longer than {timeout}" |
| Items don't add up | `READY` with `totalMismatch` |
| Upload too large / wrong type | 413 / 415 |
| No provider for the household | 409 on upload |
| Household owner sets a non-preset URL | 400 |

## Security and privacy

- Only administrators can point the server at an arbitrary URL; household owners use
  hosted presets — no server-side requests into the operator's network on a household's
  say-so.
- Keys are encrypted at rest, never returned, never logged; photos and model replies are
  not logged.
- Receipt data and photos are served only to the receipt's household.
- The instance privacy page says receipt photos are sent to the configured provider.

## Testing

Server:

- Each adapter against a JDK `com.sun.net.httpserver.HttpServer` in the test: request shape
  (image part, model, auth header), a good reply, a non-JSON reply then retry, an
  images-not-supported error, a timeout.
- Reply validation: amounts to minor units per currency decimals, total mismatch, unknown
  currency dropped, category matching.
- Pipeline: `PENDING` → `READY`/`FAILED` through the executor; the concurrency bound.
- Image handling: a large PNG is stored as a ≤1600 px JPEG without EXIF; 413 and 415.
- Access: another household's receipt and image are 404.
- Linking through REST and through sync push; a foreign receipt uuid rejected.
- Cleanup: unlinked receipts older than 24 h deleted with their file; a tombstoned
  transaction's receipt deleted.
- Provider resolution (household → environment → admin page → none), key masking, the
  household preset-URL rule, the environment lock on the admin `PUT`.

Web: scan with polling filling the form, the mismatch warning, the failed state; the
receipt on a transaction; provider settings for owner, member and admin.

Android: upload and poll in the quick-add view model (Ktor `MockEngine`); Room migration
5 → 6; the build without Tesseract.

## Out of scope

- Splitting a receipt into several transactions.
- Scanning without a server (on-device recognition).
- Provider settings on Android.
- MCP access (sub-project 4).
