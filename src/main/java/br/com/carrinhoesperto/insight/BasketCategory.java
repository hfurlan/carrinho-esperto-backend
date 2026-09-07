package br.com.carrinhoesperto.insight;

/**
 * Coarse shopping-basket buckets used by the M006.1 basket insight
 * (docs/basket-insight.md).
 *
 * <p>These are a <strong>heuristic reading of the receipt text</strong>, not a
 * canonical product classification — Resolution and Catalog are untouched. Each
 * item is placed in exactly one bucket by {@link BasketClassifier}; items whose
 * description matches no rule are {@link #NAO_CLASSIFICADO}.
 *
 * <p>{@code superfluous} marks the buckets that count against the health seal
 * ({@link BasketSeal}).
 */
public enum BasketCategory {

    BASICO_IN_NATURA("Básicos in natura", false),
    ALIMENTO_PROCESSADO("Alimentos processados", false),
    BEBIDA_BAIXO_VALOR("Bebidas de baixo valor nutricional", true),
    DOCE_SUPERFLUO("Doces e supérfluos", true),
    LIMPEZA("Limpeza", false),
    HIGIENE("Higiene e cuidado pessoal", false),
    NAO_CLASSIFICADO("Não classificado", false);

    private final String label;
    private final boolean superfluous;

    BasketCategory(String label, boolean superfluous) {
        this.label = label;
        this.superfluous = superfluous;
    }

    /** Portuguese (Brazil) display label — the product UI is pt-BR. */
    public String label() {
        return label;
    }

    /** Whether value in this bucket counts as supérfluo for the health seal. */
    public boolean superfluous() {
        return superfluous;
    }
}
