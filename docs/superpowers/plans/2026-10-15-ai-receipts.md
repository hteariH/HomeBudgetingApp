# AI Receipt Scanning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A photo of a receipt becomes a filled-in expense, read asynchronously by an AI model the administrator or household owner configured; the receipt's items and a compressed photo stay with the transaction; Tesseract is removed.

**Architecture:** Server `ai` package (provider settings with resolution household → environment → admin page; three `ReceiptExtractor` adapters over `java.net.http.HttpClient`) and `receipt` package (upload, compress, bounded executor, validation, polling endpoints, image endpoint, cleanup, linking via REST and sync). Web and Android upload, poll and fill their entry forms.

**Tech Stack:** Kotlin/Spring Boot/Flyway/Postgres (H2 PostgreSQL mode in tests), JDK `com.sun.net.httpserver.HttpServer` as the fake model in tests, `javax.imageio` + TwelveMonkeys `imageio-webp`; React 19/TanStack Query/Vitest; Compose Multiplatform/Ktor/Room.

**Spec:** [docs/superpowers/specs/2026-10-15-ai-receipts-design.md](../specs/2026-10-15-ai-receipts-design.md)

## Global Constraints

- Migrations: `V4__ai_providers.sql` (Task 1), `V5__receipts.sql` (Task 3). V1–V3 are released and not edited. (Deviation from the spec's single `V4__receipts.sql`: two migrations, one per task.)
- Accepted uploads: JPEG, PNG, WebP up to 15 MB (413 above; 415 for anything else, HEIC included, with the message "Send the photo as JPEG or PNG"). (Deviation: the spec listed HEIC; there is no pure-Java HEIC decoder. Browsers and the Android camera send JPEG.)
- Stored photo: longest side ≤ 1600 px, JPEG quality 0.85, no metadata, at `<app.data-dir>/receipts/<household uuid>/<receipt uuid>.jpg`.
- Provider types: `OPENAI_COMPATIBLE`, `ANTHROPIC`, `GEMINI`. Household presets for `OPENAI_COMPATIBLE`: `https://api.openai.com/v1`, `https://openrouter.ai/api/v1`; any other URL from a household is 400. Anthropic and Gemini URLs are fixed: `https://api.anthropic.com/v1/messages`, `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`.
- Environment: `AI_PROVIDER`, `AI_BASE_URL`, `AI_MODEL`, `AI_API_KEY`, `AI_TIMEOUT` (default `5m`), `AI_CONCURRENCY` (default `2`). Environment wins over the admin page; the admin `PUT` answers 409 when the environment sets the provider.
- Keys: encrypted with `SecretCrypto` (JPA `@Convert`), never returned (`hasKey` only), never logged. Photos and model replies never logged.
- Receipt statuses `PENDING` | `READY` | `FAILED`; unlinked receipts deleted 24 h after creation; a tombstoned transaction's receipt deleted with its file.
- Failure reasons (exact): "Could not reach the model at {host}", "This model cannot read images", "The model's answer could not be read", "The model took longer than {timeout}".
- UI strings in en, ru and uk. Do not push, tag or deploy.

## Commands

- Server: `./mvnw -B -Dfrontend.skip=true test` (`-Dtest=Name`). Docker is not running; Testcontainers tests skip.
- Web: `cd src/main/frontend && npm test && npm run typecheck`.
- Android (Git Bash, from `client/`): `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; ./gradlew testDebugUnitTest assembleDebug --no-daemon`.

---

# Phase A — Server (`src/main/kotlin/com/homebudget/app/`)

### Task 1: Provider settings and resolution

**Files:** create `ai/AiProviderType.kt`, `ai/AiProviderEntity.kt` (`ai_providers`), `ai/AiProviderRepository.kt`, `ai/AiProviderSettings.kt` (resolution + validation), `ai/AiProviderController.kt`, `ai/AiDtos.kt`, `src/main/resources/db/migration/V4__ai_providers.sql`; modify `instance/InstanceInfo.kt` + `InstanceController.kt` (`receiptScan`), `application.yml` (`app.ai.*` from the env names), `.env.example` and `README.md` (AI section), `docker-compose.yml` (pass the six `AI_*` variables like the others, with defaults `AI_TIMEOUT:-5m`, `AI_CONCURRENCY:-2`, others `:-`). Tests: `ai/AiProviderSettingsTest.kt`, `ai/AiProviderEndpointsTest.kt`.

**Interfaces (produced):**
- `enum class AiProviderType { OPENAI_COMPATIBLE, ANTHROPIC, GEMINI }`; `enum class AiProviderScope { INSTANCE, HOUSEHOLD }`; `enum class AiProviderSource { HOUSEHOLD, ENVIRONMENT, INSTANCE, NONE }`
- `data class ResolvedProvider(val type: AiProviderType, val baseUrl: String, val model: String, val apiKey: String?, val source: AiProviderSource)` — `baseUrl` is the full endpoint base (for Anthropic/Gemini the fixed URL)
- `AiProviderSettings.resolveFor(householdId: Long): ResolvedProvider?`, `instanceProvider(): ResolvedProvider?`, `timeout: Duration`, `concurrency: Int`
- `ProviderView(source, type?, baseUrl?, model?, hasKey: Boolean, host?)`, `ProviderRequest(type, baseUrl?, model, apiKey?)` (`apiKey` null on PUT keeps the stored key; empty string clears it)
- Endpoints: `GET/PUT/DELETE /api/admin/ai-provider` (ADMIN), `GET/PUT/DELETE /api/household/ai-provider` (GET any member, PUT/DELETE owner via `requireOwner`)
- `InstanceInfo.receiptScan: Boolean` (= `instanceProvider() != null`)

Schema:

```sql
-- V4: which AI model reads receipts - one for the instance, optionally one per household.
CREATE TABLE ai_providers (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    scope        VARCHAR(20)                 NOT NULL,
    household_id BIGINT                      NULL REFERENCES households (id) ON DELETE CASCADE,
    type         VARCHAR(30)                 NOT NULL,
    base_url     VARCHAR(500)                NULL,
    model        VARCHAR(200)                NOT NULL,
    api_key      VARCHAR(2048)               NULL,
    updated_at   TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_provider_household UNIQUE (household_id)
);
CREATE UNIQUE INDEX uq_ai_provider_instance ON ai_providers (scope) WHERE scope = 'INSTANCE';
```

(If H2 rejects the partial index, enforce "one INSTANCE row" in `AiProviderSettings` with an upsert and say so; keep the index for Postgres via a comment-documented alternative only if H2 truly fails.)

Steps (TDD): write `AiProviderSettingsTest` first — resolution order (household > environment > admin page > none) using `@TestPropertySource` for the env case; household PUT with `OPENAI_COMPATIBLE` and a non-preset URL → `IllegalArgumentException`; admin may set `http://ollama:11434/v1`; `apiKey` null keeps the old key; the stored column is not the plain key (read it with a native query). `AiProviderEndpointsTest` (MockMvc): GET never contains the key and shows `hasKey`; member PUT → 403; admin PUT under env → 409; `/api/instance` shows `receiptScan`. Implement; run the suite; commit "Let the administrator and household owners choose the model that reads receipts".

---

### Task 2: The three extractors and the Test button

**Files:** create `ai/ReceiptExtractor.kt` (interface, `ExtractionContext`, `ExtractedReceipt`, `ExtractedItem`, `ExtractionException`), `ai/ReceiptPrompt.kt` (prompt text + JSON parsing with one retry), `ai/OpenAiCompatibleExtractor.kt`, `ai/AnthropicExtractor.kt`, `ai/GeminiExtractor.kt`, `ai/ExtractorFactory.kt`, `src/main/resources/ai/sample-receipt.jpg` (a small synthetic receipt image: generate it in a test-independent way — draw text "SHOP / Milk 1.20 / Bread 0.80 / TOTAL 2.00" with `java.awt` once, save the file and commit it); modify `ai/AiProviderController.kt` (`POST …/test` for admin and household). Tests: `ai/ExtractorsTest.kt` using `com.sun.net.httpserver.HttpServer` on a random port.

**Interfaces (produced):**

```kotlin
data class ExtractionContext(val expenseCategories: List<String>, val currencies: List<String>)
data class ExtractedItem(val name: String, val quantity: java.math.BigDecimal?, val price: java.math.BigDecimal)
data class ExtractedReceipt(
    val merchant: String?, val date: java.time.LocalDate?, val currency: String?,
    val total: java.math.BigDecimal?, val items: List<ExtractedItem>, val suggestedCategory: String?
)
class ExtractionException(val reason: String) : RuntimeException(reason)
interface ReceiptExtractor { fun extract(jpeg: ByteArray, context: ExtractionContext): ExtractedReceipt }
class ExtractorFactory(...) { fun create(provider: ResolvedProvider, timeout: java.time.Duration): ReceiptExtractor }
```

- Prompt: instructs the model to read the receipt and reply with only the JSON object of the spec's shape, choosing `suggestedCategory` from `context.expenseCategories` and `currency` from `context.currencies` when possible.
- Parsing (`ReceiptPrompt.parse(text): ExtractedReceipt`): strip code fences, parse with Jackson, tolerate numbers as strings or numbers, bad dates → null. Invalid JSON → the extractor sends one follow-up/retry request with "Reply with the JSON object only.", then throws `ExtractionException("The model's answer could not be read")`.
- Wire formats as in the spec table. OpenAI-compatible: `Authorization: Bearer <key>` when a key exists; content parts `[{type:"text"}, {type:"image_url", image_url:{url:"data:image/jpeg;base64,…"}}]`; `response_format: {type:"json_object"}` (if the server answers 400 mentioning `response_format`, retry once without it). Anthropic: headers `x-api-key`, `anthropic-version: 2023-06-01`; `max_tokens: 2048`; content `[{type:"image", source:{type:"base64", media_type:"image/jpeg", data}}, {type:"text"}]`. Gemini: `?key=` query or `x-goog-api-key` header; `contents[0].parts = [{inline_data:{mime_type:"image/jpeg", data}}, {text}]`; `generationConfig.responseMimeType: "application/json"`.
- Errors: connect failure/`IOException` → "Could not reach the model at {host}"; HTTP 4xx whose body mentions image/vision/multimodal (case-insensitive) → "This model cannot read images"; `HttpTimeoutException` → "The model took longer than {timeout as e.g. 5m}"; other non-2xx → "Could not reach the model at {host}".
- Test endpoint: body `ProviderRequest` (unsaved settings; household rules apply to the household endpoint) or empty to test the saved one; runs the sample image with an empty context; returns `{ ok: true, receipt: ExtractedReceipt }` or `{ ok: false, reason }` (200 either way).

Steps (TDD): `ExtractorsTest` — for each adapter, a fake server asserts the request (path, auth header, model, the base64 image present) and returns a canned good reply → parsed receipt; a non-JSON reply then a good one → success after retry with exactly 2 requests; a 400 "model does not support image input" → the images reason; a delayed reply beyond a 1 s timeout → the timeout reason. Plus `ReceiptPrompt` parse cases (fences, numeric strings). Implement; suite; commit "Read receipts with OpenAI-compatible, Anthropic and Gemini models".

---

### Task 3: Receipts — upload, reading, polling, photos, linking, cleanup

**Files:** create `receipt/Receipt.kt`, `receipt/ReceiptItem.kt`, `receipt/ReceiptRepository.kt`, `receipt/ReceiptImages.kt` (decode/scale/encode/store/delete), `receipt/ReceiptService.kt`, `receipt/ReceiptReader.kt` (bounded executor), `receipt/ReceiptController.kt`, `receipt/ReceiptDtos.kt`, `receipt/ReceiptCleanup.kt`, `src/main/resources/db/migration/V5__receipts.sql`; modify `domain/Transaction.kt` (`receipt`), `dto/Dtos.kt` (`receiptUuid` on create/update requests and `TransactionResponse`), `dto/SyncDtos.kt` (`SyncTransaction.receiptUuid`), `service/TransactionService.kt`, `service/SyncService.kt` (apply + toSync), `service/Mappers.kt`, `controller/GlobalExceptionHandler.kt` (413/415), `application.yml` (`spring.servlet.multipart.max-file-size: 15MB`, `max-request-size: 16MB`), `pom.xml` (`com.twelvemonkeys.imageio:imageio-webp:3.12.0`), privacy page `src/main/frontend/public/privacy.html` (one sentence: receipt photos go to the configured provider). Tests: `receipt/ReceiptImagesTest.kt`, `receipt/ReceiptFlowTest.kt`, `receipt/ReceiptLinkTest.kt`, `receipt/ReceiptCleanupTest.kt`.

Schema:

```sql
-- V5: receipts read by an AI model, their items, and the transaction they became.
CREATE TABLE receipts (
    id                    BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    uuid                  VARCHAR(36)                 NOT NULL UNIQUE,
    household_id          BIGINT                      NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    created_by_user_id    BIGINT                      NULL REFERENCES users (id) ON DELETE SET NULL,
    status                VARCHAR(20)                 NOT NULL,
    failure_reason        VARCHAR(500)                NULL,
    merchant              VARCHAR(255)                NULL,
    receipt_date          DATE                        NULL,
    currency_code         VARCHAR(10)                 NULL,
    total_minor           BIGINT                      NULL,
    total_mismatch        BOOLEAN                     NOT NULL DEFAULT FALSE,
    suggested_category_id BIGINT                      NULL REFERENCES categories (id) ON DELETE SET NULL,
    image_path            VARCHAR(500)                NOT NULL,
    created_at            TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP(3) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_receipts_household ON receipts (household_id);

CREATE TABLE receipt_items (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    receipt_id  BIGINT        NOT NULL REFERENCES receipts (id) ON DELETE CASCADE,
    position    INT           NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    quantity    NUMERIC(14,3) NULL,
    price_minor BIGINT        NOT NULL
);

ALTER TABLE transactions ADD COLUMN receipt_id BIGINT NULL REFERENCES receipts (id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uq_transactions_receipt ON transactions (receipt_id);
```

**Interfaces (produced):**
- `POST /api/receipts` (multipart part `image`) → 202 `{ uuid, status: "PENDING" }`; 409 `{message:"Receipt recognition is not set up"}` without a provider; 413/415.
- `GET /api/receipts/{uuid}` → `ReceiptResponse(uuid, status, failureReason?, merchant?, date?, currency?, total?: String (decimal), totalMismatch, suggestedCategoryId?, suggestedCategoryUuid?, items: [{name, quantity?: String, price: String}])`; 404 for another household. (`suggestedCategoryUuid` lets the offline-first Android client match its local category.)
- `GET /api/receipts/{uuid}/image` → `image/jpeg`; 404 for another household.
- `CreateTransactionRequest.receiptUuid`, `UpdateTransactionRequest.receiptUuid`, `SyncTransaction.receiptUuid`, `TransactionResponse.receiptUuid` (all nullable strings).

Rules:
- Upload: check the provider resolves first (409), validate size/type, `ReceiptImages.store` (ImageIO read; null reader → 415; scale with `Graphics2D` bilinear to ≤1600 px; write JPEG 0.85 via `ImageWriteParam`; a fresh `BufferedImage` carries no metadata), save `PENDING`, then submit to `ReceiptReader` **after commit** (`TransactionSynchronization.afterCommit`) so the worker sees the row.
- `ReceiptReader`: a `ThreadPoolExecutor(concurrency, concurrency, …, LinkedBlockingQueue)`; each job resolves the provider again, builds `ExtractionContext` (household expense category names, active currency codes — fall back to the full catalogue codes when none are active), calls the extractor, then in a new transaction writes `READY` (validated fields + items) or `FAILED` (reason). Unexpected exceptions → `FAILED` with "The model's answer could not be read". Expose a test hook (`awaitIdle(timeout)`) or let tests poll the repository.
- Validation: currency kept only if in the catalogue; decimals from that currency (else the household base currency, else 2); amounts → minor units with `MinorUnits.toMinor`; `totalMismatch` when items exist and `|Σ price − total| > items.size` minor units; `suggestedCategory` matched case-insensitively to the household's non-archived EXPENSE categories.
- Linking: create/update with `receiptUuid` → look up the receipt in the caller's household (else 404 / sync `UNKNOWN_REFERENCE`); a receipt already linked to another transaction → 400 / sync `INVALID`. Sync pull sends `receiptUuid`.
- Transaction tombstone (REST delete, sync `deleted = true`) → delete its receipt row and file.
- `ReceiptCleanup`: `@Scheduled(fixedDelayString = "PT1H")` deletes receipts with no linked transaction and `created_at < now − 24h`, files included.

Steps (TDD):
1. `ReceiptImagesTest`: a 4000×3000 PNG is stored as JPEG with longest side 1600; a GIF/HEIC-like byte array → 415 path; a 16 MB upload → 413 (via MockMvc in `ReceiptFlowTest`).
2. `ReceiptFlowTest` (MockMvc + a fake OpenAI-compatible `HttpServer`, admin provider configured to it): upload → 202 PENDING → poll until READY with merchant, total in minor units → `/image` returns `image/jpeg`; another household gets 404 on both; a fake returning garbage twice → FAILED with the exact reason; no provider → 409.
3. `ReceiptLinkTest`: create via REST with `receiptUuid` → `TransactionResponse.receiptUuid`; push via sync with `receiptUuid` → linked, and pull returns it; a foreign receipt → 404 / `UNKNOWN_REFERENCE`; deleting the transaction deletes the receipt and its file.
4. `ReceiptCleanupTest`: an unlinked receipt older than 24 h is deleted with its file; a linked one and a fresh one stay.
5. Implement, suite, commit "Read receipts in the background and keep them with their transactions".

---

# Phase B — Web (`src/main/frontend/src/`)

### Task 4: Provider settings pages and an authenticated image helper

**Files:** modify `app/api/http.ts` (export `apiBlob(path): Promise<Blob>` reusing `send` and the refresh-and-retry logic), `app/api/types.ts` (`ProviderView`, `ProviderRequest`, `ProviderTestResult`, `InstanceDto.receiptScan`), `app/api/endpoints.ts` (`adminAiProvider`, `setAdminAiProvider`, `deleteAdminAiProvider`, `testAdminAiProvider`, `householdAiProvider`, `setHouseholdAiProvider`, `deleteHouseholdAiProvider`, `testHouseholdAiProvider`), `app/api/queryClient.ts` (`qk.aiProvider`, `qk.adminAiProvider`); create `app/features/ai/ProviderForm.tsx` (shared form: type select, base URL — free text for admin, preset select for household `OPENAI_COMPATIBLE`, model, key with "leave empty to keep" placeholder when `hasKey`, Test, Save, Remove) and `app/features/ai/ProviderForm.test.tsx`; modify `app/features/settings/SettingsPage.tsx` (new Card "Receipt recognition" between the privacy/about card and the danger zone: shows source and host; owner sees `ProviderForm` in household mode; members see a sentence), `app/features/admin/AdminPage.tsx` (new Card with `ProviderForm` in admin mode, or "Set by the environment" when `source === 'ENVIRONMENT'`), locales `app.{en,ru,uk}.ts`.

Tests first: `http.test.ts` — `apiBlob` sends the bearer token and retries once after refresh on 401; `ProviderForm.test.tsx` — household mode offers only the two presets for OpenAI-compatible, Test shows the returned reason, Save sends the body without `apiKey` when the field is empty; Settings shows "Photos are sent to {host}". Implement; `npm run typecheck && npm test`; commit "Add receipt-recognition settings for owners and the administrator".

### Task 5: Scanning in the entry form and the receipt on a transaction

**Files:** create `app/features/receipts/useReceiptScan.ts` (upload via `endpoints.uploadReceipt(file)` with `FormData` part `image` and a filename, poll `endpoints.receipt(uuid)` every 2 s until not `PENDING`, cancel), `app/features/receipts/ScanButton.tsx`, `app/features/receipts/ReceiptView.tsx` (thumbnail via `apiBlob` + `URL.createObjectURL` with cleanup, click opens a full-size dialog, item list), tests for each; modify `app/api/{types,endpoints}.ts` (`ReceiptDto`, `uploadReceipt`, `receipt`, `receiptImage`; `receiptUuid` on create/update requests and `RestTransaction`), `app/features/quickadd/EntryForm.tsx` (scan button for INCOME/EXPENSE modes when the household can scan — `GET /api/household/ai-provider` source ≠ NONE; on READY fill amount, day, note, category; if the receipt currency differs from the account's, leave the amount and show a note; `totalMismatch` warning; collapsible items; save sends `receiptUuid`; reset clears it), `app/features/transactions/TransactionEditPage.tsx` (a `ReceiptView` card when `tx.receiptUuid`), locales (reuse `quickadd_scan_receipt`, `quickadd_scan_failed`; add `receipt_reading`, `receipt_cancel`, `receipt_mismatch`, `receipt_items`, `receipt_title`, `receipt_currency_differs`).

Tests first: `useReceiptScan` with a fetch stub — PENDING, PENDING, READY → resolves the draft and makes exactly three GETs; FAILED → exposes the reason; cancel stops polling. EntryForm: a READY receipt fills the fields and the save body contains `receiptUuid`; the scan button is absent when the provider source is NONE. TransactionEditPage shows the item names when `receiptUuid` is set. Implement; typecheck + tests; commit "Scan receipts in the entry form and show them on transactions".

---

# Phase C — Android (`client/composeApp/src/`)

### Task 6: Scan through the server, keep the receipt, drop Tesseract

**Files:**
- Delete: `androidMain/.../core/ocr/ReceiptScanner.android.kt` Tesseract parts, `commonMain/.../core/ocr/ReceiptParser.kt`, `commonTest/.../core/ocr/ReceiptParserTest.kt`, `androidInstrumentedTest/.../core/ocr/ReceiptRecognitionTest.kt`, `androidMain/assets/tessdata/`, the `tesseract4android` dependency (build.gradle.kts lines 138 and 155, libs.versions.toml version + library + comment).
- Keep/rename: the camera capture (TakePicture into `cacheDir/receipts` via the existing FileProvider) becomes `expect fun rememberReceiptCamera(onCaptured: (ByteArray) -> Unit, onFailed: () -> Unit): () -> Unit` (`androidMain` reads `openInputStream(uri).readBytes()` then deletes the capture); `receiptScanAvailable` stays as the platform capability.
- Create: `commonMain/.../core/receipt/ReceiptApi` methods on `HomeBudgetApi` (`uploadReceipt(bytes): ReceiptDto` multipart part `image` with `filename="receipt.jpg"` and `image/jpeg`, `receipt(uuid): ReceiptDto`, `householdAiProvider(): ProviderViewDto`), `commonMain/.../core/receipt/ReceiptScanner.kt` (`suspend fun scan(bytes): ScanOutcome` = upload + poll every 2 s up to 6 min; `ScanOutcome.Ready(draft) | Failed(reason) | Offline`), DTOs in `ApiDtos.kt`.
- Modify: QuickAdd VM/screen (button visible when `receiptScanAvailable && canScan`, where `canScan` comes from `householdAiProvider().source != "NONE"` fetched on screen open, failures → hidden; while reading show a progress indicator; Ready → `setAmountText(total)`, `setDate`, `setNote(merchant)`, select the suggested category when it exists locally (match by `ReceiptDto.suggestedCategoryUuid` to the local category's uuid), remember `receiptUuid`; Failed → snackbar with the reason; Offline → "Scanning needs a connection to your server"); domain `Transaction.receiptUuid`, `TransactionEntity.receiptUuid` (column `receipt_uuid`), `MIGRATION_5_6` (`ALTER TABLE transactions ADD COLUMN receipt_uuid TEXT`), `AppDatabase` version 6 + `6.json`, `SyncTransactionDto.receiptUuid` (pushed and pulled; `toDto`/`toEntity`), `RoomTransactionRepository` keeps it on edit, transaction editor: a "Receipt" row when `receiptUuid` is set that opens a dialog loading the image (`HomeBudgetApi.receiptImage(uuid): ByteArray`, decoded with `BitmapFactory` in an androidMain actual or Compose `decodeToImageBitmap`) and the items, online only; strings en/ru/uk (`receipt_reading`, `receipt_offline`, `receipt_title`, `receipt_items`, `receipt_setup_on_web`), drop `quickadd_scan_nothing_found`.

Tests first: commonTest `ReceiptScannerTest` (MockEngine: POST → PENDING, GET PENDING, GET READY → `Ready` with the draft; GET FAILED → `Failed(reason)`; IOException → `Offline`; asserts the multipart part name `image`); androidUnitTest: `SyncMappers` push and pull carry `receiptUuid`; QuickAdd VM saves `receiptUuid` after a Ready scan; `MigrationTest.migrate5To6_addsTheReceiptColumn` (compiles; run if a device exists). Build must pass with no `tesseract` left: `grep -rni "tesseract\|tessdata\|ReceiptParser" client/composeApp/src client/composeApp/build.gradle.kts client/gradle` is empty. Commit "Scan receipts through the server and drop Tesseract".

---

## After the last task

Every suite once more (server, web, site, Android), then superpowers:finishing-a-development-branch.
