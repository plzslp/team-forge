ALTER TABLE matches ADD COLUMN confirmation_key BINARY(16);
ALTER TABLE matches ADD COLUMN confirmation_fingerprint VARCHAR(64);
ALTER TABLE matches ADD CONSTRAINT uk_matches_confirmation_key UNIQUE (confirmation_key);
