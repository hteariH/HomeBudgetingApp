-- V4: what the HomeMoney importer needs to be re-runnable.
--
-- Only ANSI-ish DDL that both MySQL 8 and H2 (MODE=MySQL) accept, as V1's header requires.

-- Categories gain the external id that transactions and accounts already had. Without
-- it a second import cannot tell "the Groceries I created last time" from "a Groceries
-- the user made themselves", and re-running would either duplicate the tree or adopt
-- rows it did not create.
ALTER TABLE categories ADD COLUMN external_id VARCHAR(100) NULL;
CREATE INDEX idx_categories_external ON categories (user_id, external_id);

-- Dedup by external id had no index at all. On a 50-line bank statement that is
-- invisible; on an import of ten years of history it is one table scan per row, which
-- turns the whole migration quadratic.
CREATE INDEX idx_tx_external ON transactions (user_id, external_id);
