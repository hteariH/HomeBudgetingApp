# Premium

The free plan covers **5 accounts in play** (archived ones do not count). Everything past
that needs premium. The cap is enforced on the server in three places — REST account
creation, sync push, and the HomeMoney importer — so no client can talk its way around it.

There are exactly two ways to become a subscriber, and they are the same kind of
subscriber afterwards:

| | Where | Who does it |
|---|---|---|
| A Play subscription | The Android app | The user, in the Play dialog |
| A manual grant | `POST /api/admin/entitlements/users/{id}` | An administrator |

Nothing else grants premium, and nothing else may write `users.entitlement` — every path
goes through `EntitlementService`.

## Why only Android

Google Play Billing is the only payment rail this project can currently use. Stripe does
not accept merchants in Ukraine, which rules out Stripe itself and every provider built on
it, so there is no way to sell a subscription on the web today. The web client therefore
shows the paywall but no purchase button: `/api/billing/status` answers
`purchasesAccepted: false` on a browser and the screen says where to buy instead.

## How a purchase becomes an entitlement

1. The app queries Play for the products the server named in `/api/billing/status`
   (`premium_monthly`, `premium_yearly` by default) and launches the Play dialog.
2. Play returns a **purchase token**. The app posts it to `/api/billing/play/purchase`.
3. The server asks Google — `purchases.subscriptionsv2.get` — what that token bought, and
   grants premium until the expiry Google reports. The phone's own opinion of its
   subscription is never used.
4. The server acknowledges the purchase with Play. This is not optional: Play refunds a
   purchase left unacknowledged for three days.
5. Every hour, `PlayBillingService.recheckExpiring` re-asks Google about purchases that
   have reached their expiry. This is how renewals keep working on a phone nobody opens,
   and how a lapse eventually takes premium away.

A purchase token belongs to one account. Presenting one that another account already holds
is refused rather than silently transferred.

`ACTIVE`, `CANCELED` and `IN_GRACE_PERIOD` all pay for premium — cancelling stops the
renewal, it does not refund the period already paid for. `ON_HOLD`, `PAUSED`, `PENDING`
and `EXPIRED` do not.

## Server setup

Purchases can only be verified with a Google service account that Play Console has given
access to the app:

1. In Google Cloud, create a service account and a JSON key.
2. In Play Console → **Users and permissions**, invite that service account and give it
   *View financial data* and *Manage orders and subscriptions* for this app.
3. Put the key on the server and point `PLAY_SERVICE_ACCOUNT` at it — either the JSON
   itself on one line or a path to the file. See `.env.example`.
4. Create the subscription products in Play Console with the ids in `PLAY_PRODUCTS`.

With `PLAY_SERVICE_ACCOUNT` empty — every development machine — nothing can be bought and
the paywall says so. Manual grants still work.

## Granting premium by hand

`role = 'ADMIN'` on the `users` row, and a normal access token. There is no UI, and no
way to promote an account from inside the app either - it is one statement against the
database:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';
```

Locally that is the H2 console at `/h2-console` (the `local` profile turns it on); in
production it is the MariaDB shell. Sign out and back in afterwards - the role is read
from the row on every request, but an old token's principal is not.

Find the account:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/admin/entitlements/users?query=someone@example.com"
```

With no `query` it lists everyone who currently has an entitlement, and where each came
from — the "who has premium and until when" question.

Grant it indefinitely:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"reason":"translator"}' "$BASE/api/admin/entitlements/users/42"
```

For a while — `{"days": 90}` — or to an exact instant — `{"until": "2027-01-01T00:00:00Z"}`.

Take it away:

```bash
curl -s -X DELETE -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/admin/entitlements/users/42"
```

Two rules worth knowing before you use these:

- **A grant never shortens what the account already has.** Granting a month to somebody
  who has a year leaves the year alone, whichever source gave it.
- **A revoke does not cancel a Play subscription.** If one is still live, the next hourly
  re-check grants premium straight back. Cancel it in the Play Console first, or refund it.

The Play sweep, unlike an administrator, can only revoke what Play granted — a manual
grant does not lapse when a subscription does. That is what `users.entitlement_source` is
for.
