-- V2: Monobank integration fields on accounts
ALTER TABLE accounts
    ADD COLUMN monobank_token      VARCHAR(255) NULL,
    ADD COLUMN monobank_account_id VARCHAR(100) NULL;

-- Monobank statement id on transactions for deduplication
ALTER TABLE transactions
    ADD COLUMN monobank_id VARCHAR(100) NULL;
