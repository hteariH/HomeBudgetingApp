# Google Play Data Safety form — PersonalMoney

The answers below map the Play Console **App content → Data safety** questionnaire to what
PersonalMoney actually does. Copy them across as-is.

> Last verified against the codebase: 2026-09-05.
>
> **This file was rewritten when the app stopped being offline-only.** PersonalMoney now
> has accounts, server-side sync, bank connections and Play Billing. An answer set written
> for the old offline app would be a false declaration, which Play treats as a policy
> violation rather than a form error.

---

## Section 1 — Data collection and security

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** — HTTPS everywhere (our API, AdMob, Frankfurter, Google Play) |
| Do you provide a way for users to request that their data be deleted? | **Yes** — in-app account deletion, and by email |
| Account deletion URL (required, must work without installing the app) | `https://personalmoney.app/app/` — sign in, **Settings → Delete account** |

Deleting the account erases the user row and every account, category, transaction,
transfer, subscription and bank credential belonging to it (`DELETE /api/users/me`).

---

## Section 2 — Data types

Everything below is **collected**. Nothing is **shared** with third parties for their own
purposes; the AdMob rows are collection by an SDK embedded in the app, which Play still
requires us to declare.

### Personal info

| Data type | Collected | Shared | Required? | Purposes |
| --- | --- | --- | --- | --- |
| Name | ✅ | ❌ | Required | App functionality, Account management |
| Email address | ✅ | ❌ | Required | App functionality, Account management |
| User IDs | ✅ | ❌ | Required | App functionality, Account management |

(Registration takes first name, last name and email. Each account also carries a UUID, which
is the identifier a Play purchase is filed under.)

### Financial info

| Data type | Collected | Shared | Required? | Purposes |
| --- | --- | --- | --- | --- |
| Purchase history | ✅ | ❌ | Required | App functionality (knowing whether Premium is active) |
| Other financial info | ✅ | ❌ | Required | App functionality |

**"Other financial info" covers the point of the app**: account balances, transaction
amounts, currencies, dates, notes and categories are synced to our server. It also covers
bank connection credentials (a Monobank personal token, a HomeMoney OAuth token), which are
stored encrypted on the server and never sent back to a device.

**User payment info** stays **unchecked**: Play Billing processes the payment and we never
receive card details.

> The old version of this file argued that financial data was on-device only and left this
> whole section blank. That stopped being true when sync shipped.

### App activity

| Data type | Collected | Shared | Required? | Purposes |
| --- | --- | --- | --- | --- |
| App interactions | ✅ | ❌ | Optional | Advertising or marketing |

(Ad impressions and clicks, collected by AdMob.)

### Device or other IDs

| Data type | Collected | Shared | Required? | Purposes |
| --- | --- | --- | --- | --- |
| Device or other IDs | ✅ | ❌ | Optional | Advertising or marketing |

(The Android Advertising ID, collected by AdMob. The manifest declares
`com.google.android.gms.permission.AD_ID`.)

### Location

| Data type | Collected | Shared | Required? | Purposes |
| --- | --- | --- | --- | --- |
| Approximate location | ✅ | ❌ | Optional | Advertising or marketing |

(IP-based, by AdMob. The app requests no location permission and calls no location API.)

### Everything else — leave unchecked

Precise location · Photos and videos · Audio · Files and docs · Calendar · Contacts ·
Messages · Health and fitness · Web browsing history · Installed apps · Crash logs ·
Diagnostics.

**Receipt scanning does not change this.** The photo is taken by the system camera through
`ACTION_IMAGE_CAPTURE`, read on-device by Tesseract with the language data bundled in the
app, and deleted from the cache immediately. Nothing about the image leaves the device, so
Play's definition of "collected" is not met and **Photos and videos stays unchecked**.

**Crash logs stay unchecked** because there is no Crashlytics or equivalent in the build.
Add the row the day one is added.

---

## Section 3 — Security practices

- Data is encrypted in transit: **Yes**.
- Users can request that data be deleted: **Yes** (in-app, and by email).
- Passwords are stored as bcrypt hashes; bank credentials are encrypted at rest; session
  tokens live in Android `EncryptedSharedPreferences` on the device.
- Independent security review: **No**.
- Committed to the Play Families Policy: **No** — the app is not directed at children.

---

## Section 4 — Related App content forms

| Form | Answer |
| --- | --- |
| Privacy policy | `https://personalmoney.app/privacy.html` |
| Ads | **Yes, the app contains ads** |
| Target audience | **18+** (a money-management app with ads and a paid subscription) |
| Content rating questionnaire | Category: *Finance / Utility*. No violence, no sexual content, no gambling, no user-generated content, no user-to-user communication. Declare that the app shares the user's location with an advertising SDK when asked about location sharing. |
| Government apps | No |
| Financial features | The app is a personal-finance tracker that can read bank statements the user authorises. It does **not** lend, invest, trade, insure, or move money. Answer the declaration accordingly and do not claim regulated services. |
| Data safety | This document |

---

## Section 5 — Re-do this file when

- a new SDK lands (analytics, crash reporting, attribution),
- a new data type starts crossing the network,
- a new bank or aggregator provider is added,
- receipt images ever start being uploaded or retained,
- the app starts collecting anything for a purpose not listed above.
