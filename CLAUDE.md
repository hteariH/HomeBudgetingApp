# CLAUDE.md

Guidance for Claude Code when working in this repo. Keep this file current.

## What this is

**PersonalMoney** — a local-first Android personal-finance app. No backend; all data lives in
Room on-device. Monetised with AdMob. Trilingual UI (en / ru / uk). The core value is fast,
frictionless expense entry (including a home-screen widget).

Planning & backlog live in companion docs — read them before large changes:
- [PLAN.md](PLAN.md) — original phased plan + current status.
- [ROADMAP.md](ROADMAP.md) — code-grounded improvement backlog (Top-10 already shipped).
- [backlog.md](backlog.md) — small user-requested items.

## Stack

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · WorkManager · Glance (widget) ·
AdMob + UMP · Frankfurter FX API. Min SDK 26, Target SDK 35. Package `com.personalmoney`
(debug app id `com.personalmoney.debug`).

## Build / test / run

Gradle needs the Android Studio JBR. From the repo root:

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
- Layout: `core/{database,domain,data,ui,currency}` + `feature/<name>` + `widget/` + `ads/`.
  Nav in `navigation/Routes.kt` + `PersonalMoneyNavHost.kt`. Bottom nav bar + FAB.
- **Money is `Long` minor units.** Per-currency decimals via `Currency.getInstance(code)
  .defaultFractionDigits` (fallback 2) / `CurrencyRegistry`. Numpad math via `core/calc/Calculator`
  + shared sanitizer `core/calc/ExpressionInput`.
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
- **i18n:** every user-facing string goes in all three of `res/values/`, `res/values-ru/`,
  `res/values-uk/strings.xml`. Never hardcode UI text.

## Git / workflow norms

- Commit or push **only when asked**. If on `master`, the user has been committing/merging there
  directly this project — confirm rather than assume. End commit messages with:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- The user prefers to **evaluate UX alternatives as working builds on separate branches** and decide
  himself — when a change is a judgment call, build it on a branch for comparison.
- Verify load-bearing claims against the actual code before relying on them; verify features on the
  emulator (screenshot + DB) before reporting done.
