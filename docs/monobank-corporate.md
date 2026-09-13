# Monobank without a personal token

Monobank has two APIs behind the same `/personal` paths.

The **personal** one takes a token the customer fetches themselves at api.monobank.ua. It
needs nothing from the bank, which is why the app has always supported it — and it is
close to unusable in front of real people. It asks somebody to visit a developer page they
have never heard of, confirm in their banking app, and paste a string that reads every
transaction they have ever made into a form. Most will not, and they are right not to.

The **corporate** one inverts that. The *company* is authenticated, by a signature; the
customer only approves a request inside the Monobank app they already trust, sees the
access listed there, and revokes it there. Nothing is typed and no long-lived secret
changes hands.

This document is the one-off operator work that buys the second option. Users do none of
it.

## What the app does with it

`MonobankCorporateProvider` implements `ConsentProvider`, so connecting is:

1. `POST /api/bank/providers/MONOBANK_CORPORATE/consent` — the server asks Monobank for a
   request and gets back a `tokenRequestId` and an `acceptUrl`. Nothing is stored: an
   unapproved request is not a connection.
2. The client shows the `acceptUrl` — as a QR encoded by the server (`QrCode`) on a
   desktop, as a link on a phone — and polls
   `POST /api/bank/providers/MONOBANK_CORPORATE/consent/{reference}` every few seconds.
3. The user approves it in the Monobank app. The next poll comes back `CONNECTED`, and the
   `tokenRequestId` is stored as the connection's credential, encrypted like any other.

From then on it is an ordinary `BankProvider`: the request id goes in `X-Request-Id`
wherever the personal provider would have sent `X-Token`, and account linking, preview and
import are shared code.

Every request carries four headers, and getting them wrong fails as an opaque `403`:

| Header | Value |
| --- | --- |
| `X-Key-Id` | the company's key id, from registration |
| `X-Time` | now, in seconds |
| `X-Request-Id` | the customer's `tokenRequestId`, on the endpoints that have one |
| `X-Sign` | `base64(ECDSA-SHA256(X-Time + X-Request-Id + path))`, no separators, request id omitted entirely when absent |

The curve is **secp256k1** — not the `prime256v1` most examples reach for, and one the JDK
dropped from SunEC in 16, which is why BouncyCastle is a dependency.

## Registering

`scripts/monobank-register.sh` does all three steps. It needs `openssl` and `curl`; on
Windows, Git Bash has both. Run it somewhere outside the repository.

```bash
./scripts/monobank-register.sh keygen
```

Writes `monobank-priv.pem` and `monobank-pub.pem`. **The private key is the credential the
whole registration rests on** — Monobank has no rotation but a fresh application, so keep
it out of git, out of images, and backed up.

```bash
export MONO_COMPANY_NAME="…"
export MONO_DESCRIPTION="…"     # what the service is and why it needs statements; at most 100 characters
export MONO_CONTACT="…"
export MONO_PHONE=380671234567
export MONO_EMAIL="…"
export MONO_LOGO=./logo.png     # shown to customers on the approval screen
./scripts/monobank-register.sh register
```

Then, until it stops saying `New`:

```bash
./scripts/monobank-register.sh status
```

`Approved` comes with the `keyId`. `Declined` means writing a better description and
starting again — the bank is judging whether the service has a reason to read somebody's
statement.

The permissions the company is granted are decided here too, and they are fixed per
company rather than per request. Statements (`s`) is what the import needs; personal data
(`p`) only supplies the customer's name, which the app uses to label the connection and
does without if it is not granted.

## Configuring the server

```
MONOBANK_KEY_ID=<the keyId from status>
MONOBANK_PRIVATE_KEY=/etc/personalmoney/monobank-priv.pem   # or the PEM text itself
```

Both PEM shapes work: what `openssl ecparam -genkey` writes, and PKCS#8.

**Without both set, the provider bean does not exist and the bank list does not offer it** —
a self-hosted copy with no company falls back to the personal-token provider, rather than
showing an option that could only ever fail. Nothing else needs configuring: no callback
URL, no public hostname. The approval reaches the app by polling, so the server never has
to be reachable from the internet for this to work.

## Notes for whoever debugs this next

- **`403` on everything** means the deployment's key, not the customer: unapproved, wrong
  curve, or a signature over the wrong bytes. `MonobankCorporateApiTest` pins the bytes.
- **`401` on `GET /personal/auth/request`** is not an error. It is "not approved yet", and
  it is what the poll is asking about. A `401` anywhere *else* means the customer revoked
  access in their app, which the app hears about only by being told no.
- **`429`** — statements are rate limited to roughly one request a minute per key. This is
  why importing is a button rather than a background sync, and why the 31-day window is
  checked before the request rather than after: a rejected request still spends the minute.
- Monobank refuses a statement window wider than **31 days and an hour**.
- A customer who reconnects gets their existing connection updated in place, matched on the
  bank's own `clientId` (`bank_connections.external_client_id`). A second row would leave
  every account they had linked pointing at dead access.
