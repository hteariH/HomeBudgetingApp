-- V5: the Play subscriptions this server has verified.
--
-- Supersedes V3's note about RevenueCat: purchases are verified against the Google Play
-- Developer API directly, with no third party in between. V3's own comment is left as it
-- was because Flyway checksums an applied migration and rewriting one breaks validation
-- on every deployment that already ran it.
--
-- Only ANSI-ish DDL that both MySQL 8 and H2 (MODE=MySQL) accept, as V1's header requires.
--
-- The row exists for two reasons that the entitlement columns on `users` cannot serve.
-- One: a purchase token has to be re-checked later, because a renewal a month from now
-- produces no request from the phone and the entitlement would otherwise lapse on a
-- subscription that is being paid for. Two: a token belongs to exactly one account, and
-- without somewhere to write that down the same purchase could be replayed against every
-- account it is pasted into.
CREATE TABLE play_purchases (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    -- The token itself is far too long to index: Play tokens run to hundreds of
    -- characters, and utf8mb4 puts a unique index over one past MySQL's 3072-byte key
    -- limit. The SHA-256 of it carries the uniqueness instead - the same trick
    -- `refresh_tokens` already uses.
    purchase_token VARCHAR(1024) NOT NULL,
    token_hash     VARCHAR(64)  NOT NULL UNIQUE,
    product_id     VARCHAR(191) NOT NULL,
    order_id       VARCHAR(191) NULL,
    -- Google's own SUBSCRIPTION_STATE_* string, stored verbatim rather than mapped to an
    -- enum of ours: it is a diagnostic, and a state Google adds later should show up in
    -- the table rather than fail to deserialise.
    state          VARCHAR(64)  NOT NULL,
    expires_at     DATETIME(3)  NULL,
    auto_renewing  BOOLEAN      NOT NULL DEFAULT FALSE,
    acknowledged   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     DATETIME(3)  NOT NULL,
    checked_at     DATETIME(3)  NOT NULL,
    CONSTRAINT fk_play_purchase_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- The nightly re-check walks purchases by expiry, and the app asks for one user's rows.
CREATE INDEX idx_play_purchases_user ON play_purchases (user_id);
CREATE INDEX idx_play_purchases_expiry ON play_purchases (expires_at);

-- Which of the two doors an entitlement came through.
--
-- Needed because they must not undo each other: the nightly Play re-check has to be able
-- to take premium away when a subscription lapses, and without this it would also take
-- away one an administrator handed out by hand. NONE is what a FREE account carries;
-- MANUAL and PLAY name the source that granted the current entitlement.
ALTER TABLE users ADD COLUMN entitlement_source VARCHAR(20) NOT NULL DEFAULT 'NONE';
