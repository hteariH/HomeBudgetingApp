-- V9: Multi-tagging and project/event tracking.
--
-- Lets users organize transactions with cross-cutting tags.

CREATE TABLE tags (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid       VARCHAR(36) NOT NULL UNIQUE,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    color      VARCHAR(20) NOT NULL DEFAULT '#6366F1',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_tag_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_tags_sync ON tags (user_id, updated_at);
CREATE INDEX idx_tags_user ON tags (user_id);

CREATE TABLE transaction_tags (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid           VARCHAR(36) NOT NULL UNIQUE,
    user_id        BIGINT      NOT NULL,
    transaction_id BIGINT      NOT NULL,
    tag_id         BIGINT      NOT NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted        BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_tt_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_tt_tx   FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_tt_tag  FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

CREATE INDEX idx_tt_sync ON transaction_tags (user_id, updated_at);
CREATE INDEX idx_tt_tx   ON transaction_tags (transaction_id);
CREATE INDEX idx_tt_tag  ON transaction_tags (tag_id);
