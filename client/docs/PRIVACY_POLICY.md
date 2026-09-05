# Privacy Policy — PersonalMoney

_Last updated: 2026-09-05_

This Privacy Policy describes how the PersonalMoney Android application and the
PersonalMoney service at <https://personalmoney.app> (together, the **Service**) handle
your information.

The Service is operated by **mamoru** ("we", "us", "our"). Contact:
**aberkunsky@gmail.com**.

## TL;DR

- PersonalMoney is an **account-based** service. You register with an email address and a
  name, and your accounts, transactions, categories and subscriptions are **stored on our
  server** so the same data reaches every device you sign in on.
- The app is **offline-first**: everything is kept in a local database on your device and
  works with no network, then synchronises with the server when there is one.
- If you connect a bank, the credentials you give us are **encrypted and stored on the
  server only**. They are never sent back to a device.
- We show ads through **Google AdMob** and sell an optional subscription through **Google
  Play Billing**.
- You can **delete your account and everything in it** at any time, permanently.

---

## 1. Data you give us

| What | Why | Where it is stored |
| --- | --- | --- |
| Email address | Identifies your account, confirms the address, password resets | Server (PostgreSQL) |
| First and last name | Shown in the app | Server |
| Password | Authentication. Stored only as a bcrypt hash — we cannot read it | Server |
| Accounts, categories, transactions, transfers, subscriptions, currency settings | The purpose of the app | Device (Room) **and** server, kept in sync |
| Bank connection credentials (e.g. a Monobank personal token, a HomeMoney OAuth token) | Importing your statements automatically | Server only, **encrypted at rest**; never returned to a device |
| Imported statement files (CSV) | Turning a statement into transactions | Parsed on the server, not retained as files |

Transaction data includes amounts, currencies, dates, the account and category you chose,
and any note you typed. It is financial data about you, and we treat it as such.

## 2. Data collected automatically

| What | By whom | Why |
| --- | --- | --- |
| Advertising ID, approximate location (from IP), device model / OS / language, app version, ad impressions and clicks | Google AdMob and the User Messaging Platform SDK | Serving and measuring ads |
| Google Play purchase token, purchase state, subscription expiry | Google Play Billing, then our server | Knowing whether your Premium subscription is active |
| Server access logs (IP address, timestamp, request path) | Our server and its Cloudflare / Caddy front end | Security, rate limiting, debugging. Retained short-term |
| Session tokens (JWT access + refresh) | Our server | Keeping you signed in. Stored on the device in Android `EncryptedSharedPreferences` |

## 3. What stays on your device and never reaches us

- **Receipt photos.** Scanning a receipt opens the system camera through
  `ACTION_IMAGE_CAPTURE`, recognises the text on-device with Tesseract (the language data
  ships inside the app), and deletes the image from the cache as soon as it has been read.
  The app declares no `CAMERA` permission and reads no photo library. No image, and no text
  extracted from one, is ever uploaded.
- **Biometric unlock.** Handled entirely by Android's `BiometricPrompt`. We never see a
  fingerprint or a face.
- **Your theme, language and app preferences.**
- **Exported backup files.** You choose where they are written through the system file
  picker; they do not pass through us.

## 4. Third parties

### 4.1 Google AdMob (advertising)

The app shows banner ads. Google may collect the data listed in §2 to serve and measure
them. You can opt out of personalised advertising through the in-app consent dialog (shown
where required, e.g. in the EEA and the UK) or in **Android Settings → Google → Ads**.
Google's policies: <https://policies.google.com/technologies/partner-sites> and
<https://support.google.com/admob/answer/6128543>.

### 4.2 Google Play Billing (payments)

Premium is sold through Google Play. **We never see your card or payment details** — Google
processes the payment and tells us only that a purchase exists and whether it is still
active. See <https://policies.google.com/privacy>.

### 4.3 Resend (transactional email)

Address-confirmation and password-reset mail is sent through Resend, which therefore
processes your email address. <https://resend.com/legal/privacy-policy>

### 4.4 Banks and aggregators you choose to connect

If you connect Monobank or HomeMoney, we call their APIs on your behalf with the
credentials you supplied, and import the transactions they return. We ask them for nothing
beyond statement data. Disconnecting a connection in the app deletes the stored credential.

### 4.5 Exchange rates

Rates come from the European Central Bank via the public Frankfurter API
(<https://frankfurter.app>) and from our own server. Only currency codes are sent — never
amounts, never identifiers.

## 5. Where the data lives

On a virtual server in the European Union, in a PostgreSQL database, behind HTTPS. All
traffic between the app and the server is encrypted in transit. Bank credentials are
additionally encrypted at rest with a key held only on that server.

## 6. How long we keep it

- Your content, for as long as your account exists.
- Deleted account → everything belonging to it is **erased immediately and irreversibly**,
  including bank credentials. There is no tombstone and no recovery.
- Server access logs, a short rolling window.
- Purchase records are retained where tax or accounting law requires it.

## 7. Your rights and how to use them

- **Delete your account** — in the app, **Settings → Delete account**, or on the web at
  <https://personalmoney.app/app/>, or by emailing **aberkunsky@gmail.com**.
- **Export your data** — **Settings → Export** writes a full JSON backup.
- **Correct or access your data** — everything we hold about you is visible in the app;
  write to us if something is missing.
- If you are in the EEA or the UK, the GDPR applies: our legal basis is performance of a
  contract (running the service you signed up for), consent (personalised advertising), and
  legitimate interest (security and abuse prevention). You may complain to your national
  data protection authority.

## 8. Children

PersonalMoney is not directed at children and we do not knowingly collect data from anyone
under 13 (or the local equivalent). If you believe a child has created an account, write to
us and we will delete it.

## 9. Security

Passwords are bcrypt-hashed, bank credentials are encrypted at rest, transport is HTTPS
only, sessions expire and can be revoked, and the login endpoint is rate-limited. No system
is perfect; if you find a problem, please report it to **aberkunsky@gmail.com**.

## 10. Changes

We will update the date at the top when this policy changes, and announce material changes
in the app.
