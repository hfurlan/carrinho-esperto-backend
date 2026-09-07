package br.com.carrinhoesperto.fiscal.dto;

/**
 * Issuer block of {@link FiscalDocumentRequest}. Only {@code cnpj} is required;
 * it is normalized to digits only. The address is a single free-text string,
 * stored as printed (M002 does not decompose addresses).
 */
public record FiscalIssuerRequest(
    String cnpj,
    String legalName,
    String tradeName,
    String address
) {
}
