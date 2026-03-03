-- V1: Initial Schema for Home Budgeting App (MariaDB / MySQL)

CREATE TABLE IF NOT EXISTS currencies (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(3)     NOT NULL UNIQUE,
    name                VARCHAR(100)   NOT NULL,
    symbol              VARCHAR(10)    NOT NULL,
    exchange_rate_to_base DECIMAL(18,6) NOT NULL DEFAULT 1.000000,
    is_base             BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS accounts (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)   NOT NULL,
    type        VARCHAR(20)    NOT NULL,
    balance     DECIMAL(18,2)  NOT NULL DEFAULT 0.00,
    currency_id BIGINT         NOT NULL,
    description VARCHAR(500),
    color       VARCHAR(20)    NOT NULL DEFAULT '#4F46E5',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_currency FOREIGN KEY (currency_id) REFERENCES currencies(id)
);

CREATE TABLE IF NOT EXISTS categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)   NOT NULL,
    type        VARCHAR(10)    NOT NULL,
    icon        VARCHAR(50)    NOT NULL DEFAULT 'tag',
    color       VARCHAR(20)    NOT NULL DEFAULT '#6366F1',
    description VARCHAR(500),
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_category_name_type UNIQUE (name, type)
);

CREATE TABLE IF NOT EXISTS transactions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id  BIGINT        NOT NULL,
    category_id BIGINT        NOT NULL,
    amount      DECIMAL(18,2) NOT NULL,
    type        VARCHAR(10)   NOT NULL,
    date        DATE          NOT NULL,
    description VARCHAR(500),
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_account  FOREIGN KEY (account_id)  REFERENCES accounts(id),
    CONSTRAINT fk_transaction_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE TABLE IF NOT EXISTS transfers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_account_id BIGINT         NOT NULL,
    to_account_id   BIGINT         NOT NULL,
    from_amount     DECIMAL(18,2)  NOT NULL,
    to_amount       DECIMAL(18,2)  NOT NULL,
    exchange_rate   DECIMAL(18,6)  NOT NULL DEFAULT 1.000000,
    date            DATE           NOT NULL,
    description     VARCHAR(500),
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfer_from FOREIGN KEY (from_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_transfer_to   FOREIGN KEY (to_account_id)   REFERENCES accounts(id)
);
