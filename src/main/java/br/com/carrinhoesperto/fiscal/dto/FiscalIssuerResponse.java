package br.com.carrinhoesperto.fiscal.dto;

import br.com.carrinhoesperto.fiscal.FiscalIssuer;

/** Client representation of a {@link FiscalIssuer} — the issuer as reported. */
public record FiscalIssuerResponse(
    String cnpj,
    String legalName,
    String tradeName,
    String address
) {

    public static FiscalIssuerResponse from(FiscalIssuer issuer) {
        return new FiscalIssuerResponse(
            issuer.cnpj(), issuer.legalName(), issuer.tradeName(), issuer.address());
    }
}
