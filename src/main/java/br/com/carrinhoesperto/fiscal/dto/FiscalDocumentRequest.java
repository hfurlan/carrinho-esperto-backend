package br.com.carrinhoesperto.fiscal.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body of {@code POST /api/fiscal-documents} (M002).
 *
 * <p>A structured fiscal-document payload. M002 has no NFC-e retrieval or
 * parsing (that is M003) — a caller (a test, or a future provider) supplies the
 * already-structured document and the API persists it as fiscal truth without
 * interpreting the products.
 *
 * <p>Owner is never in this payload: it is always the authenticated principal.
 * Re-submitting the same {@code accessKey} is idempotent — it returns the
 * already-stored document instead of creating a second one.
 *
 * @param accessKey        NFC-e chave de acesso; required. Non-digits are stripped; must be 44 digits.
 * @param documentNumber   {@code nNF}; optional
 * @param series           {@code serie}; optional
 * @param issuedAt         {@code dhEmi} as an ISO-8601 string (offset or {@code Z}); required
 * @param issuer           issuing business; {@code issuer.cnpj} is required
 * @param totalAmount      {@code vNF}; required, {@code >= 0}
 * @param totalItemsAmount {@code vProd}; optional, {@code >= 0}
 * @param totalDiscount    {@code vDesc}; optional, {@code >= 0}
 * @param items            at least one line
 * @param payments         payment lines; may be empty
 */
public record FiscalDocumentRequest(
    String accessKey,
    Long documentNumber,
    Integer series,
    String issuedAt,
    FiscalIssuerRequest issuer,
    BigDecimal totalAmount,
    BigDecimal totalItemsAmount,
    BigDecimal totalDiscount,
    List<FiscalItemRequest> items,
    List<FiscalPaymentRequest> payments
) {
}
