# CLAUDE.md

Guidance for Claude Code when working in this repo. Keep this file current.

## What this is

**PersonalMoney** — an offline-first Android personal-finance app, and a client of the
HomeBudgeting server, which lives in the parent directory of this one: `client/` holds
the Android client, the repository root holds the server. The web client is not here: it
is the React app in `../src/main/frontend`, served by the same server at `/app`. Data lives in Room on-device and converges on the
server through two-way sync, so the app keeps working with no network at all. No ads, no
purchases: the app is a client of a self-hosted server whose address the user enters at
sign-in. Trilingual UI (en / ru / uk). The core value is fast, frictionless expense entry
(including a home-screen widget).

Planning & backlog live in companion docs — read them before large changes:
- [PLAN.md](PLAN.md) — original phased plan + current status.
- [ROADMAP.md](ROADMAP.md) — code-grounded improvement backlog (Top-10 already shipped).
- [backlog.md](backlog.md) — small user-requested items.

## Stack

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · WorkManager · Glance (widget) ·
Retrofit + OkHttp (server API) · Frankfurter FX API. Min SDK 26, Target SDK 35.
Package `com.personalmoney` (debug app id `com.personalmoney.debug`).

> Being migrated to Kotlin Multiplatform (Compose Multiplatform · Ktor · Room KMP · Koin)
> so the same code serves Android, iOS and the web. Hilt→Koin and Retrofit→Ktor are
> in flight; prefer the multiplatform-friendly option when touching `core/`.

## Build / test / run

Gradle needs the Android Studio JBR. From `client/` (not the repository root, which
is the Maven server build):

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```

Every change is gated on **`testDebugUnitTest` + `assembleDebug` both green** before it's considered done.

On-device checks (emulator `Pixel_6_API35`, 1080×2400):

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
# screenshot (PowerShell '>' corrupts binary — pull instead):
& $adb shell screencap -p /sdcard/v.png; & $adb pull /sdcard/v.png "$env:TEMP\v.png"; & $adb shell rm /sdcard/v.png
# inspect the DB:
& $adb shell run-as com.personalmoney.debug sqlite3 databases/personalmoney.db "'SELECT ...;'"
```

Tests: JUnit + MockK + Turbine, with a `MainDispatcherRule` (UnconfinedTestDispatcher) + `runTest`.


## Architecture & conventions

- Unidirectional flow: `ViewModel(StateFlow)` → Compose. Hilt for DI. Room + Flow for reactive reads.
- Layout: `core/{database,domain,data,ui,currency}` + `feature/<name>` + `widget/`.
  Nav in `navigation/Routes.kt` + `PersonalMoneyNavHost.kt`.
- **Web leftovers.** There used to be a wasmJs target serving the web app; it was replaced
  by React and removed. Code in commonMain written for the browser - the web chrome below,
  `BrowserUrl`, the Banks/CSV/HomeMoney screens (gated off by `bankConnectionsAvailable` /
  `homeMoneyImportAvailable`) - is now unused on Android and is kept only until a cleanup.
- **Chrome differs by platform, not by window size** - `core/ui/webChrome` (expect/actual).
  Android: rail on a wide window, bottom bar + FAB on a phone. Web: `navigation/AppHeader.kt`
  across the top (brand, sections, theme switch, account menu with Settings and Sign out),
  folding into a `ModalNavigationDrawer` below 1240dp and dropping the wordmark below 480dp;
  no bottom bar and no FAB, so below 900dp the header carries the "+" instead. Screens' own
  `TopAppBar`s pass `colors = pageBarColors()`, which goes transparent under that header so
  the title reads as a page heading rather than a second band.
- Browser URLs track the back stack via `navigation/BrowserUrl.kt` (`bindToBrowserNavigation`,
  no-op on Android). Routes are URL **fragments** - `/app/#accounts` - so the server needs no
  rewrite rule; the start destination stays fragment-less.
- **Money is `Long` minor units.** Per-currency decimals via `Currency.getInstance(code)
  .defaultFractionDigits` (fallback 2) / `CurrencyRegistry`. Amounts are typed on the **system keyboard**
  (`core/ui/components/AmountField`, `KeyboardType.Decimal`) - the in-app numpad is gone, and
  with it the layout preference it needed. `core/calc/Calculator` still evaluates, so a
  pasted or scanned `12+3.40` works; `core/calc/ExpressionInput.sanitize` cleans what the
  keyboard hands over, because a `Decimal` IME is a hint and not a guarantee.
