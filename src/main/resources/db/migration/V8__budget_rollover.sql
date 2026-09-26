-- V8: Budget rollover and custom paycheck cycle start day.
--
-- Adds rollover settings to categories and budget_cycle_start_day to users.

ALTER TABLE categories ADD COLUMN rollover_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE categories ADD COLUMN rollover_negative BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users ADD COLUMN budget_cycle_start_day INT NOT NULL DEFAULT 1;
