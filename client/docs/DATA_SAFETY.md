# Google Play Data Safety form — PersonalMoney

This is a fill-in template that maps the Play Console **Data Safety** questionnaire to PersonalMoney's actual behavior. Copy the answers below into Play Console when you fill out the form. Update if you change the app's networking or SDK behavior.

> Last verified against the codebase: 2026-05-28.

---

## Section 1 — Data Collection and Security

**Does your app collect or share any of the required user data types?** → **Yes**

(Reason: the AdMob SDK collects the advertising identifier and ad-event signals. The app itself does not collect user data, but Google requires you to declare the SDK's collection.)

**Is all of the user data collected by your app encrypted in transit?** → **Yes**

(All requests use HTTPS — Google AdMob endpoints and `api.frankfurter.app`.)

**Do you provide a way for users to request that their data be deleted?** → **Yes**

(Uninstall the app, or **Settings → Clear data** in Android system settings, removes the local database. The advertising identifier can be reset via Android system settings.)

---

## Section 2 — Data types

Declare only the items listed below. Everything not mentioned should be left unchecked.

### Location

| Data type | Collected | Shared | Optional? | Purposes | Notes |
| --- | --- | --- | --- | --- | --- |
| Approximate location | ✅ Collected, ❌ Not shared | Yes (auto by AdMob) | Yes | Advertising or marketing, Analytics | Collected by Google AdMob via IP-based geolocation. PersonalMoney does not access location APIs directly. |

### Personal info

None directly. **Do not check any boxes here.**

### Financial info

> ⚠️ **Important interpretation note.** Play's Data Safety form asks about data your app **collects or shares over the network**. Financial data stored only on-device is not "collected" by Play's definition. Therefore: **leave all Financial info boxes unchecked**. If asked separately in your store listing description, state that financial data is stored locally only.

### App activity / App info & performance

| Data type | Collected | Shared | Optional? | Purposes |
| --- | --- | --- | --- | --- |
| App interactions | ✅ Collected, ❌ Not shared | No | Advertising or marketing |
| Crash logs | ❌ Not collected | — | — | — (we do not integrate Firebase Crashlytics; AdMob may collect minimal SDK crash signals — declare only if your AdMob configuration enables it) |

(Ad-interaction signals fall under "App interactions" for AdMob.)

### Device or other IDs

| Data type | Collected | Shared | Optional? | Purposes |
| --- | --- | --- | --- | --- |
| Device or other IDs | ✅ Collected, ❌ Not shared | No | Advertising or marketing |

(The Android Advertising ID collected by AdMob. The app itself does not read `ANDROID_ID` or any other identifier.)

### All other categories

(Files & docs, Audio, Photos & videos, Messages, Contacts, Calendar, Health & fitness, Web browsing history) — **leave unchecked**. The app does not access any of these.

> **Receipt scanning does not change this.** The photo is taken by the system camera app through `ACTION_IMAGE_CAPTURE` (so the app declares no `CAMERA` permission and reads no photo library), recognised on-device by Tesseract with language data bundled in the APK, and deleted from the cache as soon as it has been read. No network call is involved at any point. Nothing about the image leaves the device, so Play's definition of "collected" is not met and **Photos & videos stays unchecked**.

---

## Section 3 — Security practices

- **Data is encrypted in transit:** Yes (HTTPS for all outbound requests).
- **Users can request that data be deleted:** Yes (uninstall or **Settings → Clear all data**; reset advertising ID via system settings).
- **You follow the Families Policy:** Only check if you target ages 13+. PersonalMoney is not directed at children.
- **Independent security review:** No (unless you have one).

---

## Section 4 — Privacy policy URL

Provide the public URL of your hosted Privacy Policy.

- Template: `docs/PRIVACY_POLICY.md` in this repository.
- Recommended hosting: GitHub Pages, your own domain, or a static-site host. The URL **must** be reachable without authentication.

---

## Section 5 — When the answers change

Re-do this checklist whenever you:

- add a new SDK (analytics, crash reporting, attribution, etc.),
- start sending user data to your own backend,
- enable Crashlytics or any other crash collector,
- add user authentication (account creation, sign-in),
- expand permissions (location, contacts, files outside SAF, etc.),
- start retaining or uploading receipt images rather than deleting them after recognition.

---

## Quick mapping reference

| Actual app behavior | Play Data Safety answer |
| --- | --- |
| AdMob banner + UMP consent | "Approximate location" + "App interactions" + "Device or other IDs" — collected, not shared |
| Frankfurter currency rates request | Not user data (only currency code strings) — no declaration needed |
| Local Room database (accounts, transactions, etc.) | Not "collected" by Play's definition (stays on device) — no declaration needed |
| Local biometric prompt | Not user data — no declaration needed |
| Local backup file via SAF | Not collection — the user chooses where to save the file |
| Receipt scan (camera → on-device Tesseract → deleted) | Not collection — the image never leaves the device and is not retained |
