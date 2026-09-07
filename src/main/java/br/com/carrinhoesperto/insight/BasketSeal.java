package br.com.carrinhoesperto.insight;

/**
 * The qualitative "how healthy is this basket" seal shown after a receipt is
 * read (M006.1, docs/basket-insight.md). Deliberately coarse — it is a nudge,
 * not a nutrition score.
 */
public enum BasketSeal {

    EQUILIBRADO("Equilibrado"),
    ATENCAO("Atenção"),
    MUITOS_SUPERFLUOS("Muitos supérfluos"),
    /** Too little of the basket could be classified to say anything useful. */
    SEM_DADOS("Sem dados suficientes");

    private final String label;

    BasketSeal(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
