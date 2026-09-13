-- V6: what a consent-based bank connection needs that a token-based one did not.
--
-- Only ANSI-ish DDL that both MySQL 8 and H2 (MODE=MySQL) accept, as V1's header requires.

-- Who the credentials belong to at the bank, as the bank identifies them.
--
-- A token connection is made once and edited in place, so the row was identity enough.
-- Consent is not: access is granted for a while, revoked in the bank's own app, and
-- granted again later, and each grant is a fresh reference. Without the bank's own id for
-- the customer, reconnecting cannot tell "the same Monobank account, new access" from "a
-- second Monobank account" - so it would leave a dead row behind holding every account
-- link the user had made.
ALTER TABLE bank_connections ADD COLUMN external_client_id VARCHAR(100) NULL;
CREATE INDEX idx_bank_conn_client ON bank_connections (user_id, provider, external_client_id);
