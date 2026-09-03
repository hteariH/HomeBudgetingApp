-- Subscription state, and a stable public identifier a payment provider can key on.
--
-- Two things arrive together because they are two halves of the same feature. The provider
-- needs a name for the customer, and the server needs somewhere to write down what the
-- provider says about them.

-- The identifier RevenueCat is told to call this customer (`Purchases.logIn(uuid)`).
-- Deliberately not the primary key: an auto-increment id tells anyone who sees it roughly
-- how many accounts exist and in what order they were created, and it is the wrong thing
-- to hand to a third party. Added nullable, backfilled, then tightened - the constraint
-- can only go on once every existing row can satisfy it.
ALTER TABLE users ADD COLUMN uuid VARCHAR(36);
UPDATE users SET uuid = UUID() WHERE uuid IS NULL;
ALTER TABLE users MODIFY COLUMN uuid VARCHAR(36) NOT NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_uuid UNIQUE (uuid);

-- What the payment provider last told us, and until when.
--
-- The server holds this rather than trusting a client to report its own subscription,
-- which is the whole reason the webhook exists: a client that can claim to be premium is
-- not a limit, it is a suggestion. An expiry in the past means the entitlement has lapsed;
-- NULL with PREMIUM means it does not expire (a lifetime or promotional grant).
ALTER TABLE users ADD COLUMN entitlement VARCHAR(20) NOT NULL DEFAULT 'FREE';
ALTER TABLE users ADD COLUMN entitlement_expires_at DATETIME(3) NULL;
