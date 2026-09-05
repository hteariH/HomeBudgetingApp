# CLAUDE.md

Guidance for Claude Code when working in this repo. Keep this file current.

## What this is

**PersonalMoney** — an offline-first Android personal-finance app, and a client of the
HomeBudgeting server, which lives in the parent directory of this one: `client/` holds
every client (Android and web), the repository root holds the server. Data lives in Room on-device and converges on the
server through two-way sync, so the app keeps working with no network at all. Monetised with
AdMob. Trilingual UI (en / ru / uk). The core value is fast, frictionless expense entry
(including a home-screen widget).

Planning & backlog live in companion docs — read them before large changes:
- [PLAN.md](PLAN.md) — original phased plan + current status.
- [ROADMAP.md](ROADMAP.md) — code-grounded improvement backlog (Top-10 already shipped).
- [backlog.md](backlog.md) — small user-requested items.

## Stack

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · WorkManager · Glance (widget) ·
Retrofit + OkHttp (server API) · AdMob + UMP · Frankfurter FX API. Min SDK 26, Target SDK 35.
Package `com.personalmoney` (debug app id `com.personalmoney.debug`).

> Being migrated to Kotlin Multiplatform (Compose Multiplatform · Ktor · Room KMP · Koin)
> so the same code serves Android, iOS and the web. Hilt→Koin and Retrofit→Ktor are
> in flight; prefer the multiplatform-friendly option when touching `core/`.
> Premium is sold through **Google Play Billing**, not RevenueCat — see Plans below.

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

Web checks (headless — no visible window needed, unlike a real browser tab, which stops
rendering the moment it goes to the background):

```powershell
# 1. build and stage the bundle where the local server serves /app from
./gradlew.bat wasmJsBrowserDistribution --no-daemon
Copy-Item "composeApp\build\dist\wasmJs\productionExecutable\*" `
          "..\src\main\resources\static\app" -Recurse -Force
# 2. run the server from classes (mvnw spring-boot:run, or `mvn package` then the jar)
# 3. shoot it at any width
& "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --headless=new `
  --enable-unsafe-swiftshader --hide-scrollbars --window-size=1440,900 `
  --virtual-time-budget=40000 --screenshot="$env:TEMP\web.png" `
  --user-data-dir="$env:TEMP\hlprofile" "http://localhost:8085/app/"
