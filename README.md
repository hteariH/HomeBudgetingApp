# PersonalMoney

PersonalMoney is open-source software for tracking personal and family finances that you run
yourself: a server on your NAS, mini PC or VPS, a web app it serves, and an Android app that
connects to it. Your server, your data. Project page and installation guides for more
platforms: [personalmoney.app](https://personalmoney.app).

## Features

- **Accounts in any currency.** Checking, savings, cash, credit and investment accounts, each
  in its own currency with its own precision, from yen with no decimals to bitcoin with eight.
  Exchange rates are fetched automatically.
- **Fast entry, honest transfers.** Income and expenses in a few taps, with your own
  categories. Cross-currency transfers store the actual rate you got, not today's.
- **Bank import and CSV statements.** Connect Monobank, or import a CSV from any other bank.
  Column detection can be corrected, duplicates are spotted, and categories are suggested from
  earlier choices. Import from HomeMoney is supported too.
- **Analytics and budgets.** Net worth, income against expenses over six months, spending by
  category, and budgets to hold the month against.
- **One ledger for the whole family** (planned). Invite the people you live with into a shared
  household with one set of accounts and transactions.
- **Receipts read by your own AI model** (planned). Photograph a receipt and let a model you
  choose turn it into a transaction.
- Web app and Android app, in English, Russian and Ukrainian. No ads, no analytics, no tracking.

## Install

You need a machine that runs Docker with the Compose plugin.

```bash
mkdir personalmoney && cd personalmoney
curl -fsSLO https://github.com/hteariH/HomeBudgetingApp/releases/latest/download/docker-compose.yml
curl -fsSL https://github.com/hteariH/HomeBudgetingApp/releases/latest/download/env.example -o .env
# set DB_PASSWORD in .env (e.g. openssl rand -base64 24)
docker compose up -d
```

Then open `http://<machine>:8080`. The first account you create becomes the administrator.
Your data stays in `./data` and `./postgres` next to the compose file. The administrator page
is at `/app/admin`, and `/actuator/health` answers once the server is up.

Step-by-step guides for Synology, Unraid, TrueNAS and Portainer are on
[personalmoney.app](https://personalmoney.app/#install).

## Configuration

Configuration is a `.env` file next to `docker-compose.yml`. The release's `env.example` (the
repository's `.env.example`) lists every setting. Only `DB_PASSWORD` is required.

| Variable | Default | Meaning |
| :--- | :--- | :--- |
| `DB_PASSWORD` | none, required | Password of the bundled Postgres database. Pick a long random one (`openssl rand -base64 24`); you never type it anywhere else. |
| `PERSONALMONEY_VERSION` | `latest` | Image tag to run. Set it to a release's version tag to stay on that release. |
| `PERSONALMONEY_PORT` | `8080` | Port published on the host. |
| `REGISTRATION` | unset | Who may create an account: `open` or `closed`. Unset means open until the first account (the administrator) exists, then closed. The administrator can toggle it in the app unless it is set here. |
| `JWT_SECRET` | generated | Key that signs sign-in sessions. Generated on first start when unset. Changing it signs everyone out. |
| `CRYPTO_SECRET` | generated | Key that encrypts stored bank connections. Generated on first start when unset. Changing it makes stored bank connections unreadable. |
| `APP_BASE_URL` | unset | The address people open PersonalMoney at, for example `https://money.example.com`. Needed for links in emails. |
| `MAIL_HOST` | unset | SMTP server. Without mail, nobody is asked to verify an address and "forgot password" is replaced by reset links the administrator issues. |
| `MAIL_PORT` | `587` | SMTP port. |
| `MAIL_USERNAME` | unset | SMTP user name. |
| `MAIL_PASSWORD` | unset | SMTP password. |
| `MAIL_FROM` | `noreply@localhost` | Sender address of outgoing mail; set it to an address your SMTP provider accepts. |
| `MONOBANK_KEY_ID` | unset | Key id for Monobank's consent-based corporate API, which needs a company registration with the bank; see [docs/monobank-corporate.md](docs/monobank-corporate.md). Monobank with a personal token always works. |
| `MONOBANK_PRIVATE_KEY` | unset | The matching private key for the corporate API. |
| `HOMEMONEY_CLIENT_ID` | HomeMoney's public demo client | Your own HomeMoney API client id, if you registered one. |
| `HOMEMONEY_CLIENT_SECRET` | HomeMoney's public demo client | The matching client secret. |

`JWT_SECRET` and `CRYPTO_SECRET` are generated into `/data/secrets` (`./data/secrets` on the
host) the first time the server starts and reused afterwards; a value set in `.env` always
wins. The container's entrypoint hands `./data` to the application's user on every start. Back
that directory up: without `CRYPTO_SECRET` the stored bank connections cannot be decrypted.

## HTTPS

PersonalMoney speaks plain HTTP on port 8080. To reach it from outside your home network, or
simply to get a padlock, put a reverse proxy in front of it (Caddy, Traefik, Nginx Proxy
Manager), or use a private network such as Tailscale. Set `APP_BASE_URL` to the public
address. A minimal Caddy example:

```
money.example.com {
	reverse_proxy <machine>:8080
}
```

## Updating

```bash
docker compose pull
docker compose up -d
```

Database migrations run automatically when the new version starts. Pin `PERSONALMONEY_VERSION`
if you want to choose when to move to a new release.

## Backups

Back up the database and `./data` (it holds the generated secrets):

```bash
docker compose exec db pg_dump -U personalmoney personalmoney > personalmoney-$(date +%F).sql
# and copy ./data (it holds the generated secrets)
```

To restore, start only the database into an empty `./postgres` with
`docker compose up -d db`, load the dump with
`docker compose exec -T db psql -U personalmoney personalmoney < personalmoney-2026-10-15.sql`,
put your saved `./data` back, then start the rest with `docker compose up -d`.

## Locked out

If the administrator forgets the password and no mail is configured:

```bash
docker compose exec app personalmoney reset-password you@example.com
```

## Android

Download the APK from the [latest release](https://github.com/hteariH/HomeBudgetingApp/releases/latest)
and install it (Android will ask to allow installs from your browser or file manager). When it
starts, enter your server's address, for example `https://money.example.com`; the app checks
that the address is a PersonalMoney server before it lets you sign in.

A plain `http://` address works, which is handy on a home network, but the app warns that
traffic to it is not encrypted. If your server uses a certificate from your own certificate
authority, install that CA certificate on the phone (Settings, Security, Encryption and
credentials, Install a certificate, CA certificate); the app trusts CAs installed there.

## Development

### Requirements

- **JDK 25** for the toolchain; the bytecode target is Java 21 (`java.version` in `pom.xml`),
  which is also what the release image is built with.
- **Maven**: use the wrapper (`./mvnw`, `mvnw.cmd` on Windows).
- **Node.js and npm** are installed locally by Maven for a full build; for hot reload while
  you work on the web app, use Node 20 or newer.
- **Docker**, only for the Testcontainers suite (it skips itself when Docker is not running).
- For the Android app: Android Studio (its bundled JBR is the JDK Gradle uses).

### Run locally

The `local` profile uses an H2 file database (`./data/homebudgetdb`) in PostgreSQL mode, so
you need no database server.

1. **Server:**
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```
   It listens on [http://localhost:8085](http://localhost:8085). The H2 console is at
   [http://localhost:8085/h2-console](http://localhost:8085/h2-console).

2. **Web app (hot reload):**
   ```bash
   cd src/main/frontend
   npm install
   npm run dev
   ```
   Open [http://localhost:3000](http://localhost:3000) (the application itself is at `/app`).
   `/api` is proxied to the server on port 8085.

3. **Android app:** build and test from `client/`; see [client/CLAUDE.md](client/CLAUDE.md)
   for the emulator workflow. Gradle needs the Android Studio JBR as `JAVA_HOME`.
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
   ./gradlew.bat testDebugUnitTest assembleDebug --no-daemon
   ```
   In a POSIX shell, set `JAVA_HOME` to the same directory and run `./gradlew`. The app asks
   for the server address at sign-in; from an Android emulator the server running on your
   machine is `http://10.0.2.2:8085`.

### Full build

```bash
./mvnw clean package
```

This installs Node locally, builds the web app, and packages everything into one JAR in
`target/`. The release image is built from the `Dockerfile` in the repository root.

### Scripts

- `./mvnw -B -Dfrontend.skip=true test`: server tests (without rebuilding the web app).
- `./mvnw spring-boot:run`: runs the server.
- In `src/main/frontend`: `npm run dev` (Vite on port 3000), `npm run build` (type-check and
  production bundle), `npm test` (Vitest, once; `npm run test:watch` to keep watching) and
  `npm run typecheck`.
- In `site/`: `npm run dev`, `npm run build` and `npm test` for the project page.

### Tests

```bash
./mvnw -B -Dfrontend.skip=true test      # server (add -Dtest=ClassName for one class)
cd src/main/frontend && npm test         # web app
cd site && npm test                      # project page
```

The Android suite is `testDebugUnitTest` from `client/`, shown above. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the conventions.

## Project structure

```text
HomeBudgetingApp/
├── pom.xml                 # Maven project configuration
├── Dockerfile              # The release image (ghcr.io/htearih/personalmoney)
├── docker-compose.yml      # What self-hosters run: the app and Postgres
├── .env.example            # Every setting, published as the release asset env.example
├── docker/                 # Container entrypoint and the `personalmoney` admin command
├── deploy/                 # How the project page at personalmoney.app is deployed
├── docs/                   # Extra documentation (e.g. Monobank corporate API)
├── site/                   # The project page: personalmoney.app (React + Vite)
├── client/                 # The Android app (Kotlin, Compose Multiplatform)
├── src/
│   ├── main/
│   │   ├── kotlin/         # Kotlin server source code
│   │   │   └── com.homebudget.app
│   │   │       ├── admin/ bank/ config/ controller/ importer/ instance/ security/
│   │   │       ├── domain/     # JPA Entities (Account, Category, Transaction, etc.)
│   │   │       ├── dto/        # Data Transfer Objects
│   │   │       ├── repository/ # Spring Data JPA Repositories
│   │   │       └── service/    # Business Logic
│   │   ├── resources/      # Server resources
│   │   │   ├── application.yml
│   │   │   ├── application-local.yml
│   │   │   ├── application-prod.yml
│   │   │   └── db/migration/   # Flyway migrations
│   │   └── frontend/       # The web app (React + Vite)
│   │       ├── index.html
│   │       ├── public/
│   │       ├── src/
│   │       │   ├── App.tsx       # Top-level routes: landing, sign-in pages, /app/*
│   │       │   ├── landing/      # Landing page and the pages before there is an account
│   │       │   ├── app/          # The application: api/, lib/, components/, shell/, features/
│   │       │   └── i18n/         # One dictionary for everything, en / ru / uk
│   │       ├── package.json
│   │       └── vite.config.ts
│   └── test/
│       └── kotlin/         # Server tests
└── target/                 # Build artifacts
```

The server, the web app and the Android app live in one repository because they share an API
contract: a change to a DTO is one commit on every side, and a release ships the server and
the web app it serves as the same image.

## License

PersonalMoney is licensed under the [GNU Affero General Public License v3.0](LICENSE). You may
use, study, change and share it freely; if you run a modified version as a service that other
people use over a network, you must offer those users the source code of your modified
version under the same licence.