- **Transfers** = a pair of `TransactionEntity` rows (negative debit on source, positive credit on
  destination) linked by `transferGroupId`, cross-wired via `transferCounterpartAccountId`, optional
  `transferFeeMinor` on the debit. Saved atomically with `TransactionRepository.insertTransferPair`.
  `TransactionType` = EXPENSE / INCOME / TRANSFER. Transfer is the 3rd mode of the QuickAdd screen
  (`feature/quickadd/QuickAddScreen` hosts `TransferViewModel`); the standalone `feature/transfers`
  screen is legacy/unreferenced.
- **FX** = Frankfurter **v2** (`api.frankfurter.dev`, base URL in `core/di/NetworkModule`). Endpoint
  `v2/rates`, param `quotes` (comma-separated), response is an **array** of `FrankfurterRate`
  `{date,base,quote,rate}`. v2 covers ~165 currencies incl. UAH/RUB (v1 `api.frankfurter.app` is
  ECB-only ~30 and silently omits UAH — do not revert). `FxRefreshWorker` runs daily *and* once on
  launch (`FxScheduler.refreshNow`). Synchronous conversion via `FxSnapshot.from(rates)`.
  **It carries no crypto, and one unknown code fails the whole request with 422** — so the worker
  asks `v2/currencies` first and sends only what that list contains. Without it a single imported
  USDT account froze every rate on the device for good, `Result.retry()` re-running the same
  doomed request; the server's `FxService` has the same guard for the same reason.
  Dollar stablecoins convert through `core/currency/FxPegs` (USDT/USDC = 1 USD, a definition
  rather than a quote), and anything else a feed will not carry — BTC, a voucher currency
  somebody invented — needs a rate typed on the currency edit screen, stored against USD
  because that is the pivot `FxSnapshot` triangulates through. **Any stored rate beats a peg**,
  so a depeg is the user's to state. An account that still cannot be converted is left out of the
  dashboard total *and said so* under it (`convertibleAccountsCount`) — the header used to count
  all 26 accounts over a sum that held 24.
- **Sync & auth:** `core/sync/SyncEngine` pulls, merges in one Room transaction (parents before
  children, uuid→local-id), then pushes. Every syncable row carries `uuid` / `updatedAt` /
  `deleted` / `pendingSync` (`SyncableEntity`); deletes are tombstones, concurrency is
  optimistic via `baseUpdatedAt`. A syncable entity must also declare
  `Index(value = ["uuid"], unique = true)` and `Index("pending_sync")` - the unique one is
  what stops a merge leaving the same row twice. `SubscriptionEntity` was missing both,
  while `MIGRATION_2_3` created them anyway, so an upgraded phone and a freshly installed
  one had different schemas for v3; `MIGRATION_3_4` converges them. Schema `N.json` is
  frozen once shipped - fix a schema by adding a version, never by editing the recorded one. **uuid is the identity across clients — server ids are not
  portable.** Tokens live in `core/auth/TokenStore` (EncryptedSharedPreferences); refresh is
  handled once, under a mutex, by `TokenAuthenticator` — never refresh at a call site.
- **Design:** the palette, radii and type scale come from the React client's tokens
  (`../src/main/frontend/src/index.css`), ported in `core/ui/theme/`. Colours are named by
  role (`DarkCard`, `Accent`, `IncomeGreenDark`), never by hue, and screens read Material
  slots - `surfaceContainer` is a card, `outlineVariant` is a 1px border. Money colours and
  the third text step come from `LocalFinanceColors`, which differs per theme: `#10B981`
  as text on white is 2.47:1, so light mode uses a darker step. **Never hardcode a colour
  in a screen** - if a slot is missing, add it to `FinanceSemanticColors`. Material 3 pins
  buttons to a pill regardless of `MaterialTheme.shapes`, so a button that should match the
  web app passes `shape = PmButtonShape`. Material You is off by default: it repaints the
  app from the wallpaper, which is the opposite of one product on two platforms.
- **No plans, ads or purchases:** every account is the same, and there is no billing or
  advertising code. Do not add a dependency that phones home (ads, analytics, crash
  reporting) - people run their own server precisely so that nothing else sees their data.
