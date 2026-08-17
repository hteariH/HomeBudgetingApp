-- V1: Home Budgeting schema.
--
-- This is the canonical model shared by the React client and the Android client.
-- Three conventions run through the whole thing and explain most of the column choices:
--
--   Money is an integer count of a currency's smallest unit ("minor units"), never a
--   decimal. The exponent lives in currencies.decimals, because a third of the catalog
--   is not two-decimal: JPY and KRW have no minor unit, the Gulf dinars have three
--   digits, BTC has eight. Both clients store money this way, so neither has to round.
--
--   Account balances are derived, never stored. A stored running total has to be
--   mutated by every write, and an offline client replaying its outbox applies the
--   same delta twice and drifts silently. `initial_balance_minor` is the opening
--   figure; the current balance is summed from the ledger on read, which is idempotent.
--
--   Every row a client can own carries `uuid`, `updated_at` and `deleted`. The uuid is
--   minted by whichever side creates the row, so a phone can create a transaction that
--   references an account the server has never seen and both arrive in one push.
--   `deleted` is a tombstone: a hard delete is invisible to a client that was offline
--   when it happened, so deletions have to be something you can *replicate*.
--
-- Only ANSI-ish DDL that both MySQL 8 and H2 (MODE=MySQL, used by the local and test
-- profiles) accept. Notably absent: column type changes, nullability changes and
-- RENAME COLUMN, which all spell differently in the two engines.

-- ── Currencies ──────────────────────────────────────────────────────────────
-- The catalog is global and admin-managed, so it is not synced and needs no uuid.
CREATE TABLE currencies (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- 10 rather than 3: the catalog carries 4-character tickers (USDT, USDC).
    code                  VARCHAR(10)   NOT NULL UNIQUE,
    name                  VARCHAR(100)  NOT NULL,
    symbol                VARCHAR(10)   NOT NULL,
    -- Digits after the decimal point, and the exponent for minor units.
    decimals              INT           NOT NULL DEFAULT 2,
    sort_order            INT           NOT NULL DEFAULT 0,
    -- Legacy single-rate FX, read by the React client until it moves to
    -- exchange_rates. Nothing new should be built on these two columns.
    exchange_rate_to_base DECIMAL(18,6) NOT NULL DEFAULT 1.000000,
    is_base               BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Users ───────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    role             VARCHAR(20)  NOT NULL DEFAULT 'USER',
    email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    base_currency_id BIGINT,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_base_currency FOREIGN KEY (base_currency_id) REFERENCES currencies (id)
);

-- Long-lived refresh tokens. Stored hashed: a leaked table must not hand out sessions.
CREATE TABLE refresh_tokens (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at DATETIME(3)  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);

