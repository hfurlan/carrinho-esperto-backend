-- Local mirror of an authenticated Cognito identity.
--
-- cognito_subject is the Cognito `sub` claim: the stable external identity key.
-- It is the only identity attribute persisted for the starter — email, name,
-- roles, memberships and organizations are deliberately out of scope until a
-- real need shows up. "user" is a reserved word in SQL, hence the table name.
CREATE TABLE app_user (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    cognito_subject TEXT        NOT NULL,
    username        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT app_user_cognito_subject_key UNIQUE (cognito_subject)
);
