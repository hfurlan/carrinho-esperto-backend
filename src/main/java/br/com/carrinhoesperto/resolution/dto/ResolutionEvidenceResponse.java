package br.com.carrinhoesperto.resolution.dto;

import br.com.carrinhoesperto.resolution.ResolutionEvidence;

/**
 * One piece of auditable evidence in a {@link ResolutionResponse}. M005 only
 * ever emits {@code {type: "GTIN", source: "FISCAL_ITEM", value: "<gtin>"}}.
 */
public record ResolutionEvidenceResponse(String type, String source, String value) {

    public static ResolutionEvidenceResponse from(ResolutionEvidence evidence) {
        return new ResolutionEvidenceResponse(
            evidence.type().name(), evidence.source().name(), evidence.value());
    }
}
