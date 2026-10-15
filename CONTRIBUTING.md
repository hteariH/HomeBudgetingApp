# Contributing to PersonalMoney

Thanks for helping. This file covers how to run the tests and the conventions the code
already follows. For setting up a development environment, see
[Development in the README](README.md#development).

## Running the tests

Run the suite for whatever you touched before opening a pull request.

```bash
# Server (from the repository root; mvnw.cmd on Windows). Add -Dtest=ClassName for one class.
./mvnw -B -Dfrontend.skip=true test

# Web app
cd src/main/frontend && npm test && npm run typecheck

# Project page
cd site && npm test
```

The Android app (from `client/`; Gradle needs the Android Studio JBR as `JAVA_HOME`):

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```

Some server tests use Testcontainers and need Docker running; they skip themselves when it is
not, and CI runs them. A change is done when the tests for the part you touched pass.

## Conventions

- **Comments explain why**, not what. The code says what it does; a comment is for the
  constraint, the trade-off or the surprise behind it.
- **Money is in minor units.** The database stores amounts as integers of the currency's
  smallest unit (`*_minor` columns), with the number of decimals taken from the currency. The
  API sends decimals as strings. Never use floating point for money; the web app uses
  `decimal.js` for arithmetic.
- **Every schema change is a new Flyway migration** once a version has been released. Never
  edit a migration that has shipped; add `V<next>__description.sql` under
  `src/main/resources/db/migration`.
- **Every UI string exists in English, Russian and Ukrainian.** Web: `src/i18n/locales/{en,ru,uk}.ts`
  for dotted keys and `app.{en,ru,uk}.ts` for snake_case keys, under `src/main/frontend`
  (the project page keeps its own in `site/src/locales`). Android:
  `composeResources/values/strings.xml`, `values-ru` and `values-uk`.
- **The API is shared.** A change to a DTO is one commit that updates the server, the web app
  and the Android app together.
- Follow the style of the code around you, and keep a change focused on one thing.

## Pull requests

Open an issue first for anything large, so we can agree on the direction. Keep pull requests
small, say what changed and why, and include tests for behaviour you add or fix.

## Licence

PersonalMoney is licensed under the [GNU Affero General Public License v3.0](LICENSE). By
contributing you agree that your contribution is licensed under the same terms (AGPL-3.0).

## Security issues

Do not report vulnerabilities in issues; see [SECURITY.md](SECURITY.md).
