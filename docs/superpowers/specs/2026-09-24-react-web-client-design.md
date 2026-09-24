# React web client

Replace the Compose/WebAssembly web client at `/app` with React, and make the landing page,
the sign-in pages and the application one single-page app.

## Why

The web client is the `wasmJs` target of the Compose Multiplatform project in `client/`.
It needs its own CI job with an Android SDK, ships a multi-megabyte `.wasm` bundle, and
signing in means handing the tab over from the React landing page to a different document
through localStorage and a `?next=` parameter. The Android app stays on Compose; only the
web moves.

## Decisions

- **Scope: full parity.** Every screen the wasm web client has today, with the same
  behaviour. The wasm target is removed only once React has caught up.
- **One SPA.** `src/main/frontend` holds the landing page, the auth pages and the app.
  The app lives at `/app/*` as ordinary routes.
- **Delivery: one switch.** All work happens on a feature branch that is merged when parity
  is reached. The same merge removes the wasm target. Production never runs both clients.
- **Web-only code in `client/commonMain` stays.** The banks, CSV import and HomeMoney
  screens, view models, repositories and DTOs become unused on Android once the wasm target
  is gone. They are left in place for a later cleanup and are out of scope here.

## Stack

| Concern | Choice |
| --- | --- |
| Build | Vite (replaces Create React App), TypeScript 5 |
| UI | React 19 |
| Routing | react-router 7, path routing (`/app/accounts`, not `/app/#accounts`) |
| Server state | TanStack Query |
| Styling | Tailwind v3 with the existing CSS tokens in `index.css` |
| Charts | Recharts |
| Tests | Vitest + Testing Library |

Tailwind stays on v3 to keep the move to Vite a tooling change only. The Compose theme was
built from the same CSS tokens (`client/.../ui/theme/Color.kt`), so the app keeps its look,
including the light-theme contrast values that Compose adjusted: those move into
`index.css` as the light theme.

## Layout

```
src/main/frontend/src/
  landing/        Landing, Login, Register, ForgotPassword, ResetPassword, VerifyEmail, AuthShell
  app/
    api/          fetch client, DTO types, TanStack Query hooks per resource
    lib/          money, minor units, calculator, expression input, FX conversion and pegs,
                  dates, subscription scheduling, API error mapping
    features/     dashboard, transactions, quickadd, accounts, categories, currencies,
                  subscriptions, analytics, banks, homemoney, premium, settings,
                  onboarding
    components/   AmountField, CurrencySelect, ConfirmDialog, account picker, EmptyState,
                  MoneyText, SelectorChip, IconCircle, page shell and navigation
  i18n/           one dictionary for landing and app, en / ru / uk
```

`app/lib` is plain TypeScript with no React imports, so every rule in it can be tested
without rendering anything.

## Routes

The app's routes mirror `client/.../navigation/Routes.kt`, with query arguments kept as
query parameters:

`/app` (dashboard), `/app/quickadd?type=&categoryId=`, `/app/transactions`,
`/app/transactions/:id`, `/app/accounts`, `/app/accounts/edit?accountId=`,
`/app/categories`, `/app/categories/edit?categoryId=&type=`, `/app/subscriptions`,
`/app/subscriptions/edit?subscriptionId=`, `/app/currencies`, `/app/currencies/edit?code=`,
`/app/analytics`, `/app/banks`, `/app/import/homemoney`, `/app/premium`, `/app/settings`,
`/app/onboarding`.

There is no `/app/more`: the wasm client does not register the More tab in a browser. The
header and its drawer do that job, as they do now. The header shows its sections inline
at 1240px and wider, and in a drawer below that. The wordmark appears at 480px and wider.
Below 900px the header has a "+" button that opens quick add. At 900px and wider, the
dashboard and transactions pages carry the entry panel inline instead.

**HomeMoney redirect.** The server's callback sends the browser to
`/app/?homemoney=pending&code=…`, or `?homemoney=denied`. On load, the app reads those
parameters once and removes them from the address bar with `replaceState`, so the code
cannot be spent twice. It then opens `/app/import/homemoney`, which finishes the exchange.
The server is not changed.

**Stored preferences.** The landing page and the wasm client both use the localStorage key
`theme`, in different formats: `dark`/`light` and `SYSTEM`/`LIGHT`/`DARK`. The merged app
stores `system`, `light` or `dark`, and on read it also accepts the old uppercase values.
The other keys stay as the wasm client wrote them, so nobody has to redo onboarding or pick
their default account again: `onboarded`, `default_account`, `base_currency`, `lang` and
`homemoney_auth_state`.

The Compose account picker was its own destination only so it could return a result to
the previous screen. In React it becomes a dialog.

**Old bookmarks.** On load, if `/app/` carries a hash that `Routes.isKnown` would have
accepted (`#accounts`, `#transaction/edit/12`), it is rewritten with `replace` to the
matching path. An unknown hash is dropped and the dashboard shown, as the wasm client does
today.

## Authentication

- Tokens stay in localStorage under the existing keys `pm_access_token`,
  `pm_refresh_token` and `pm_email`, so everyone who is signed in stays signed in across
  the switch.
- Signing in stores the session and calls `navigate(next)` inside the router. The
  full-page handover through `location.replace` goes away. `next` is now a path under
  `/app` and is checked against the known routes before use, so the redirect can never
  leave the app.
- A route guard on `/app/*` sends a browser with no session to `/login?next=<path>`.
- The fetch client adds the bearer header to every request except `auth/login`,
  `auth/register` and `auth/refresh`. On a 401 it refreshes **once**: the first 401 starts
  a refresh promise, every other failing request awaits the same promise, and each retries
  once with the new token. If the refresh fails, the session is cleared and the user is
  sent to sign-in with the current path as `next`. This mirrors the Ktor `Auth` plugin in
  `HttpClients.kt`.

