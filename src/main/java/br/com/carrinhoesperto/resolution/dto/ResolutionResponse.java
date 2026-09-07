package br.com.carrinhoesperto.resolution.dto;

import br.com.carrinhoesperto.resolution.ProductResolution;
import br.com.carrinhoesperto.resolution.ResolutionEvidence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Result of resolving (or reading the resolution of) a fiscal item (M005 §25,
 * §26).
 *
 * <p>An {@code UNRESOLVED} outcome is a normal {@code 200} response with
 * {@code canonicalProductId = null}, {@code method = null} and empty
 * {@code evidence} — it is not an error.
 */
public record ResolutionResponse(
    UUID resolutionId,
    UUID fiscalItemId,
    UUID canonicalProductId,
    String status,
    String method,
    List<ResolutionEvidenceResponse> evidence,
    Instant createdAt,
    Instant updatedAt
) {

    public static ResolutionResponse from(ProductResolution resolution, List<ResolutionEvidence> evidence) {
        return new ResolutionResponse(
            resolution.id(),
            resolution.fiscalItemId(),
            resolution.canonicalProductId(),
            resolution.status().name(),
            resolution.method() == null ? null : resolution.method().name(),
            evidence.stream().map(ResolutionEvidenceResponse::from).toList(),
            resolution.createdAt(),
            resolution.updatedAt());
    }
}
