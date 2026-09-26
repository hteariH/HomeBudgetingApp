-- V6: Transaction splits.
--
-- Lets a user split a single transaction across multiple categories without
-- altering the parent transaction amount or account balance.
-- Invariant enforced on sync and REST: SUM(splits.amount_minor) == transaction.amount_minor.

CREATE TABLE transaction_splits (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid           VARCHAR(36)  NOT NULL UNIQUE,
    user_id        BIGINT       NOT NULL,
    transaction_id BIGINT       NOT NULL,
    category_id    BIGINT       NULL,
    amount_minor   BIGINT       NOT NULL,
    note           VARCHAR(500) NULL,
    sort_order     INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_split_user     FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_split_tx       FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_split_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE INDEX idx_splits_sync ON transaction_splits (user_id, updated_at);
CREATE INDEX idx_splits_tx   ON transaction_splits (transaction_id);
