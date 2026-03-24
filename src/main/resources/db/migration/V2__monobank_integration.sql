-- V2: Monobank integration fields on accounts
ALTER TABLE accounts
    ADD COLUMN monobank_token      VARCHAR(255) NULL,
    ADD COLUMN monobank_account_id VARCHAR(100) NULL;

-- Monobank statement id on transactions for deduplication
ALTER TABLE transactions
    ADD COLUMN monobank_id VARCHAR(100) NULL;

CREATE TABLE category_mcc_codes (
                                    category_id BIGINT NOT NULL,
                                    mcc_code    INT    NOT NULL,
                                    PRIMARY KEY (category_id, mcc_code),
                                    CONSTRAINT fk_cat_mcc_category
                                        FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE
);
