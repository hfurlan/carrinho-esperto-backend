-- SQL for ResolutionEvidenceRepository (M005), loaded through ELSQL.
--
-- Evidence is historical/auditable. The service replaces a resolution's
-- evidence set (deleteByResolutionId + insert) only when the resolution's
-- outcome actually changes; an unchanged re-resolve touches nothing.

@NAME(insert)
  INSERT INTO resolution_evidence (id, resolution_id, type, source, value)
  VALUES (:id, :resolutionId, :type, :source, :value)

@NAME(deleteByResolutionId)
  DELETE FROM resolution_evidence
  WHERE resolution_id = :resolutionId

@NAME(findByResolutionId)
  SELECT id, resolution_id, type, source, value, created_at
  FROM resolution_evidence
  WHERE resolution_id = :resolutionId
  ORDER BY created_at ASC, id ASC
