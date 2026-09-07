package br.com.carrinhoesperto.fiscal;

/**
 * The issuing business (emitente) as reported by a fiscal document.
 *
 * <p>Kept deliberately small (M002, docs/fiscal-core.md §5): just enough to
 * identify the issuer. The distinction between legal entity, supermarket chain
 * and physical store is <em>not</em> modelled here — the information needed to
 * solve it later (CNPJ + names + printed address) is preserved, nothing more.
 * {@code address} is the free-text address exactly as printed, not decomposed.
 *
 * @param cnpj      issuer CNPJ, digits only (14). Always present.
 * @param legalName razão social ({@code xNome}), or {@code null} when not reported
 * @param tradeName nome fantasia ({@code xFant}), or {@code null} when not reported
 * @param address   printed address as a single string, or {@code null}
 */
public record FiscalIssuer(String cnpj, String legalName, String tradeName, String address) {
}
