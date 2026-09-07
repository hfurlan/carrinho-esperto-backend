package br.com.carrinhoesperto.fiscal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A fiscal document (NFC-e) as reported by a consultation, and the aggregate
 * root of the Fiscal bounded context (docs/architecture.md §5, docs/fiscal-core.md).
 *
 * <p>This is <strong>source truth</strong>: every field mirrors what the fiscal
 * document reports. Nothing here is a Carrinho Esperto interpretation — product
 * canonicalization lives in the future Resolution/Catalog contexts and never
 * touches these rows.
 *
 * @param id               surrogate identifier
 * @param ownerUserId      the {@code app_user} who imported it; always the authenticated principal
 * @param accessKey        NFC-e chave de acesso, normalized to 44 digits — the natural fiscal identity
 * @param documentNumber   {@code nNF}, or {@code null} when the payload omits it
 * @param series           {@code serie}, or {@code null} when the payload omits it
 * @param issuedAt         {@code dhEmi} — when the document was issued
 * @param issuer           issuing business as reported ({@link FiscalIssuer})
 * @param totalAmount      {@code vNF} — total value of the document
 * @param totalItemsAmount {@code vProd} — total merchandise value, or {@code null} when not reported
 * @param totalDiscount    {@code vDesc} — total discount, or {@code null} when not reported
 * @param createdAt        row creation timestamp
 */
public record FiscalDocument(
    UUID id,
    UUID ownerUserId,
    String accessKey,
    Long documentNumber,
    Integer series,
    Instant issuedAt,
    FiscalIssuer issuer,
    BigDecimal totalAmount,
    BigDecimal totalItemsAmount,
    BigDecimal totalDiscount,
    Instant createdAt
) {
}
