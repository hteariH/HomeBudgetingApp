# Release checklist — PersonalMoney

A release is a version tag on GitHub. Pushing the tag makes `release.yml` build and publish
the server image, the signed Android APK and the compose files. Section 0 happens once;
section 1 happens every release.

The Gradle module is **`composeApp`**, not `app` — every path below reflects that.

---

## 0. One-time

### Signing keystore

The APK on GitHub Releases is signed with a key you own, and Android only installs an update
over an app signed by the same key.

```powershell
keytool -genkeypair -v -keystore personalmoney.keystore -alias upload `
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=PersonalMoney, O=mamoru, C=UA"
```

`client/personalmoney.keystore` and `client/keystore.properties` (copy it from
`keystore.properties.example`) are **git-ignored** on purpose. Two consequences:

- **Back them up somewhere off this machine** (password manager / encrypted drive). The
  passwords cannot be recovered from anywhere else.
- **Losing the key cannot be fixed.** Nobody can sign an update that installs over the
  existing app; users would have to uninstall first, losing their on-device data.

`composeApp/build.gradle.kts` reads `keystore.properties` at configuration time and skips
release signing entirely if the file is absent, so a clean checkout still builds (unsigned).

In CI the workflow rebuilds that file from four repository secrets, which must exist before the
first tag: `ANDROID_KEYSTORE_BASE64` (`base64 -w0 personalmoney.keystore`),
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`.

### The container image

The first push to `ghcr.io/htearih/personalmoney` creates a **private** package. After the
first release, open the package's settings on GitHub and make it public, or nobody else can
`docker compose pull` it.

### The privacy page

`https://personalmoney.app/privacy.html` is the project's own page, from
`site/public/privacy.html`; the app's Settings screen links to it (`privacyPolicyUrl` in
`keystore.properties` overrides that). Each instance also serves its own at `/privacy.html`,
from `src/main/frontend/public/privacy.html`. Change both together if the wording changes.

---

## 1. Every release

### 1.1 Decide the version

The version comes from the tag: `release.yml` takes `X.Y.Z` from the tag `vX.Y.Z` for the
image, the server and the APK's `versionName`, and uses the workflow run number as the APK's
`versionCode`, so it always goes up. There is nothing to edit in `build.gradle.kts`.

### 1.2 Run everything locally first

```bash
./mvnw -B -Dfrontend.skip=true test        # server (Docker running, so Testcontainers runs)
(cd src/main/frontend && npm test)
(cd site && npm test)
```

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
cd client
./gradlew.bat testDebugUnitTest assembleDebug --no-daemon      # the standing gate
./gradlew.bat :composeApp:assembleRelease --no-daemon          # signed APK, for the device
```

The APK lands in `composeApp/build/outputs/apk/release/`.

### 1.3 Tag

On `master`, with everything committed and pushed:

```bash
git tag v0.1.1
git push origin v0.1.1
```

### 1.4 Wait for `release.yml`

Watch the run on the repository's **Actions** tab. When it is green the GitHub Release has the
signed APK, `docker-compose.yml` and `env.example` attached, and
`ghcr.io/htearih/personalmoney` has the new version's tag and `latest`.

### 1.5 Check the image runs on both architectures

```bash
docker run --rm --platform linux/amd64 --entrypoint java ghcr.io/htearih/personalmoney:latest -version
docker run --rm --platform linux/arm64 --entrypoint java ghcr.io/htearih/personalmoney:latest -version
```

Then do a clean install the way the README says, on a scratch machine or folder: download the
two files from the release, set `DB_PASSWORD`, `docker compose up -d`, wait for
`/actuator/health`, open `http://<machine>:8080` and create the administrator account. On an
existing install, `docker compose pull && docker compose up -d` and confirm the migrations
ran and the data is still there.

### 1.6 Install the APK over the previous one

Smoke-test the **release** APK on a phone that has the previous release installed (R8 only
breaks things in release builds). Install over it — do not uninstall first — and check:

- [ ] It installs as an update and opens with the existing sign-in intact
- [ ] Sign in to a fresh server: enter the address, accept the plain-http warning if any
- [ ] Quick Add → save → appears in Transactions
- [ ] Sync: make a change, open the web app, see it there
- [ ] Widget pin → tap → opens Quick Add
- [ ] Biometric lock on → relaunch → prompt shown
- [ ] Receipt scan reads a real receipt
- [ ] Monobank / HomeMoney / CSV import
- [ ] Export → file opens; Import → restores
- [ ] Delete account → the account is really gone
- [ ] All three languages switch instantly

Anything that works in debug and not here is R8. Add a `-keep` to
`composeApp/proguard-rules.pro` and rebuild — do not disable minification.

### 1.7 Publish the notes

Edit the GitHub Release: say what changed, and call out anything that needs attention when
updating (a new setting in `env.example`, a migration that takes a while).

---

## 2. Cannot be changed after the first release

- `applicationId` — `com.personalmoney`
- The APK signing key
- A shipped Flyway migration — fix mistakes with a new one
