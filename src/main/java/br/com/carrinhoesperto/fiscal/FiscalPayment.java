package br.com.carrinhoesperto.fiscal;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A payment line ({@code pag}/{@code detPag}) as reported by a
 * {@link FiscalDocument}.
 *
 * <p>This is only the payment information printed on the receipt — M002 does not
 * build a personal-finance or account domain (docs/fiscal-core.md §7).
 *
 * @param id                surrogate identifier
 * @param fiscalDocumentId  owning document
 * @param lineNumber        1-based position among the document's payment lines
 * @param method            {@code tPag} as reported (SEFAZ code or payload value)
 * @param methodDescription human label when the document provides one, else {@code null}
 * @param amount            {@code vPag} — amount paid with this method
 */
public record FiscalPayment(
    UUID id,
    UUID fiscalDocumentId,
    int lineNumber,
    String method,
    String methodDescription,
    BigDecimal amount
) {
}
