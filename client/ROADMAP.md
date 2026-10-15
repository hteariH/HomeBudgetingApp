# PersonalMoney — Improvement Roadmap

_Generated 2026-06-11 from a full multi-agent audit of the codebase (11 domain audits + 4 product-strategy lenses → 115 code-grounded findings + 59 feature ideas, deduplicated and adversarially verified against the source)._

## How to read this
- **Impact**: user value / trust / growth leverage — `high` / `medium` / `low`
- **Effort**: rough size for a solo dev — `S` (hours) / `M` (a day or few) / `L` (multi-day)
- **Evidence**: `file:line` or concrete observation backing the item
- Findings were reality-checked against the code; corrections from that pass are noted inline.

> **Historical.** This audit predates the move to a self-hosted, open-source app. There are no
> ads, no purchases and no Play Store listing any more, and the Play-era documents it cites
> (`PRIVACY_POLICY.md`, `STORE_LISTING.md`) are gone: the privacy page is `site/public/privacy.html`
> and releases follow `docs/RELEASE_CHECKLIST.md`. Rows about ads, consent or the store are
> marked obsolete below; `file:line` evidence elsewhere may have drifted.

---

## ✅ Status (updated 2026-06-11)

**Top 10 — all done.** Each shipped with regression tests + a green build; commits on `master`:
1. Privacy policy URL + filled template — `0fab8bb`
2. Money-correctness trio (FX ÷0, overflow clamp, date-converter crash) — `ba02e4b`
3. Category budgets (DB migration v1→v2, edit UI, dashboard progress) — `e30a459`
4. Safe backup import (atomic replace + preview + confirm) — `facf724`
5. Category/Subscription delete confirmations + transactionId crash fix — `7e8db2d`
6. Net worth trend sparkline — `32c6fbb`
7. Biometric failure feedback on lock screen — `e34b661`
8. Expanded transaction search + Clear filters — `9c05ffd`
9. Localized backup/transfer/widget strings — `0641bbe`
10. Quick-repeat (duplicate transaction) — `05e0bfd`

Also fixed a pre-existing red unit-test baseline (`b73d7d0`).

**Post-Top-10 roadmap progress (continuing):**
- Theme 5 — subscription save errors surfaced; charge dates formatted (`2eaf2d1`)
- Theme 6 — analytics month names localized; transactions "All" string (`7f17e7a`)
- Theme 1 — category usageCount counts subscriptions; precise recurring factors (`4faa639`)
- Theme 1 — account cascade-delete warning + clear stale default account (`549f2d0`)
- Theme 9 — accessibility labels for numpad symbol keys + transfer swap (`6003016`)
- Theme 3 — spending insights card (MoM change, savings rate) (`158ef98`)

Verified-as-already-done or false positives (skipped): currency-deletion guard
(CurrencyEditViewModel already checks usageCount), language-tag NPE (null-safe),
TransactionEdit scientific-notation (toPlainString never uses exponents),
archived-category "leak" (maps are needed to label history).

Test count now ~140 unit + 1 instrumented. Every change gated on testDebugUnitTest
+ assembleDebug.

---

## ⭐ Top 10 — best next moves (ranked)

