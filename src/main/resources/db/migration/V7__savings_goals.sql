-- V7: Savings goals and sinking funds.
--
-- Lets users create, track, and fund savings targets with pacing calculations.

CREATE TABLE savings_goals (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid                 VARCHAR(36)   NOT NULL UNIQUE,
    user_id              BIGINT        NOT NULL,
    name                 VARCHAR(100)  NOT NULL,
    target_amount_minor  BIGINT        NOT NULL,
    currency_code        VARCHAR(10)   NOT NULL,
    current_amount_minor BIGINT        NOT NULL DEFAULT 0,
    target_date          DATE          NULL,
    linked_account_id    BIGINT        NULL,
    icon                 VARCHAR(50)   NOT NULL DEFAULT 'flag',
    color                VARCHAR(20)   NOT NULL DEFAULT '#10B981',
    archived             BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted              BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_goal_user           FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_goal_linked_account FOREIGN KEY (linked_account_id) REFERENCES accounts (id) ON DELETE SET NULL
);

CREATE INDEX idx_goals_sync ON savings_goals (user_id, updated_at);
CREATE INDEX idx_goals_user ON savings_goals (user_id);
