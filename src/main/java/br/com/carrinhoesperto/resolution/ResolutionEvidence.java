package br.com.carrinhoesperto.resolution;

import java.time.Instant;
import java.util.UUID;

/**
 * One auditable fact supporting a {@link ProductResolution} (M005 §9, §10).
 * Historical data — preserved once written, not a transient calculation cache.
 *
 * <p>M005 records exactly one item per {@code RESOLVED} resolution:
 * {@code type = GTIN, source = FISCAL_ITEM, value = <the 8..14-digit GTIN>}.
 * No scoring / contribution fields.
 */
public record ResolutionEvidence(
    UUID id,
    UUID resolutionId,
    ResolutionEvidenceType type,
    ResolutionEvidenceSource source,
    String value,
    Instant createdAt
) {
}
