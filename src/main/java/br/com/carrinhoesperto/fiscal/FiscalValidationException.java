package br.com.carrinhoesperto.fiscal;

/**
 * A fiscal document payload is not acceptable for storage: a missing or
 * malformed access key, a missing issuer CNPJ, an unparseable issue date, no
 * items, a negative quantity/price/total, and the like.
 *
 * <p>This is domain integrity, not fiscal certification (docs/fiscal-core.md
 * §16) — it does not verify NF-e check digits, tax math or SEFAZ authorization.
 * Maps to HTTP 400.
 */
public class FiscalValidationException extends RuntimeException {

    public FiscalValidationException(String message) {
        super(message);
    }
}