## Porting logic

Everything that `commonMain` computes on the client is rewritten as a module in
`app/lib`. The existing Kotlin tests are ported case by case to Vitest, so the TypeScript
has to pass the same cases:

| Kotlin source | Tests ported |
| --- | --- |
| `core/calc/Calculator.kt`, `ExpressionInput.kt` | `CalculatorTest`, `ExpressionInputTest` |
| `core/domain/Money.kt`, `MinorUnits.kt`, `MoneyFormatter` | `MoneyTest` |
| `core/currency/CurrencyConverter.kt`, `FxPegs.kt` | `FxSnapshotTest` |
| `core/network/ApiErrors.kt` | `ApiErrorsTest` |
| `navigation/Routes.kt` (`isKnown`) | `RoutesTest` |
| `feature/subscriptions/SubscriptionScheduler.kt` | new tests: month ends, leap years |
| transaction date-range filter (fix `6d9a525`) | new tests: day boundaries across time zones |

Money is formatted with `Intl.NumberFormat`. The API sends every amount as a decimal
string. The client parses each one into a `Decimal` from decimal.js and does all arithmetic
on it, the way the Kotlin code uses `BigDecimal`. An amount never passes through a JS
`number` until the moment it is formatted for display. Rounding is half away from zero,
to the currency's decimals. The `MoneyTest` cases about clamping to `Long` do not apply,
because `Decimal` does not overflow, so only its rounding cases are ported.

## Strings

A one-off script converts `client/composeApp/src/commonMain/composeResources/values*/strings.xml`
(en, ru, uk) into TypeScript locale modules and merges them with the landing page's
dictionaries in `src/i18n/locales`:

- Android placeholders `%1$s` and `%1$d` become `{0}`.
- Plurals become the i18n module's plural form.
- Escaped apostrophes and quotes are unescaped.

The script is committed under `scripts/` so the conversion can be read and repeated. It
is not part of the build. After the switch, the TypeScript files are the source of truth
for web strings.

## Screens

Each screen keeps the behaviour of its Compose counterpart and its view model. That
includes empty states, confirmations before deleting, validation messages and what is
refreshed after a write. After a write, the related TanStack Query keys are invalidated:
a new transaction, for example, refreshes accounts, the dashboard and analytics.

These stay absent on the web, as they are today:

- receipt scanning (`receiptScanAvailable = false`);
- ads;
- Play billing. The premium screen shows the account's entitlement and says where to buy.

## Server

- `SpaFallbackController`: drop the `/app` mapping that forwards to the wasm shell.
- `WebConfig`: keep a resource handler on `/app/**`, but make it always resolve to the
  React shell `classpath:/static/index.html`. It stays a resource handler for the reason
  its comment gives: PathPatternParser cannot express a multi-segment no-dot fallback.
  Drop the `.wasm` cache rule and serve Vite's content-hashed `/assets/**` with a long
  `Cache-Control`.
- `pom.xml`: Vite is configured to emit to `build/`, so `copy-resources` stays as it is.
  The `CI` and `GENERATE_SOURCEMAP` variables were for CRA and are removed.

## Removing the wasm client

All on the same branch, before the merge:

- `client/composeApp/build.gradle.kts`: remove the `wasmJs` target and
  `wasmJsMain.dependencies`.
- Delete `client/composeApp/src/wasmJsMain`. The expect/actual pairs keep their Android
  actuals, so `commonMain` compiles unchanged.
- `Dockerfile`: remove `COPY web-dist/`. Delete `web-dist/` and its `.gitignore` rules.
- `.github/workflows/deploy.yml`: the `client` job keeps only `testDebugUnitTest`, with no
  bundle and no artifact. `build-and-push` no longer depends on it or downloads anything.
  A new `frontend` job runs `npm ci`, `tsc --noEmit` and `vitest run`, and
  `build-and-push` needs it.
- Update the root README and `client/`'s README to say the web client is React. In
  `client/CLAUDE.md`, remove the wasm build-and-stage instructions and the dev loop.

## Verification before merge

- `npm run typecheck`, `npm test` and `npm run build` pass. `./mvnw package` produces a jar
  that serves both `/` and `/app/*`.
- `client`: `./gradlew assembleDebug testDebugUnitTest` still passes without the wasm target.
- Manual run against the local backend (profile `local`, H2), in a browser, covering:
  - sign-in, sign-out, and an expired access token being refreshed without the user
    noticing;
  - an old `/app/#accounts` bookmark;
  - create, edit and delete for accounts, categories, transactions, transfers,
    subscriptions and currencies;
  - quick add with an expression;
  - CSV import preview and confirm;
  - the HomeMoney connect flow up to the external redirect;
  - dashboard and analytics numbers checked against the wasm client on the same data;
  - language and theme switching;
  - a 375px-wide viewport.

## Work order on the branch

1. Move the landing page from CRA to Vite and TypeScript 5, with no behaviour change.
2. App skeleton: fetch client with refresh, auth guard, layout, navigation, route table,
   hash-bookmark rewrite.
3. `app/lib` with the ported tests.
4. String conversion script and the merged dictionaries.
5. Screens: settings, currencies, categories, accounts, transactions and transfers,
   quick add, dashboard, subscriptions, analytics, premium, onboarding.
6. Imports: banks and CSV, HomeMoney.
7. Server changes, wasm removal, CI, README.