```

A live dev loop instead of the build-and-stage dance: `./gradlew.bat wasmJsBrowserDevelopmentRun`
serves the bundle on :8080 and proxies `/api` to the Spring server on :8085
(`composeApp/webpack.config.d/dev-server-proxy.js`), so no CORS configuration exists or is
needed. Use the staged path above when you need to see production output.

`--enable-unsafe-swiftshader` is the load-bearing flag: Compose draws through WebGL, and
without software rasterisation the page renders its rectangles and none of its text.
`--disable-gpu` is *not* a substitute - it produces exactly that half-drawn result.

This is the only way to see the web build's actual pixels. Unit tests cannot: the two bugs
that shipped anyway - a hryvnia rendering as a blank box, and a horizontal scrollbar caused
by the vertical one - were both invisible to 177 green tests and to the Android emulator.


## Architecture & conventions

- Unidirectional flow: `ViewModel(StateFlow)` → Compose. Hilt for DI. Room + Flow for reactive reads.
- Layout: `core/{database,domain,data,ui,currency}` + `feature/<name>` + `widget/` + `ads/`.
  Nav in `navigation/Routes.kt` + `PersonalMoneyNavHost.kt`.
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
- **Plans:** the free plan caps accounts at `PlanLimits.FREE_ACCOUNTS` (5) on the *server*,
  on both the REST and the sync path - a client is never asked whether it has paid. The
  server answers **402** with a sentence meant for the user; `PaymentRequired` is in
  `core/network/ApiErrors.kt`'s `USER_FACING` set so `serverMessage()` surfaces it, and
  `isPlanLimit()` next to it is what lets a screen offer the paywall alongside that
  sentence (see `AccountEditScreen`). Whether an account is premium is
  `User.isPremium(now)` on the server, which reads the expiry rather than trusting the
  stored value. `GET /api/users/me` carries `entitlement` for display only.
- **Buying premium** (`core/billing`, `feature/premium`): **Google Play Billing only, and
  therefore phone only.** Not a preference - Stripe does not accept merchants in Ukraine,
  which rules out every provider built on it including RevenueCat Web Billing, so there is
  no rail to sell on the web. `StoreBilling` is an interface with `PlayStoreBilling`
  (androidMain, `billing-ktx`) and `UnavailableStoreBilling` (web) behind it, bound in
  Koin like `KeyValueStore`. The flow: the store returns a **purchase token**,
  `EntitlementRepository.submit` posts it to `POST /api/billing/play/purchase`, and the
  server asks Google what it bought - **the client's opinion of its own subscription is
  never used**, and `UserPreferences.premium` is a display cache, never a decision.
  `syncOwnedPurchases()` runs on launch from `PersonalMoneyApplication` and is what makes
  a reinstall or a second device work with nothing to press.
  **Premium removes the ads**, and the check lives in one place: `ads/BannerAd` in
  commonMain wraps the platform's `PlatformBannerAd` and hides it for a subscriber, so a
  screen that forgot to ask cannot sell a paying user to an advertiser. `MainActivity`
  additionally skips the UMP consent form, `MobileAds.initialize` and the interstitial
  preload outright - a subscriber is served nothing, so there is nothing to consent to or
  fetch. `EntitlementRepository.premium` is `Flow<Boolean?>` for exactly this: **null means
  not known yet**, and the banner draws nothing until the answer arrives, because guessing
  `false` for the few hundred milliseconds DataStore takes is a flash of advertising in
  front of somebody who paid it away. The paywall renders on both
  platforms; in the browser it says where to buy instead. Premium can also be granted by
  hand - `POST /api/admin/entitlements/users/{id}`, ADMIN only. The whole arrangement is
  written up in `../docs/PREMIUM.md`.
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
- **Receipt scanning** (`core/ocr`, Android only - `receiptScanAvailable` gates the button
  the way `homeMoneyImportAvailable` gates Banks): the system camera via
  `ActivityResultContracts.TakePicture` (full resolution - the preview contract's thumbnail is
  unreadable), then **Tesseract** on-device (`ukr+rus+eng`), then `ReceiptParser` in
  `commonMain`. No `CAMERA` permission (ACTION_IMAGE_CAPTURE is served by the camera app) and
  the capture is deleted from the cache once read, so Play's Data safety answers do not change.
  **Tesseract and not ML Kit because Google ships no Cyrillic model for ML Kit at all** - it
  read `СУМА` as `CYMA` and the merchant name as noise, which is the wrong engine for this
  market. Cost: ~11 MB of `*.traineddata` in `androidMain/assets/tessdata/`, extracted to
  `filesDir/tesseract/tessdata` on first scan (`TESSDATA_VERSION` forces a re-extract), and a
  slower pass. The parser's primary rule stays script-independent (the largest money-shaped
  number); total keywords are matched both as Cyrillic and as homoglyph-folded Latin, because
  `eng` is in the language list and a misread is still possible. A scan fills the QuickAdd form
  and never saves.
- **Encoding defaults:** kotlinx.serialization omits any value equal to its declared
  default, and Jackson fills the gap with `false`/`0` on the other side. A boolean whose
  meaningful state *is* the default needs `@EncodeDefault` - see `RestPlannedAccount` and
  the test that guards it. Give the server DTO the same default too.
- **i18n:** strings live in **`composeApp/src/commonMain/composeResources/values{,-ru,-uk}/
  strings.xml`**, not in `res/values*` - the Android resource table cannot be read from wasm.
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
