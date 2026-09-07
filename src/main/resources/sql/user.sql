-- SQL for UserRepository, loaded through ELSQL (see config/ElSqlConfiguration).

@NAME(insertUserIfAbsent)
  INSERT INTO app_user (cognito_subject)
  VALUES (:cognitoSubject)
  ON CONFLICT (cognito_subject) DO NOTHING

-- Keep the display handle (Cognito `username` claim) current. No-op when
-- unchanged or when the claim is absent and the row is already null.
@NAME(updateUsername)
  UPDATE app_user
     SET username = :username
   WHERE cognito_subject = :cognitoSubject
     AND username IS DISTINCT FROM :username

@NAME(findByCognitoSubject)
  SELECT
      id,
      cognito_subject,
      created_at
  FROM app_user
  WHERE cognito_subject = :cognitoSubject
