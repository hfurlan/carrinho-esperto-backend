package br.com.carrinhoesperto.insight;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Places a fiscal item into a {@link BasketCategory} from its raw description
 * (M006.1, docs/basket-insight.md).
 *
 * <p>Deterministic and transparent: an ordered list of Portuguese keyword rules,
 * matched at a word boundary against a normalized (upper-case, accent-stripped,
 * whitespace-collapsed) description. First match wins; no match →
 * {@link BasketCategory#NAO_CLASSIFICADO}. Coverage is intentionally partial and
 * the ruleset is meant to grow — {@link BasketSeal#SEM_DADOS} guards against
 * drawing conclusions when too little of a basket is recognised.
 *
 * <p>This does <em>not</em> use the canonical catalog, NCM codes or any model —
 * it is a heuristic on fiscal text and never rewrites it.
 */
@Component
public class BasketClassifier {

    /**
     * Order matters — a specific sweet/beverage rule must beat a generic staple
     * rule ("LEITE CONDENSADO" before "LEITE", "OVOS DE PASCOA" before "OVO").
     */
    private static final List<Rule> RULES = List.of(
        // ── Doces & supérfluos ──────────────────────────────────────────────
        rule(BasketCategory.DOCE_SUPERFLUO,
            "CHOCOLATE", "BOMBOM", "BIS ", "KITKAT", "KIT KAT", "OVOS DE PASCOA", "OVO DE PASCOA",
            "BALA ", "PIRULITO", "CHICLETE", "JUJUBA", "PACOCA", "PACOQUINHA", "BRIGADEIRO",
            "DOCE DE LEITE", "LEITE CONDENSADO", "LEITE COND", "GELEIA", "GOIABADA", "MARMELADA",
            "BISCOITO RECHEADO", "BOLACHA RECHEADA", "OREO", "TRAKINAS", "NEGRESCO", "PASSATEMPO",
            "WAFER", "ROSQUINHA", "COOKIE", "BOLO ", "BOLINHO", "PANETONE", "CHOCOTONE",
            "SORVETE", "PICOLE", "CHANTILLY", "PUDIM", "GELATINA", "PACOCA",
            "NUTELLA", "CREME DE AVELA", "ACHOCOLATADO", "NESCAU", "TODDY", "OVOMALTINE",
            "SUCRILHOS", "CEREAL MATINAL", "SALGADINHO", "DORITOS", "CHEETOS", "RUFFLES",
            "FANDANGOS", "TORCIDA", "BATATA PALHA", "PIPOCA", "AMENDOIM JAPONES", "PACOCA",
            "PAO DE MEL", "DONUTS", "ACUCAR"),
        // ── Bebidas de baixo valor nutricional ──────────────────────────────
        rule(BasketCategory.BEBIDA_BAIXO_VALOR,
            "REFRIGERANTE", "REFRIG", "COCA COLA", "COCA-COLA", "COCA ", "PEPSI", "GUARANA",
            "FANTA", "SPRITE", "SUKITA", "TUBAINA", "DOLLY", "SODA LIMONADA", "H2OH", "SCHWEPPES",
            "SUCO EM PO", "REFRESCO EM PO", "TANG", "FRESH ", "MID ", "TAMPICO", "SUCO NECTAR",
            "NECTAR ", "ENERGETICO", "RED BULL", "MONSTER ", "BALY", "ISOTONICO", "GATORADE",
            "POWERADE", "CHA GELADO", "CHA MATE", "ICE TEA", "CERVEJA", "CHOPP", "VINHO",
            "ESPUMANTE", "VODKA", "CACHACA", "WHISKY", "GIN ", "RUM ", "LICOR", "APERITIVO"),
        // ── Limpeza ─────────────────────────────────────────────────────────
        rule(BasketCategory.LIMPEZA,
            "DETERGENTE", "SABAO", "LAVA ROUPA", "AMACIANTE", "AGUA SANITARIA", "AGUA SANIT",
            "CANDIDA", "QBOA", "CLORO", "DESINFETANTE", "MULTIUSO", "LUSTRA MOVEIS", "LIMPA VIDRO",
            "LIMPADOR", "DESENGORDURANTE", "TIRA MANCHA", "ALVEJANTE", "SAPONACEO", "ESPONJA",
            "BOMBRIL", "PALHA DE ACO", "SACO DE LIXO", "SACO LIXO", "PAPEL TOALHA", "GUARDANAPO",
            "PANO DE CHAO", "PANO MULTIUSO", "RODO ", "VASSOURA", "INSETICIDA", "PINHO "),
        // ── Higiene & cuidado pessoal ───────────────────────────────────────
        rule(BasketCategory.HIGIENE,
            "SABONETE", "SHAMPOO", "XAMPU", "CONDICIONADOR", "CREME DENTAL", "PASTA DE DENTE",
            "ESCOVA DENTAL", "ESCOVA DE DENTE", "FIO DENTAL", "ENXAGUANTE BUCAL", "LISTERINE",
            "DESODORANTE", "ANTITRANSPIRANTE", "PAPEL HIGIENICO", "ABSORVENTE", "FRALDA",
            "LENCO UMEDECIDO", "ALGODAO", "COTONETE", "HASTE FLEXIVEL", "BARBEADOR", "LAMINA BARBEAR",
            "ESPUMA DE BARBEAR", "HIDRATANTE", "PROTETOR SOLAR", "SABONETE LIQUIDO"),
        // ── Alimentos processados ───────────────────────────────────────────
        rule(BasketCategory.ALIMENTO_PROCESSADO,
            "MACARRAO", "MIOJO", "LAMEN", "MOLHO DE TOMATE", "MOLHO TOMATE", "EXTRATO DE TOMATE",
            "SARDINHA EM LATA", "SARDINHA LATA", "ATUM ", "MILHO EM CONSERVA", "ERVILHA EM CONSERVA",
            "SELETA", "CREME DE LEITE", "MAIONESE", "KETCHUP", "MOSTARDA", "CALDO KNORR", "CALDO DE",
            "TEMPERO PRONTO", "SALSICHA", "LINGUICA", "PRESUNTO", "MORTADELA", "APRESUNTADO",
            "PEITO DE PERU", "BACON", "NUGGET", "HAMBURGUER", "EMPANADO", "LASANHA", "PIZZA",
            "PAO DE QUEIJO", "PAO DE FORMA", "PAO FORMA", "PAO FRANCES", "BISNAGUINHA", "TORRADA",
            "CREAM CRACKER", "CREAM-CRACKER", "AGUA E SAL", "REQUEIJAO", "IOGURTE", "QUEIJO MUSSARELA",
            "QUEIJO PRATO", "QUEIJO MINAS", "MANTEIGA", "MARGARINA", "CAFE ", "FARINHA LACTEA",
            "OLEO DE SOJA", "OLEO DE GIRASSOL", "OLEO DE MILHO", "AZEITE", "VINAGRE", "SAL REFINADO",
            "FARINHA DE TRIGO", "FERMENTO", "GELATINA EM PO", "MISTURA PARA BOLO", "CALDO GALINHA"),
        // ── Básicos in natura ──────────────────────────────────────────────
        rule(BasketCategory.BASICO_IN_NATURA,
            "ARROZ", "FEIJAO", "OVO", "OVOS", "BATATA", "CEBOLA", "ALHO", "TOMATE", "ALFACE",
            "CENOURA", "BANANA", "MACA", "LARANJA", "MAMAO", "MELANCIA", "MELAO", "MANGA ", "UVA ",
            "ABACAXI", "LIMAO", "COUVE", "BROCOLIS", "ABOBRINHA", "ABOBORA", "MANDIOCA", "AIPIM",
            "BETERRABA", "PEPINO", "PIMENTAO", "REPOLHO", "ESPINAFRE", "RUCULA", "AGRIAO",
            "CHUCHU", "QUIABO", "VAGEM", "MILHO VERDE", "VERDURA", "LEGUME", "HORTIFRUTI",
            "CARNE ", "PATINHO", "ACEM", "COXAO", "FRALDINHA", "MUSCULO", "FILE MIGNON", "ALCATRA",
            "CONTRA FILE", "COSTELA", "PERNIL", "FRANGO", "COXA DE FRANGO", "SOBRECOXA",
            "PEITO DE FRANGO", "SASSAMI", "PEIXE", "TILAPIA", "MERLUZA", "PESCADA", "SALMAO",
            "LEITE INTEGRAL", "LEITE DESNATADO", "LEITE SEMIDESNATADO", "LEITE ", "AVEIA",
            "LENTILHA", "GRAO DE BICO", "ERVILHA SECA", "CASTANHA", "NOZES", "AMENDOIM CRU")
    );

    /**
     * The bucket for a raw fiscal description ({@code xProd}). Never {@code null}.
     */
    public BasketCategory classify(String rawDescription) {
        String normalized = normalize(rawDescription);
        if (normalized.isBlank()) {
            return BasketCategory.NAO_CLASSIFICADO;
        }
        for (Rule rule : RULES) {
            for (Pattern keyword : rule.keywords()) {
                if (keyword.matcher(normalized).find()) {
                    return rule.category();
                }
            }
        }
        return BasketCategory.NAO_CLASSIFICADO;
    }

    /** Upper-case, strip diacritics, collapse runs of non-alphanumerics to a single space. */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        return (" " + noAccents + " ")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", " ");
    }

    private static Rule rule(BasketCategory category, String... keywords) {
        List<Pattern> patterns = java.util.Arrays.stream(keywords)
            .map(BasketClassifier::normalize)
            .map(String::strip)
            .filter(k -> !k.isBlank())
            // Match at a word start so "REFRIG" hits "REFRIGERANTE" but "OVO"
            // does not hit "NOVO". A trailing space in a keyword (e.g. "COCA ")
            // was normalized away; word-start matching keeps it from hitting
            // "COCADA".
            .map(k -> Pattern.compile("\\b" + Pattern.quote(k)))
            .toList();
        return new Rule(category, patterns);
    }

    private record Rule(BasketCategory category, List<Pattern> keywords) {
    }
}