| # | Item | Impact | Effort | Why |
|---|------|--------|--------|-----|
| 1 | Replace placeholder privacy URL + fill privacy policy template | high | S | Done; the policy now lives at `site/public/privacy.html` (the Play-era `PRIVACY_POLICY.md` is gone). |
| 2 | Fix the silent-money correctness trio: FX ÷0, `Money` overflow, date-converter crash | high | S | Three tiny, code-confirmed fixes that prevent corrupted totals, flipped-sign amounts, and read-time crashes — the core trust of a finance app. |
| 3 | Category budgets with progress + overspend alerts | high | M | The single most-requested missing feature (all 4 product lenses ranked it #1 vs every competitor). Biggest growth lever; builds on existing category/monthly queries. |
| 4 | Make backup import safe (clear-or-merge, non-ABORT, confirm/preview) | high | M | Re-importing today fails cryptically or silently duplicates everything (`BackupManager.kt:46-53`). Backup/restore is the only safety net for a local-first app. |
| 5 | Delete confirmations for Category & Subscription (+ NPE/crash fixes) | high | S | Quick wins reusing the existing `ConfirmDialog`; plus the language-tag NPE (`SettingsScreen.kt:199`) and `transactionId` crash (`TransactionEditViewModel.kt:75`) are one-line guards. |
| 6 | Net worth card with trend | high | M | Primary dashboard KPI flagged by 4 lenses; FX engine + balance queries already exist — mostly aggregation + a snapshot table for the trend. |
| 7 | Biometric-failure recovery path | high | M | A failed/cancelled prompt currently locks the user out with no retry (`MainActivity.kt:86`) — severe trust/abandonment risk. |
| 8 | Expand search to category/account/amount + "Clear all" filters | high | M | Note-only search (`TransactionsViewModel.kt:71`) misses the most common queries; `clearFilters()` already exists with no UI. |
| 9 | Localize hardcoded strings (backup, transfer errors, widget) | high | M | App advertises ru/uk but shows English in backup results, transfer errors, and the widget; resources mostly exist. |
| 10 | Recurring transaction templates / quick-repeat | high | S | Cited by 5 lenses; largely an S-effort "duplicate this recent transaction" affordance that cuts daily entry friction. |

---

## Theme 1 — Correctness & data integrity (money, DB, FX)
_Ranked first: in a finance app, no feature matters if the numbers are wrong. Most are code-confirmed; several are tiny fixes with outsized payoff._

| Item | Impact | Effort | Evidence |
|------|--------|--------|----------|
| **Guard FX rate inversion against ÷0** — `1.0 / rate` with no zero check yields `Infinity`, poisoning every conversion. Return null on `rate <= 0` and reject non-positive rates at ingestion. | high | S | `CurrencyConverter.kt:19` / `FxSnapshot.kt:19-21` |
| **Prevent silent `Long` overflow in `Money.times()`** — `BigDecimal(...).toLong()` truncates past `Long` range, flipping large amounts negative. Use `longValueExact()` in `runCatching`, or clamp. _(Real, but only triggers near `Long.MAX`.)_ | high | S | `Money.kt:43-45` (also `unaryMinus` at `:41`) |
| **Handle malformed dates in Room converter** — `stringToLocalDate` uses bare `LocalDate.parse(it)` with no guard (unlike the enum converters), so a corrupt stored date crashes on read. Wrap in `runCatching`. _✅ Confirmed real: line 16 has no `runCatching`; only lines 19-32 do._ | high | S | `Converters.kt:16` |
| **Make backup import safe** — `insert()` with `ABORT` and no pre-clear means re-import fails or duplicates. Add explicit replace-vs-merge, validate referential integrity, return counts, keep atomic in `withTransaction`. | high | M | `BackupManager.kt:46-53` |
| **Block currency deletion while referenced; validate codes on save** — no FK enforcement; orphaned codes silently fall back to 2 decimals (wrong for 8-decimal currencies). Enforce `usageCount > 0` at delete; validate on account save. | high | M | `CurrencyDao.kt:34-35`; `CurrencyRegistry.decimalsOrFallback` |
| **FK / cleanup for `transferCounterpartAccountId`** — no `ForeignKey`, so deleting an account orphans the other half of a transfer pair. Add `SET_NULL` or repair on delete. | high | M | `TransactionEntity.kt` (counterpart field) |
| **Warn before cascade-deleting an account's transactions** — `delete()` triggers FK `CASCADE`, destroying history with no confirmation. Show a count; offer reassign/archive. | high | M | `AccountEditViewModel.kt:156-162`; CASCADE on `account_id` |
| **Exclude archived categories/accounts from reporting** — dashboard + transactions observe `observeAll()` (includes archived), resurfacing them in reports. Switch dashboard to `observeActive()`; flag/filter in lists. | high | S | `DashboardViewModel.kt:96`; `TransactionsViewModel.kt:67-68` |
| **Record actual FX rate used in multi-currency transfers** — `fxRateToAccount` hardcoded to `1.0`, discarding the implied rate → unauditable. Compute & store it; wire the dead `rate`/`setRate()` state into a display + stale-rate indicator. | high | M | `TransferViewModel.kt:110,125`; dead state at `:32,72` |
| **Enum/converter fallbacks shouldn't silently drop data** — unknown stored value → `null`, breaking type-keyed balance math with no log. Provide explicit defaults / surfaced error. | medium | M | `Converters.kt:19-32` |
| **Clear stale `defaultAccountId` / widget IDs on delete** — deleting the default account or a selected category leaves dangling prefs/widget refs. Clean up at delete time. | medium | M | `AccountEditViewModel` delete; `QuickExpenseWidget.kt` `KEY_CAT_ID` |
| **Count subscription refs in category `usageCount`** — only transactions are counted, so deleting a "used" category can orphan subscription refs. | medium | S | `CategoryRepository.kt:25` |
| **Correct weekly→monthly factor** — `4.345` should be `~4.3482` (365.25/7/12); 0.07% error compounds yearly. | low | S | `SubscriptionsViewModel.kt:52` |
| **Collapse redundant TRANSFER balance branches** — both CASE branches return the same value; correct but invites breakage. Comment the signing convention. | low | S | `AccountDao.kt:57-58` |

---

## Theme 2 — Budgeting, goals & net worth (core feature gaps)
_Every competitor lens independently flagged the same pillars. Biggest growth lever; mostly M-effort on top of existing FX/analytics infra, no backend._

| Item | Impact | Effort | Notes |
|------|--------|--------|-------|
| **Category budgets** — optional monthly limit per category, progress bars on Dashboard/Analytics, near/over-limit warnings (optional WorkManager notification). | high | M | #1 gap named by 4 lenses |
| **Net worth snapshot with trend** — Dashboard card summing all balances in base currency + per-account breakdown + monthly trend sparkline. | high | M | FX + `observeBalances()` exist; needs snapshot table for trend |
| **Savings goals** — named targets (amount/date), linked to accounts or aggregated, progress bars on Dashboard. | high | M | Retention/engagement driver |
| **Transaction tags** — optional multi-tags (`#business`, `#travel`), filterable/searchable. Pairs with the search-expansion fix. | high | M | Needs tag column or join table |
| **Account types / debt tracking** — add `account_type`/`is_debt` so debts subtract from net worth and accounts group by type. Prerequisite for a truthful net-worth number. | medium | S | Complements net worth |
| **Budget rollover / carryforward** _(later)_ — roll unused budget into next month. Defer until budgets ship. | low | M | Power-user refinement |

---

## Theme 3 — Reporting & insights
_Once budgets exist, comparisons/trends/insight cards create the "aha" moments. Reuses the Analytics screen + monthly chart._

| Item | Impact | Effort |
|------|--------|--------|
| **Spending insights card** — "You spent 12% more on Food than last month", top-category share, biggest single expense, savings rate %. Pure local compute. | high | M |
| **Monthly / YoY comparison view** — current vs previous month (and year-ago), per-category % change + trend arrows. | medium | M |
| **Spending trends & month-end forecast** — rolling 3-month velocity per category + prorated "on pace for $X" with over-budget warning. | medium | M |
| **Per-account / per-currency cash-flow report** — inflows vs outflows by account/currency; surfaces which accounts drain fastest. | medium | M |
| **Recurring-expense projection (next 30/90 days)** — sum active subscriptions into a projected-outflow widget. Reuses subscription data. | medium | S |
| **Payee / merchant history** — explicit payee field (or note extraction); spend-per-merchant view; spot duplicate subs. | medium | M |

---

## Theme 4 — Faster & smarter entry
_Entry friction determines whether users keep logging. Mostly small additions; several also prevent data errors._

| Item | Impact | Effort | Evidence |
|------|--------|--------|----------|
| **Recurring templates / quick-repeat** — one-tap duplicate of a recent row, or saved templates pre-filling QuickAdd (for frequent-but-irregular costs). | high | S | 5 lenses |
| **Expand search to category/account/amount + amount-range; wire "Clear all"** — search matches note text only today; `clearFilters()` exists with no UI. | high | M | `TransactionsViewModel.kt:70-71,88` |
| **Duplicate-transaction detection** — on save, warn if a similar amount/category/account occurred within ~24h. Also mitigates widget double-tap. | medium | S | 2 lenses |
| **Category suggestions from note keywords** — rule-based ("uber"→Transport), top-3 suggestions in QuickAdd. No ML infra. | medium | M | 2 lenses |
| **Persist transaction list filter state** — `showSearch`/`activePeriod` are local Compose state, reset on back-nav. Hoist to ViewModel/SavedState. | medium | M | `TransactionsScreen.kt:69-70` |
| **Clear expression on type switch (or confirm)** — `setType()` clears category but not expression, so an Income amount can carry into Expense. | medium | S | `QuickAddViewModel.kt:231-234`; `TransactionEditViewModel.kt:230-233` |
| **Receipt photo attachment (local)** — optional local image URI via SAF, thumbnail in detail. No cloud. | medium | M | 3 lenses |
| ~~**Receipt scan (OCR)**~~ — *shipped.* Camera → on-device Tesseract (`ukr+rus+eng`) → `core/ocr/ReceiptParser` fills amount, date and note in QuickAdd. Android only. | — | — | — |
| **Split transactions across categories** — one transaction → multiple category lines; improves analytics accuracy. | medium | M | 3 lenses |

---

## Theme 5 — Robust edit & subscription flows (bugs + missing UI)
_Several silent-failure bugs and unfinished UI that already has schema/backup support._

| Item | Impact | Effort | Evidence |
|------|--------|--------|----------|
| **Fix scientific-notation in edit amount load** — `stripTrailingZeros().toPlainString()` can emit `1E-3`, which `Calculator` can't parse → broken edit. Normalize to plain decimal at fixed scale. | high | S | `TransactionEditViewModel.kt:169` |
| **Defensive `transactionId` parsing** — `checkNotNull(...toLongOrNull())` crashes on malformed/missing arg (QuickAdd degrades gracefully). | high | S | `TransactionEditViewModel.kt:75` |
| **Surface unavailable/deleted account or category in edit** — inactive account silently falls back to first active (wrong-account saves) with DONE enabled; deleted category disables DONE with no reason. Warn + require re-selection. | high | M | `TransactionEditViewModel.kt:108,123,140,161` |
| **Display subscription validation errors** — `save()` sets an error state the screen never renders, so saves fail silently. | high | S | `SubscriptionEditViewModel.kt:122`; screen has no error UI |
| **Reinterpret subscription amount on account-currency change** — changing account updates currency but not the amount, so it reparses under the new currency (USD→JPY decimals) silently wrong. | high | M | `SubscriptionEditViewModel.kt:104-106,118` |
| **Delete confirmations (Category + Subscription)** — both delete on a single tap; reuse `ConfirmDialog`. Add a `SnackbarHost` to CategoryEditScreen for delete errors. | high | S | `CategoryEditScreen.kt:73-75`; `SubscriptionEditScreen.kt:69-71`; pattern at `AccountEditScreen.kt:184-193` |
| **Expose existing subscription fields: interval & end date** — stored + backed up but no UI (`setInterval` never called). | medium | M | `SubscriptionEntity.kt:37,40` |
| **Localize & format subscription dates** — next-charge renders raw ISO `toString()` instead of the shared `d MMM yyyy` formatter. | medium | M | `SubscriptionsScreen.kt:157`; `SubscriptionEditScreen.kt:118` |
| **Warn on subscriptions excluded from totals** — rows with no FX rate are silently `continue`d, so totals look low. | medium | M | `SubscriptionsViewModel.kt:57` |
| **Expose account `note` field; friendlier currency labels** — `note` stored/serialized but no UI; pickers show bare codes. Show "USD — United States Dollar". | medium | M | `AccountEntity.kt:22`; `AccountEditScreen.kt:224-229` |

---

## Theme 6 — Localization (i18n)
_Ships ru/en/uk but several user-facing strings bypass resources. Each fix is small and independently shippable._

| Item | Impact | Effort | Evidence |
|------|--------|--------|----------|
| **Localize hardcoded strings** — backup result messages, transfer validation errors, widget labels ("Expense"/"Income"/"Saved!"). | high | M | `SettingsViewModel.kt:77-78,85-86`; `TransferViewModel.kt:86,90`; `QuickExpenseWidget.kt:109,116,193` |
| **Localize analytics month names** — hardcoded English `MONTH_NAMES`, so charts always show Jan/Feb. | medium | S | `AnalyticsViewModel.kt:46-49,92` |
| **Transaction-specific "All" filter string** — reuses `R.string.currencies_all` (semantically wrong). | low | S | `TransactionsScreen.kt:173` |

---

## Theme 7 — Release & monetization readiness
_Written for a Play Store launch with ads. Obsolete except the versioning and About rows: the app
is now distributed as an APK on GitHub Releases, with no ads and no store listing._

| Item | Impact | Effort | Evidence |
|------|--------|--------|----------|
| ~~**Replace placeholder privacy URL + fill policy template**~~ — obsolete: the privacy page is `site/public/privacy.html`. | — | — | — |
| ~~**Fix UMP consent race**~~ _(obsolete: ads removed)_ — both success and error callbacks call `onComplete()`, so on failure/timeout ads can init before consent (EEA/UK risk). Gate on `canRequestAds`; handle error path. _✅ Confirmed: error path at `:30` calls `onComplete()`._ | high | M | `AdsConsentManager.kt:25-31` |
| ~~**Use the preloaded interstitial or remove it**~~ _(obsolete: ads removed)_ — `preload()` runs at startup but `showIfReady()` is never called → wasted bandwidth/battery, lost revenue. | medium | S | `MainActivity.kt:42` |
| **Use `BuildConfig.VERSION_NAME`; reserve a versionCode range** — Settings hardcodes "0.1.0"; `versionCode = 1` leaves no buffer. | medium | S | `SettingsScreen.kt:265`; `build.gradle.kts:40` |
| ~~**Add "Manage ad consent" in Settings**~~ _(obsolete: ads removed)_ — `reset()` exists but is never exposed; policy references an in-app option that doesn't exist. | medium | M | `AdsConsentManager.kt:34-36` |
| ~~**Fix BannerAd lifecycle**~~ _(obsolete: ads removed)_ — `AdView` created in `remember{}`, never destroyed, reparented across recompositions → leak. Add `DisposableEffect { destroy() }`. | medium | M | `BannerAd.kt:26-45` |
| ~~**Prepare store assets**~~ _(obsolete: no store listing)_ — production icon (512²), feature graphic (1024×500), phone screenshots. | medium | M | — |
| **Implement or remove empty About handler** — `onClick = { }` + hardcoded version. | medium | S | `SettingsScreen.kt:264-266` |

---

## Theme 8 — Auth, settings & error-handling correctness
_User-facing bugs in security/settings; a couple can hard-lock the user out._

| Item | Impact | Effort | Evidence |
|------|--------|--------|----------|
| **Biometric-failure recovery path** — `onFailure` does nothing → app stays locked with no retry/fallback, requiring force-close. | high | M | `MainActivity.kt:79-87`; `BiometricGate.kt:60` |
| **Fix NPE in language-tag equality** — `currentLangTag.equals(...)` on a value explicitly set null by "Follow system" crashes on re-selection. | high | S | `SettingsScreen.kt:180,192,199` |
| **Persist language selection** — chips call `LocaleManager.apply()` but never `preferences.setLanguage()` (inconsistent with theme/currency). | medium | S | `SettingsScreen.kt:191,201`; `UserPreferences.setLanguage()` unused |
| **Re-fire snackbar on repeated identical errors** — `LaunchedEffect(state.error)` won't retrigger on the same text. Use a one-shot event/Channel. | medium | S | `AccountEditScreen.kt:75-79`; same in QuickAdd |
| **Confirm/preview before destructive import; friendlier errors** — import launches immediately; failures show raw `UNIQUE constraint` text. (Pairs with import-safety fix.) | medium | M | `SettingsScreen.kt:248-250` |
| **Remove redundant toggle hit target** — both Row and inner Switch toggle, causing double-toggles + confusing a11y. | low | S | `SettingsScreen.kt:293,312-316` |

---

## Theme 9 — Accessibility & polish
_Broad, consistent gap best done as one sweep: interactive icons/chips/switches/rows lack `contentDescription`; some touch targets/font sizes below Material 3 guidance._

| Item | Impact | Effort |
|------|--------|--------|
| **Add content descriptions** across Dashboard actions/nav tiles, chart `IconCircle`, transaction rows (edit/delete), transfer swap, subscription Switch, Settings chips/Switch, widget buttons. Numpad operators should announce "multiply/divide/minus", not raw symbols; BACKSPACE/DONE need labels. | high | M |
| **Meet minimum touch targets + support font scaling** — ~20dp transaction rows, 14dp currency star, fixed 10sp chart labels. | medium | M |
| **Reconcile dynamic color with finance semantic colors** — Material You can clash with Expense/Income/Transfer colors on some wallpapers. Verify contrast or anchor semantic colors. | medium | M |
| **Clear empty-state & truncation affordances in charts** — top-6 (dashboard)/top-4 (widget) with no "more" hint; empty charts animate to zero bars instead of "No data". | low | M |
| **Extract shared color palette; unify error-handling** — the 14-color picker is duplicated across Account/Category edit; ViewModels mix `runCatching` vs ad-hoc handling. | low | S |

---

## Theme 10 — Widget hardening & secondary features
_Reliability/feedback gaps in the Glance widget, plus lower-priority features that round out competitiveness._

| Item | Impact | Effort |
|------|--------|--------|
| **Prevent widget double-save; remove blocking 2s delay** — no re-tap guard + unconditional `delay(2000)` risks duplicates and jank. Disable during save; use state-driven update. | medium | M |
| **Widget feedback on rejected/invalid saves; validate stale IDs** — zero/invalid amounts silently ignored; deleted category/account can show as selected. | medium | S |
| **CSV export with filters** — export filtered transactions to CSV via SAF for spreadsheet/tax workflows. | medium | S |
| **Balance / spending home-screen widget** — second widget showing per-account balances + daily/weekly spend. | medium | M |
| **PDF report export** — category/monthly/account report via SAF. (CSV first.) | medium | L |
| **Optional encrypted cloud/peer backup** — opt-in encrypted Drive/Dropbox or Syncthing-style sync, preserving local-first. Deliberate later bet. | medium | L |
| **Batch edit/delete + calendar view** — multi-select bulk actions; calendar layout. Defer until core gaps close. | low | M |

---

## Theme 11 — Test coverage
_~8% by file count (ViewModel unit tests only). As correctness fixes and features land, regression risk rises._

| Item | Impact | Effort |
|------|--------|--------|
| **Unit tests for high-risk paths** — `Money` overflow/rounding, `FxSnapshot` inversion & zero handling, `Converters` malformed input, balance math with transfers/deleted accounts, backup round-trip/import conflicts. Directly guards Theme 1. | high | M |
| **Compose/navigation tests for critical flows** — QuickAdd, edit save/cancel, nav-arg edge cases (the `transactionId` crash). Larger; do incrementally. | medium | L |

---

## Bonus — high-value ideas the first pass under-weighted
_(surfaced by the adversarial verification step)_

1. **Amount-range search with sorting/aggregation** — e.g. "all transactions > $100 last month". Low-effort given the existing filter infra; critical for spotting unusual activity.
2. **Category archive UI + smart cascade-delete guidance** — `category.archived` already exists in the model (`CategoryEntity`, `CategoryEditViewModel:67`) but has **no UI**, forcing hard deletes that break transactions. An archive toggle + "this category has N transactions" warning is high-value, low-effort data-loss prevention.
3. **Multi-currency dashboard breakdown** — the app supports 80+ currencies + FX but the dashboard only shows base-currency totals. A "balance by currency" (FX-applied) card closes a blind spot for multi-currency users and justifies the FX investment.

---

## Suggested sequencing
1. **Ship-blockers & trust (week 1, mostly S):** Top-10 items #1, #2, #5 + the versioning and About fixes (Theme 7) and language NPE (Theme 8).
2. **Backup safety (Theme 1):** import safety + confirm/preview — the safety net must be reliable before users accumulate data.
3. **First headline feature:** Category budgets (#3), then Net worth card (#6) — the two biggest growth levers, both reusing existing infra.
4. **Daily-use polish:** search expansion (#8), quick-repeat (#10), localization sweep (#9).
5. **Then:** reporting/insights (Theme 3), accessibility sweep (Theme 9), and targeted tests (Theme 11) alongside each feature.
