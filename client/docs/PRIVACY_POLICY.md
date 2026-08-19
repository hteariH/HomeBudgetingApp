# Privacy Policy — PersonalMoney

_Last updated: 2026-06-11_

This Privacy Policy describes how the PersonalMoney Android application (the **App**) handles your information.

The App is operated by the developer **mamoru** ("we", "us", "our"). If you have any questions about this policy, contact us at **aberkunsky@gmail.com**.

## TL;DR

- Your financial data (accounts, transactions, categories, subscriptions, settings) stays **on your device**. We never see it, and we never upload it anywhere.
- The App makes two kinds of outbound network requests:
  1. **Currency exchange rates** from the public [Frankfurter API](https://www.frankfurter.app/) — only currency codes (e.g. "USD,EUR,RUB") are sent. No user identifiers, no transaction details.
  2. **Advertising** through Google AdMob, which uses Google's standard advertising identifiers.
- We support deleting all data by uninstalling the App or by tapping **Settings → Clear all data**.

---

## 1. Data we store on your device

The App writes the following data to your device's private storage:

| Category | Examples | Storage |
| --- | --- | --- |
| Accounts | Account name, currency, initial balance, icon, color | Room database (local) |
| Categories | Category name, type (income / expense), icon, color | Room database (local) |
| Transactions | Amount, currency, account, category, date, note | Room database (local) |
| Subscriptions | Name, amount, frequency, next charge date | Room database (local) |
| Cached exchange rates | Public ECB rates (no personal data) | Room database (local) |
| Preferences | Base currency, theme, language, biometric lock toggle | DataStore (local) |

This data **never leaves your device** through the App.

If you enable Android Auto Backup, Google Play services may include the App's data in your Google account backup. The App excludes the local settings file `device.xml` from backup. You control Auto Backup in your device's system settings.

## 2. Data we collect about you

We do not collect, store, or transmit any personally identifiable information through the App.

We do **not** request the following permissions: contacts, SMS, location, camera, microphone, files outside what you explicitly pick via the system file picker.

## 3. Third-party services

### 3.1 Google AdMob (advertising)

The App displays banner advertisements on the dashboard and transaction history screens via Google AdMob, the Mobile Ads SDK, and the User Messaging Platform (UMP) SDK. To deliver and measure ads, Google may collect and process:

- the Advertising ID assigned to your device by Google Play services,
- approximate location based on IP address,
- the App's package name and version,
- device information (model, OS version, screen size, language),
- ad event signals (impressions, clicks).

You can opt out of personalized advertising via:

- the in-app consent dialog (shown on first launch in the EEA / UK and in jurisdictions where required), or
- your device settings → **Google → Ads → Reset advertising ID / Opt out of Ads Personalization**.

Google's privacy notices apply:

- AdMob: <https://support.google.com/admob/answer/6128543>
- Google Privacy Policy: <https://policies.google.com/privacy>
- How Google uses data: <https://policies.google.com/technologies/partner-sites>

We never share your financial data with AdMob. The advertising SDK has no access to your transactions, accounts, or other in-app data.

### 3.2 Frankfurter (currency exchange rates)

When the App refreshes currency exchange rates, it sends a request to `https://api.frankfurter.app/latest` containing only the base currency code and the list of currency codes you have activated. No user identifiers, no transaction data, no IP-linked profile is sent by the App. The Frankfurter service is operated by a third party — see <https://www.frankfurter.app/> for their terms.

You can avoid this network request entirely by leaving the device offline or by setting exchange rates manually.

## 4. Permissions used

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Currency rate refresh and ad delivery |
| `POST_NOTIFICATIONS` | Optional reminders for upcoming subscription charges |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule subscription reminder jobs after restart |
| `USE_BIOMETRIC`, `USE_FINGERPRINT` | Optional biometric / device-credential app lock |
| `VIBRATE` | Haptic feedback for keypad |
| `AD_ID` | Google AdMob advertising identifier (Android 13+) |

## 5. Children

The App is not directed at children under 13. We do not knowingly collect personal data from children.

## 6. Security

Your data is stored in your device's app-private storage and is therefore protected by the device's standard sandbox. You can additionally enable biometric / device-credential lock in **Settings → Biometric lock**.

We do not transmit your financial data over the network, so we do not encrypt it in transit (because we never transmit it).

## 7. Your rights

Because we do not collect personal data, there is no "user account" for us to delete. You retain full control over your data:

- **Export:** Settings → Export data — produces a JSON file you save anywhere you choose.
- **Import:** Settings → Import data — restores from a JSON file you select.
- **Erase:** uninstall the App, or use Android system settings → Apps → PersonalMoney → Storage → Clear data.

If you live in the EEA, UK, California, or a similar jurisdiction, you can also reset your AdMob advertising identifier or opt out of personalized ads via the system settings above, and use the in-app **Manage consent** option (if shown) to change your ad consent at any time.

## 8. Changes to this policy

We may update this policy from time to time. Material changes will be reflected by updating the "Last updated" date at the top. Continued use of the App after such updates constitutes acceptance.

## 9. Contact

Questions, requests, or complaints regarding this policy:

- Email: **aberkunsky@gmail.com**
- Postal address: available on request.

---

_The canonical, publicly hosted copy of this policy lives at
<https://gist.github.com/hteariH/bfd7d6412cc41a227bcc53646655bfc2>, linked from the Google Play
listing and from the App's **Settings → Privacy policy** screen. Keep this gist in sync when the
policy changes._
