package br.com.carrinhoesperto.fiscal.dto;

import java.math.BigDecimal;

/**
 * One payment line of {@link FiscalDocumentRequest}. Only the method and the
 * amount are modelled (docs/fiscal-core.md §7).
 *
 * @param method            {@code tPag} as reported (SEFAZ code or payload value); required
 * @param methodDescription optional human label as printed on the receipt
 * @param amount            {@code vPag}; required, {@code >= 0}
 */
public record FiscalPaymentRequest(
    String method,
    String methodDescription,
    BigDecimal amount
) {
}
