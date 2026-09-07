-- M006.1 — Anonymous Trial (docs/anonymous-trial.md).
--
-- The first experience of Carrinho Esperto must not require a Cognito account:
-- a visitor can import an NFC-e, inspect the receipt and see basket insight
-- while their data is scoped to the current browser session. The smallest
-- coherent way to support that, without touching the Fiscal ownership model, is
-- to let an `app_user` row represent EITHER a Cognito identity OR an anonymous
-- trial session. Every owner-scoped query already keys on `app_user.id`, so it
-- keeps working unchanged.
--
-- RETENTION (M006.1 decision): anonymous data is session-oriented and NOT
-- persistent user data. `last_seen_at` is recorded so a future milestone can
-- purge anonymous sessions after ~30 days of inactivity. M006.1 builds no
-- cleanup job and enforces no TTL.

-- Discriminates the two kinds of identity a row can hold.
ALTER TABLE app_user ADD COLUMN kind TEXT NOT NULL DEFAULT 'AUTHENTICATED';
-- Existing rows are all real Cognito identities; new rows must state their kind.
ALTER TABLE app_user ALTER COLUMN kind DROP DEFAULT;
ALTER TABLE app_user ADD CONSTRAINT app_user_kind_check
    CHECK (kind IN ('AUTHENTICATED', 'ANONYMOUS'));

-- An anonymous session has no Cognito subject.
ALTER TABLE app_user ALTER COLUMN cognito_subject DROP NOT NULL;

-- SHA-256 (hex) of the opaque session token handed to the browser. The raw
-- token is never stored — it is a bearer credential for that session's data.
ALTER TABLE app_user ADD COLUMN anon_token_hash TEXT;
ALTER TABLE app_user ADD CONSTRAINT app_user_anon_token_hash_key UNIQUE (anon_token_hash);

-- Last time this identity was seen. Populated on creation and on each successful
-- anonymous import; the basis for a future retention sweep.
ALTER TABLE app_user ADD COLUMN last_seen_at TIMESTAMPTZ;

-- Exactly one identity per row, matching `kind`.
ALTER TABLE app_user ADD CONSTRAINT app_user_identity_check CHECK (
    (kind = 'AUTHENTICATED' AND cognito_subject IS NOT NULL AND anon_token_hash IS NULL)
    OR
    (kind = 'ANONYMOUS' AND anon_token_hash IS NOT NULL AND cognito_subject IS NULL)
);