- **Importing from HomeMoney** (`feature/homemoney`, web only like Banks - see
  `navigation/homeMoneyImportAvailable`): the server owns it end to end
  (`com.homebudget.app.importer.homemoney`), because homemoney.ua sends no CORS headers and
  a browser cannot call it at all. Consent is an OAuth redirect: `POST /authorize` returns a
  URL *and* the signed state minted with it, the page stores the state
  (`UserPreferences.homeMoneyAuthState`) and *navigates* to the URL
  (`core/ui/rememberPageNavigator`, not `rememberUrlOpener` - a new tab would swallow the
  return). **homemoney.ua does not return `state`** - their redirect carries `code` alone -
  so the callback cannot tell whose consent came back and does not try: it bounces to
  `/app/?homemoney=pending&code=…`, `main.kt` parses that into a `HomeMoneyRedirect` and
  drops the query (the code is single-use), and the ViewModel spends it over
  `POST /connection/code`, which is authenticated and checks the stored state against the
  signed-in user. Requiring `state` on the callback instead is what made a refused import
  look like nothing happening at all - no row, no log line, no message. Their API
  answers **HTTP 200 even for errors** and rejects a trailing slash on the path; both are
  documented on `HomeMoneyApi`. Dedup is `transactions.external_id = "homemoney:<GUID>"`, so
  re-running imports nothing twice. An account-and-currency pair that is **empty and never
  appeared in any operation** arrives unticked (`HomeMoneyMapper.untickUnusedCurrencySlots`):
  HomeMoney's accounts carry currency slots that cost nothing to add, so one deposit reports
  itself in five currencies that were never used. Both conditions matter - a zero balance
  alone would drop the history of an account merely spent down to nothing, and with it the
  far end of every transfer it took part in.
- **Receipt scanning** (`core/receipt`, Android only - `receiptScanAvailable` gates the
  button the way `homeMoneyImportAvailable` gates Banks): the system camera via
  `ActivityResultContracts.TakePicture` (`rememberReceiptCamera`; full resolution - the preview
  contract's thumbnail is unreadable), then the photo goes to the server (`POST /api/receipts`,
  part `image`) and `ReceiptScanner` polls `GET /api/receipts/{uuid}` every 2 s for up to 6 min.
  The server reads it with whichever model the household or the instance configured; provider
  settings are web-only, and the button is shown only when `GET /api/household/ai-provider`
  says the source is not `NONE` (Settings then says to set it up on the web). No `CAMERA`
  permission (ACTION_IMAGE_CAPTURE is served by the camera app) and the capture is deleted
  from the cache once read. A scan fills the QuickAdd form - amount, date, merchant as note,
  the suggested category matched by uuid - and never saves; the entry keeps `receiptUuid`
  (Room v6), which sync pushes, and the transaction editor opens the photo and items from the
  server (online only). The server reads a null `receiptUuid` in a push as "keep the link",
  so the phone never needs to send one to detach. On-device Tesseract was removed with this.
- **Encoding defaults:** kotlinx.serialization omits any value equal to its declared
  default, and Jackson fills the gap with `false`/`0` on the other side. A boolean whose
  meaningful state *is* the default needs `@EncodeDefault` - see `RestPlannedAccount` and
  the test that guards it. Give the server DTO the same default too.
- **i18n:** strings live in **`composeApp/src/commonMain/composeResources/values{,-ru,-uk}/
  strings.xml`**, not in `res/values*` (chosen when the wasm web target still existed). The web
  app's copy of these strings is `../src/main/frontend/src/i18n/locales/app.*.ts`, converted once
  by `../scripts/strings-to-ts.mjs` and edited directly since.
  Read them with `stringResource(Res.string.x)` (composable) or the **suspend**
  `getString(Res.string.x)` (anywhere else), both from `org.jetbrains.compose.resources`;
  `Res` and the accessors come from `com.personalmoney.resources.*`. Every key exists in all
  three locales - never hardcode UI text. Counting strings use `<plurals>` +
  `pluralStringResource`. Only `app_name` and `widget_quick_expense` stay in `res/values*`,
  because the manifest and the widget-info XML are read by the platform.

## Git / workflow norms

- Commit or push **only when asked**. If on `master`, the user has been committing/merging there
  directly this project — confirm rather than assume.
- **No `Co-Authored-By` trailer and no AI attribution of any kind.** Every commit stands fully in
  the repository owner's name. Write the message and stop.
- The user prefers to **evaluate UX alternatives as working builds on separate branches** and decide
  himself — when a change is a judgment call, build it on a branch for comparison.
- Verify load-bearing claims against the actual code before relying on them; verify features on the
  emulator (screenshot + DB) before reporting done.
