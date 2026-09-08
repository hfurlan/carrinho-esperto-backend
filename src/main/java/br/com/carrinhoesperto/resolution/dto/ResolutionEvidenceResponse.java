package br.com.carrinhoesperto.resolution.dto;

import br.com.carrinhoesperto.resolution.ResolutionEvidence;

/**
 * One piece of auditable evidence in a {@link ResolutionResponse}.
 * {@code {type: "GTIN", source: "FISCAL_ITEM", value: "<gtin>", metadata: null}}
 * or (M007) {@code {type: "STORE_PRODUCT_CODE", source: "FISCAL_ITEM",
 * value: "<cProd>", metadata: "<store CNPJ>"}}.
 */
public record ResolutionEvidenceResponse(String type, String source, String value, String metadata) {

    public static ResolutionEvidenceResponse from(ResolutionEvidence evidence) {
        return new ResolutionEvidenceResponse(
            evidence.type().name(), evidence.source().name(), evidence.value(), evidence.metadata());
    }
}
