package br.com.carrinhoesperto.fiscal.dto;

import br.com.carrinhoesperto.fiscal.FiscalPayment;

import java.math.BigDecimal;
import java.util.UUID;

/** Client representation of a {@link FiscalPayment} — a payment line as reported. */
public record FiscalPaymentResponse(
    UUID id,
    int lineNumber,
    String method,
    String methodDescription,
    BigDecimal amount
) {

    public static FiscalPaymentResponse from(FiscalPayment payment) {
        return new FiscalPaymentResponse(
            payment.id(),
            payment.lineNumber(),
            payment.method(),
            payment.methodDescription(),
            payment.amount());
    }
}
