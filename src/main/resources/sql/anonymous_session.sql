-- SQL for AnonymousSessionRepository (M006.1), loaded through ELSQL.
--
-- An anonymous trial session is an `app_user` row with kind = 'ANONYMOUS' and no
-- Cognito subject (see V5__anonymous_session.sql). The raw session token is
-- never stored — only its SHA-256 hex digest.

@NAME(insert)
  INSERT INTO app_user (id, kind, anon_token_hash, last_seen_at)
  VALUES (:id, 'ANONYMOUS', :tokenHash, now())

-- Resolve a presented token (already hashed by the caller) to its app_user id.
@NAME(findIdByTokenHash)
  SELECT id
  FROM app_user
  WHERE kind = 'ANONYMOUS'
    AND anon_token_hash = :tokenHash

-- Bump activity for the future retention sweep. Write-path only.
@NAME(touchLastSeen)
  UPDATE app_user
     SET last_seen_at = now()
   WHERE id = :id
     AND kind = 'ANONYMOUS'
