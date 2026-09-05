# Release checklist — PersonalMoney

From a working build to Google Play. Section 0 happens once for the app, section 1 happens
every release.

The Gradle module is **`composeApp`**, not `app` — every path below reflects that.

---

## 0. One-time, per app

### 0.1 Upload keystore — done

```powershell
keytool -genkeypair -v -keystore personalmoney.keystore -alias upload `
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=PersonalMoney, O=mamoru, C=UA"
```

`client/personalmoney.keystore` and `client/keystore.properties` exist and are **git-ignored**
on purpose. Two consequences, both real:

- **Back them up somewhere off this machine** (password manager / encrypted drive). The
  passwords cannot be recovered from anywhere else.
- Losing the key is survivable but not free: with Play App Signing, Google can reset the
  *upload* key on request, so users keep getting updates. Losing it before the first upload
  means simply making a new one.

`composeApp/build.gradle.kts` reads `keystore.properties` at configuration time and skips
release signing entirely if the file is absent, so a clean checkout still builds.

### 0.2 AdMob

The publisher id `pub-4510992616986316` is already served at
`https://personalmoney.app/app-ads.txt` (see `deploy/personalmoney.caddy`).

**The current build ships Google's test ad unit ids** — `keystore.properties` leaves
`admobAppId` / `admobBannerUnitId` / `admobInterstitialUnitId` commented out and the build
falls back to the test units. That is fine for a closed test and **must not reach
production**: serving test ads to real users, or real ads you click yourself, both breach
AdMob policy.

Before the production track: create the app and its ad units at <https://apps.admob.com>,
uncomment those three keys, and rebuild. The AdMob **app id** is baked into the manifest, so
changing it needs a new build and a new versionCode.

### 0.3 Privacy policy — hosted

`https://personalmoney.app/privacy.html`, served from
`src/main/frontend/public/privacy.html`. `SpaFallbackController` only forwards paths without
a dot, so the `.html` is load-bearing — the static resource handler answers it directly, which
matters because Play's crawler runs no JavaScript.

It sits in the CRA `public/` directory rather than `src/main/resources/static/`, which is
git-ignored build output: react-scripts copies `public/` verbatim into the bundle, and Maven
copies that into `target/classes/static`. A file dropped straight into the resources
directory works when you run locally and ships nothing.

The wording lives twice, in that file and in [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md). Change
both together.

**The page is only live after the server is deployed.** Check it before filling in the Play
form:

```bash
curl -sI https://personalmoney.app/privacy.html | head -1
```

### 0.4 Create the app in Play Console

<https://play.google.com/console> → **Create app**.

| Field | Value |
| --- | --- |
| App name | PersonalMoney: Budget Tracker |
| Default language | English (United States) |
| App or game | App |
| Free or paid | Free |

The application id is fixed at **`com.personalmoney`** and can never be changed after the
first upload. Neither can the upload key, nor the AdMob app id in the manifest without a new
build.

### 0.5 First upload is manual

The Play Developer API cannot create an app and refuses the first artifact for an app that has
never had one. Upload `composeApp-release.aab` by hand in Play Console. Automating later
releases (Gradle Play Publisher + a service account) is optional and only worth it once the
cadence justifies it.

---

## 1. Every release

### 1.1 Bump the version

In `composeApp/build.gradle.kts` → `defaultConfig`:

```kotlin
versionCode = <previous + 1>     // monotonic integer, never reused
versionName = "0.1.1"
```

A versionCode is consumed the moment an AAB is uploaded, whether or not it is rolled out.

### 1.2 Check the target API level is still accepted

Play raises the floor every year, on 31 August. `targetSdk` was moved to **36** for this
release because 35 stopped being accepted on **31 August 2026** — an upload below the floor
is rejected outright, not warned about.

Next deadline: expect API 37 around 31 August 2027. Check
<https://support.google.com/googleplay/android-developer/answer/11926878> before each
release rather than trusting this line.

A `targetSdk` bump is a behaviour change, not a number: re-run the smoke test in 1.4 on a
device after one.