-- Email verification and password reset share one table: same shape, same lifecycle,
-- and `purpose` is the only thing that differs. Only the SHA-256 is stored, for the
-- same reason as refresh tokens - a leaked table must not be a set of working links.
CREATE TABLE auth_tokens (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    purpose    VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME(3) NOT NULL,
    used_at    DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_auth_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_auth_token_user ON auth_tokens (user_id, purpose);

-- ── Bank connections ────────────────────────────────────────────────────────
-- One set of credentials per bank, not per account: a single Monobank token covers
-- every card and jar the customer has, so storing it on each linked account would
-- duplicate the same secret and leave copies behind when it is revoked.
--
-- `provider` selects the BankProvider implementation; `credentials` is its opaque,
-- encrypted payload, so a bank needing a client id and secret instead of one token
-- costs no schema change. These rows are deliberately NOT part of the sync protocol -
-- bank credentials stay on the server and never reach a device.
CREATE TABLE bank_connections (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(30)  NOT NULL,
    -- Encrypted JSON of the provider's credential fields.
    credentials      VARCHAR(2048) NOT NULL,
    -- What the bank calls this customer, shown so several connections are tellable apart.
    label            VARCHAR(120) NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_verified_at DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_conn_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_bank_conn_user ON bank_connections (user_id);

-- Which slice of the global catalog a user sees, and in what order.
CREATE TABLE user_currency_settings (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid        VARCHAR(36) NOT NULL UNIQUE,
    user_id     BIGINT      NOT NULL,
    currency_id BIGINT      NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order  INT         NOT NULL DEFAULT 0,
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_ucs_user     FOREIGN KEY (user_id)     REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ucs_currency FOREIGN KEY (currency_id) REFERENCES currencies (id),
    CONSTRAINT uq_ucs_user_currency UNIQUE (user_id, currency_id)
);

CREATE INDEX idx_ucs_sync ON user_currency_settings (user_id, updated_at);

-- user_id IS NULL is a globally fetched rate (Frankfurter); a populated user_id is
-- that user's manual override of the same pair. Rates are refetchable, so they are
-- not part of the sync protocol.
CREATE TABLE exchange_rates (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_code  VARCHAR(10)    NOT NULL,
    quote_code VARCHAR(10)    NOT NULL,
    rate       DECIMAL(24,10) NOT NULL,
    rate_date  DATE           NOT NULL,
    source     VARCHAR(20)    NOT NULL,
    user_id    BIGINT         NULL,
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fx_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_fx_pair ON exchange_rates (base_code, quote_code, rate_date);

-- ── Accounts ────────────────────────────────────────────────────────────────
CREATE TABLE accounts (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid                  VARCHAR(36)  NOT NULL UNIQUE,
    user_id               BIGINT       NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    type                  VARCHAR(20)  NOT NULL,
    -- Opening balance. The current balance is initial + SUM(ledger); see the header.
    initial_balance_minor BIGINT       NOT NULL DEFAULT 0,
    currency_id           BIGINT       NOT NULL,
    description           VARCHAR(500),
    icon                  VARCHAR(50)  NOT NULL DEFAULT 'wallet',
    color                 VARCHAR(20)  NOT NULL DEFAULT '#4F46E5',
    archived              BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order            INT          NOT NULL DEFAULT 0,
    -- Lets a user keep an account on the books but out of the net-worth figure.
    include_in_total      BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Which bank connection feeds this account, and the bank's own id for it.
    bank_connection_id    BIGINT       NULL,
    external_account_id   VARCHAR(100) NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_account_user     FOREIGN KEY (user_id)     REFERENCES users (id),
    CONSTRAINT fk_account_currency FOREIGN KEY (currency_id) REFERENCES currencies (id),
    CONSTRAINT fk_account_bank_conn FOREIGN KEY (bank_connection_id) REFERENCES bank_connections (id) ON DELETE SET NULL
);

CREATE INDEX idx_accounts_sync     ON accounts (user_id, updated_at);
CREATE INDEX idx_accounts_archived ON accounts (archived);

-- ── Categories ──────────────────────────────────────────────────────────────
-- Deliberately no UNIQUE (user_id, name, type): sub-categories under different
-- parents legitimately share a name ("Groceries" under both Food and Household),
-- and a uniqueness failure during a sync push is much worse than a duplicate name.
-- CategoryService still rejects same-name siblings coming in through the REST API.
CREATE TABLE categories (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid               VARCHAR(36)  NOT NULL UNIQUE,
    user_id            BIGINT       NOT NULL,
    name               VARCHAR(100) NOT NULL,
    type               VARCHAR(10)  NOT NULL,
    icon               VARCHAR(50)  NOT NULL DEFAULT 'tag',
    color              VARCHAR(20)  NOT NULL DEFAULT '#6366F1',
    description        VARCHAR(500),
    parent_id          BIGINT       NULL,
    archived           BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order         INT          NOT NULL DEFAULT 0,
    is_default         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Monthly spending limit in the user's base currency, expense categories only.
    budget_limit_minor BIGINT       NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_category_user   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
);

CREATE INDEX idx_categories_sync     ON categories (user_id, updated_at);
CREATE INDEX idx_categories_parent   ON categories (parent_id);
CREATE INDEX idx_categories_archived ON categories (archived);

-- MCC codes learned from imported card transactions, used to suggest a category.
CREATE TABLE category_mcc_codes (
    category_id BIGINT NOT NULL,
    mcc_code    INT    NOT NULL,
    PRIMARY KEY (category_id, mcc_code),
    CONSTRAINT fk_cat_mcc_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE
);

-- ── Subscriptions ───────────────────────────────────────────────────────────
-- Recurring charges. A scheduled job walks next_charge_date forward and writes a
-- transaction per due charge, stamping it with subscription_id.
CREATE TABLE subscriptions (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid                    VARCHAR(36)  NOT NULL UNIQUE,
    user_id                 BIGINT       NOT NULL,
    name                    VARCHAR(100) NOT NULL,
    account_id              BIGINT       NOT NULL,
    category_id             BIGINT       NULL,
    amount_minor            BIGINT       NOT NULL,
    currency_code           VARCHAR(10)  NOT NULL,
    frequency               VARCHAR(20)  NOT NULL,
    interval_count          INT          NOT NULL DEFAULT 1,
    next_charge_date        DATE         NOT NULL,
    start_date              DATE         NOT NULL,
    end_date                DATE         NULL,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    reminder_minutes_before INT          NULL,
    icon                    VARCHAR(50)  NOT NULL DEFAULT 'subscriptions',
    color                   VARCHAR(20)  NOT NULL DEFAULT '#008080',
    note                    VARCHAR(500) NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted                 BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_sub_user     FOREIGN KEY (user_id)     REFERENCES users (id),
    CONSTRAINT fk_sub_account  FOREIGN KEY (account_id)  REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE INDEX idx_sub_sync   ON subscriptions (user_id, updated_at);
CREATE INDEX idx_sub_next   ON subscriptions (next_charge_date);
CREATE INDEX idx_sub_active ON subscriptions (active);

-- ── Transactions ────────────────────────────────────────────────────────────
-- A transfer is *two* rows sharing a transfer_group_id: a negative debit on the
-- source account and a positive credit on the destination. That keeps one entity
-- type for the sync protocol, one SUM for the balance, and makes cross-currency
-- transfers fall out naturally (each row carries its own account_amount_minor).
--
-- Sign convention, matching the Android client's balance query:
--   INCOME   -> account_amount_minor is a positive magnitude, added
--   EXPENSE  -> account_amount_minor is a positive magnitude, subtracted
--   TRANSFER -> account_amount_minor is already signed, added as-is
CREATE TABLE transactions (
    id                              BIGINT         AUTO_INCREMENT PRIMARY KEY,
    uuid                            VARCHAR(36)    NOT NULL UNIQUE,
    user_id                         BIGINT         NOT NULL,
    account_id                      BIGINT         NOT NULL,
    -- Nullable: transfer rows have no category, and a deleted category must not
    -- take its history with it.
    category_id                     BIGINT         NULL,
    -- Amount as entered, in the currency below.
    amount_minor                    BIGINT         NOT NULL,
    currency_code                   VARCHAR(10)    NOT NULL,
    -- The same amount converted into the account's currency; equal to amount_minor
    -- unless the entry was made in a different currency.
    account_amount_minor            BIGINT         NOT NULL,
    fx_rate_to_account              DECIMAL(24,10) NOT NULL DEFAULT 1,
    type                            VARCHAR(10)    NOT NULL,
    -- An instant, not a date: the Android client orders same-day entries by time.
    occurred_at                     DATETIME(3)    NOT NULL,
    description                     VARCHAR(500),
    subscription_id                 BIGINT         NULL,
    transfer_group_id               VARCHAR(36)    NULL,
    transfer_counterpart_account_id BIGINT         NULL,
    transfer_fee_minor              BIGINT         NULL,
    -- The bank's own id for this operation, used to avoid importing it twice.
    external_id                     VARCHAR(100)   NULL,
    created_at                      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted                         BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_tx_user         FOREIGN KEY (user_id)         REFERENCES users (id),
    CONSTRAINT fk_tx_account      FOREIGN KEY (account_id)      REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_tx_category     FOREIGN KEY (category_id)     REFERENCES categories (id),
    CONSTRAINT fk_tx_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
);

CREATE INDEX idx_tx_sync           ON transactions (user_id, updated_at);
CREATE INDEX idx_tx_account        ON transactions (account_id);
CREATE INDEX idx_tx_category       ON transactions (category_id);
CREATE INDEX idx_tx_occurred_at    ON transactions (occurred_at);
CREATE INDEX idx_tx_transfer_group ON transactions (transfer_group_id);
CREATE INDEX idx_tx_subscription   ON transactions (subscription_id);
