package br.com.carrinhoesperto.dashboard.dto;

import java.math.BigDecimal;

/**
 * The caller's spending at one issuing supermarket.
 *
 * @param issuerCnpj   issuer CNPJ (digits only)
 * @param issuerName   trade name, else legal name, else the CNPJ
 * @param total        summed receipt total for the issuer
 * @param receiptCount receipts from the issuer
 */
public record IssuerSpendingResponse(String issuerCnpj, String issuerName, BigDecimal total, long receiptCount) {
}
