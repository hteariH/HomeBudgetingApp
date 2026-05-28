# Release checklist — PersonalMoney

Step-by-step from "working debug build" to "live on Google Play". Tick each box once; the next release just re-runs sections marked **every release**.

---

## 0. One-time setup

### 0.1 Create the upload keystore
```bash
keytool -genkeypair -v \
  -keystore personalmoney.keystore \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=PersonalMoney, O=YourName, C=XX"
```
Move the resulting `personalmoney.keystore` to the repository root (it is git-ignored).

### 0.2 Configure signing & ad IDs
```bash
cp keystore.properties.example keystore.properties
# edit keystore.properties — set keystore passwords, AdMob production IDs, privacy URL
```
`app/build.gradle.kts` reads this file at configuration time; without it, debug still builds but release won't be signed.

### 0.3 Set up AdMob
1. Create the app in <https://apps.admob.com>.
2. Create one **Banner** ad unit and one **Interstitial** ad unit (we keep interstitial off by default but the unit must exist for the SDK init).
3. Paste the IDs into `keystore.properties`.
4. Run a debug build at least once with real IDs — AdMob's first impression takes ~24 h to start counting.

### 0.4 Host the Privacy Policy
- Take [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md), fill in your contact info, render to HTML.
- Host at a stable public URL (GitHub Pages, Vercel, your own domain).
- Put that URL into `keystore.properties` under `privacyPolicyUrl=`.
- Verify it loads in a private browser window (no auth).

### 0.5 Generate the production app icon
Current icons are placeholder vectors. Use [Android Studio → Image Asset → Launcher Icons (Adaptive)] with a 512×512 PNG. Export will overwrite:
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/mipmap-*/ic_launcher.png` (legacy fallbacks)

Also generate a 512×512 Play Store icon and a 1024×500 feature graphic (see [`STORE_LISTING.md`](STORE_LISTING.md)).

---

## 1. Every release

### 1.1 Bump version
In `app/build.gradle.kts`:
```kotlin
versionCode = <previous + 1>     // monotonic integer, never reused
versionName = "0.1.1"            // human-readable, semver-ish
```

### 1.2 Pre-flight checks (local)
```bash
./gradlew clean
./gradlew lint test
./gradlew :app:assembleRelease   # signed APK — install on a real device to smoke-test
./gradlew :app:bundleRelease     # signed AAB — what you upload to Play
```
Smoke-test on the release APK:
- [ ] Onboarding completes
- [ ] Quick Add → save → appears in Transactions
- [ ] Widget pin → tap → opens Quick Add
- [ ] Biometric lock toggle on → relaunch → prompt shown
- [ ] AdMob banner loads on Dashboard (test ad, since you're on debug AdMob IDs locally)
- [ ] Settings → Export → file opens in Files
- [ ] Settings → Import → previously exported file restores
- [ ] All three languages (System / English / Русский / Українська) switch instantly

### 1.3 ProGuard / R8 sanity
After `assembleRelease`, ensure these features still work in the obfuscated build:
- [ ] Frankfurter currency rate refresh (turn off & on the network, watch logs)
- [ ] Subscription auto-charge (use `adb shell cmd jobscheduler run -f com.personalmoney 0`)
- [ ] Backup JSON round-trip

If anything is missing, R8 has stripped something. Add a `-keep` rule to `proguard-rules.pro` and re-test.

### 1.4 Play Console upload
1. Open <https://play.google.com/console>.
2. **Internal testing → Create new release**.
3. Upload `app/build/outputs/bundle/release/app-release.aab`.
4. Paste **release notes** (max 500 chars per language) — keep one in `docs/RELEASE_NOTES_<version>.md` for history.
5. **Save → Review release → Roll out to Internal testing**.
6. Wait ~10 min, install via Play Internal testing link on a real device, repeat the smoke-test from §1.2.

### 1.5 Promote
After 24–48 h on Internal testing with no crashes:
- **Promote to Closed testing** (private testers from email list)
- **Promote to Production** with **staged rollout 10 % → 25 % → 50 % → 100 %**

Watch the Play Console **Android vitals** tab between rollout stages. ANR rate > 0.47 % or crash rate > 1.09 % means halt and investigate.

---

## 2. Required Play Console forms (every release after major changes)

| Form | When to update | Source of truth |
| --- | --- | --- |
| **App content → Privacy policy** | Whenever you change your privacy URL | `keystore.properties` → `privacyPolicyUrl` |
| **App content → Data safety** | When SDKs change (e.g. you add Firebase, Crashlytics) | [`DATA_SAFETY.md`](DATA_SAFETY.md) |
| **App content → Target audience** | Never (we declared 13+) | — |
| **App content → Ads** | Never (we declared "Yes, ads") | — |
| **Store listing** | When marketing copy changes | [`STORE_LISTING.md`](STORE_LISTING.md) |
| **Pricing & availability** | Country additions | — |

---

## 3. Optional but recommended

- [ ] Hook the project up to **Firebase Crashlytics** (zero crashes is a goal, not a default). If you add it, update `DATA_SAFETY.md` to declare crash log collection.
- [ ] Add **Play App Signing** (Google manages the upload key, you keep a download key) — Play Console offers this on first AAB upload.
- [ ] Configure **Play Integrity API** if you ever add a backend (we don't have one).
- [ ] Add an **End-User License Agreement** screen if your jurisdiction requires it.

---

## 4. Things you cannot fix after release without a new APK/AAB

- `applicationId` (`com.personalmoney`)
- The keystore (lose it = lose the app on Play forever; **back it up to encrypted offline storage**)
- AdMob app ID embedded in the manifest

Sanity-check these now, before the first upload, not after.