### 1.3 Build and test

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
cd client
./gradlew.bat testDebugUnitTest assembleDebug --no-daemon      # the standing gate
./gradlew.bat :composeApp:assembleRelease --no-daemon          # signed APK, for the device
./gradlew.bat :composeApp:bundleRelease --no-daemon            # signed AAB, for Play
```

Outputs land in `composeApp/build/outputs/apk/release/` and
`composeApp/build/outputs/bundle/release/composeApp-release.aab`.

Confirm the AAB really is signed — an unsigned one is rejected on upload, and the build
succeeds either way:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\jarsigner.exe" -verify -verbose:summary `
  composeApp\build\outputs\bundle\release\composeApp-release.aab
```

### 1.4 Smoke-test the **release** APK on a device

R8 only breaks things in release builds, and the release build is the one that talks to
production (`apiBaseUrl` from `keystore.properties`; debug points at `10.0.2.2:8085`).

- [ ] Register a new account, confirm the email arrives
- [ ] Sign in, sign out, sign back in
- [ ] Quick Add → save → appears in Transactions
- [ ] Sync: make a change, open the web client, see it there
- [ ] Widget pin → tap → opens Quick Add
- [ ] Biometric lock on → relaunch → prompt shown
- [ ] Receipt scan reads a real receipt
- [ ] Monobank / HomeMoney / CSV import
- [ ] Premium purchase flow (needs a licence tester, see 1.6)
- [ ] Export → file opens; Import → restores
- [ ] Delete account → the account is really gone
- [ ] AdMob banner appears on Dashboard
- [ ] All three languages switch instantly

Anything that works in debug and not here is R8. Add a `-keep` to
`composeApp/proguard-rules.pro` and rebuild — do not disable minification.

> `proguard-rules.pro` still carries Hilt and Retrofit rules from before the Koin/Ktor
> migration. Harmless, but it means the file is not evidence that the current stack is
> covered — check Ktor, Koin and Play Billing behaviour on the device rather than assuming.

### 1.5 Upload

Play Console → **Testing → Closed testing → Create new release** → upload the AAB → paste the
release notes from [`STORE_LISTING.md`](STORE_LISTING.md) → **Save → Review release → Start
rollout**.

Track names differ between the Console and the API:

| Console | API track |
| --- | --- |
| Internal testing | `internal` |
| Closed testing | `alpha` |
| Open testing | `beta` |
| Production | `production` |

### 1.6 In-app purchases

Premium is sold through Play Billing, so the product has to exist in Play Console
(**Monetise → Subscriptions**) with the same product id the app asks for, and the build has to
be on a track, before a purchase can be tested at all. Add yourself under **Setup → Licence
testing** to buy without being charged.

---

## 2. App content forms — all block a release

| Form | Source of truth |
| --- | --- |
| Privacy policy | `https://personalmoney.app/privacy.html` |
| Data safety | [`DATA_SAFETY.md`](DATA_SAFETY.md) |
| Ads | Yes, the app contains ads |
| Content rating | Finance / Utility questionnaire — see `DATA_SAFETY.md` §4 |
| Target audience | 18+ |
| Account deletion URL | `https://personalmoney.app/app/` |
| Financial features | Personal finance tracker; no lending, investing, trading or money movement |
| Data safety ↔ listing consistency | The listing must not claim anything the form contradicts |

A wrong Data safety answer is a **policy violation**, not a form error. Fill it from
`DATA_SAFETY.md` rather than from memory of what the app used to do.

---

## 3. The closed-testing requirement

A personal developer account created after 13 November 2023 must run a closed test with at
least **12 testers who stay opted in for 14 continuous days** before it can apply for
production access. Organisation accounts are exempt.

The 14 days start when the twelfth real tester has opted in — not when the build was
uploaded — and the counter resets if the number drops. Emulators and duplicate accounts do
not count. Plan the calendar around that, not around the build.

---

## 4. Cannot be changed after the first release

- `applicationId` — `com.personalmoney`
- The app signing key
- The AdMob app id in the manifest (needs a new build, not just a Console change)
- A versionCode, once uploaded

Sanity-check these before the first upload, not after.
