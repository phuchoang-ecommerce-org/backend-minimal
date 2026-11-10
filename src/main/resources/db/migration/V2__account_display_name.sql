ALTER TABLE marketplace.account
  ADD COLUMN display_name text;

UPDATE marketplace.account SET display_name = left(normalized_login, 100);

ALTER TABLE marketplace.account ALTER COLUMN display_name SET NOT NULL;

ALTER TABLE marketplace.account
  ADD CONSTRAINT account_display_name_valid CHECK (length(btrim(display_name)) BETWEEN 1 AND 100);
