# Deploying PersonalMoney

The stack sits behind the shared `edge` Caddy that owns ports 80/443 on the VPS, the
same way the wordle and crocodile stacks do. Nothing here publishes a host port.

## One-time setup

### 1. DNS

| Record | Value |
| --- | --- |
| `A` `personalmoney.app` | VPS IP |
| `A` `www.personalmoney.app` | VPS IP |

Certificates are issued by the edge Caddy on first request; nothing to do beyond DNS.

### 2. Mail — Resend

Sending is plain SMTP, so the provider is swappable via environment variables. Resend's
values are the defaults in `docker-compose.yml`; only the API key is a secret.

1. Add `personalmoney.app` as a domain in Resend.
2. Add the DNS records Resend generates for it — a DKIM record plus SPF, and usually an
   MX on a sending subdomain for bounce handling. **Take them from the Resend dashboard
   rather than copying them from anywhere else**: the DKIM value is unique per domain.
3. Wait for Resend to show the domain as verified. Until it does, mail either will not
   send or will land in spam.
4. Create an API key and put it in the `MAIL_PASSWORD` repository secret.

Consider adding a DMARC record (`_dmarc`, starting at `p=none`) once SPF and DKIM pass.
It is not required to send, but it is what stops somebody else spoofing the domain.

**Until `MAIL_PASSWORD` is set the app still runs.** Mail failures are swallowed and
logged, so registration works and nobody is locked out - the confirmation link simply
never arrives, and users can request another one.

### 3. Secrets

Application secrets live in `/opt/homebudget/.env` on the server, the same way the
edge and wordle stacks do. Deploys never write to that file, so it survives every
release and is the one place to look.

```bash
sudo mkdir -p /opt/homebudget
sudo cp .env.example /opt/homebudget/.env   # then fill it in
sudo chmod 600 /opt/homebudget/.env
```

Generate the two secrets with `openssl rand -base64 32` and **keep copies in a password
manager**. `CRYPTO_SECRET` in particular cannot be recovered: lose it and every stored
bank connection becomes unreadable, so users have to reconnect.

GitHub only needs the SSH secrets - `HOST`, `USERNAME`, `VPS_SSH_KEY` and optionally
`VPS_PORT`. Nothing about the application is stored there.

### 4. The database must be created fresh

The schema is not compatible with whatever the pre-Flyway deployment left behind: money
changed type, the `transfers` table is gone, and constraint names differ. Flyway is
deliberately not baselined, so it will refuse to run against the old schema rather than
half-migrate it.

Drop the old database once, before the first deploy of this version.

## What the deploy does

On push to `master`: run the backend tests, build and push the image to GHCR, copy
`docker-compose.yml` to `~/homebudget` and `deploy/personalmoney.caddy` to
`/opt/edge/conf.d/`, then bring the stack up and reload the shared Caddy.

The app joins the external `edge` network as `personalmoney-app`; the database stays on
the stack's own network and is never reachable from the proxy or the internet.
