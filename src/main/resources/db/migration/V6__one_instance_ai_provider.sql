-- V6: at most one INSTANCE row in ai_providers, enforced by the database.
-- H2 has no partial unique index, so the instance row carries instance_slot = 1 and every
-- household row NULL; a plain UNIQUE constraint lets any number of NULLs through.

-- Two administrators saving at the same moment could have left more than one; keep the latest.
DELETE FROM ai_providers
 WHERE scope = 'INSTANCE'
   AND id <> (SELECT MAX(id) FROM ai_providers WHERE scope = 'INSTANCE');

ALTER TABLE ai_providers ADD COLUMN instance_slot SMALLINT NULL;
UPDATE ai_providers SET instance_slot = 1 WHERE scope = 'INSTANCE';
ALTER TABLE ai_providers ADD CONSTRAINT uq_ai_provider_instance UNIQUE (instance_slot);
ALTER TABLE ai_providers ADD CONSTRAINT ck_ai_provider_instance_slot
    CHECK ((scope = 'INSTANCE' AND instance_slot = 1) OR (scope <> 'INSTANCE' AND instance_slot IS NULL));
