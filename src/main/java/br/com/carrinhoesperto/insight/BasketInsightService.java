package br.com.carrinhoesperto.insight;

import br.com.carrinhoesperto.fiscal.FiscalDocumentService;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemResponse;
import br.com.carrinhoesperto.insight.dto.BasketInsightResponse;
import br.com.carrinhoesperto.insight.dto.CategoryBreakdown;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Groups one of the caller's receipts into {@link BasketCategory} buckets and
 * derives a {@link BasketSeal} (M006.1, docs/basket-insight.md).
 *
 * <p>A thin read/derive layer — like {@code dashboard} — not a bounded context.
 * It reads fiscal truth through {@link FiscalDocumentService} (which enforces
 * ownership for both authenticated and anonymous callers), classifies each line
 * with {@link BasketClassifier}, and returns aggregates. Nothing is persisted;
 * Resolution and Catalog are untouched.
 */
@Service
public class BasketInsightService {

    /** Above this unclassified value share, the seal is {@link BasketSeal#SEM_DADOS}. */
    private static final BigDecimal SEM_DADOS_LIMIT = new BigDecimal("0.40");
    /** At or above this supérfluo value share, the basket is {@link BasketSeal#MUITOS_SUPERFLUOS}. */
    private static final BigDecimal MUITOS_SUPERFLUOS_LIMIT = new BigDecimal("0.35");
    /** Below this supérfluo share (with enough básicos) the basket is {@link BasketSeal#EQUILIBRADO}. */
    private static final BigDecimal EQUILIBRADO_SUPERFLUO_LIMIT = new BigDecimal("0.15");
    /** Minimum "básicos in natura" value share for {@link BasketSeal#EQUILIBRADO}. */
    private static final BigDecimal EQUILIBRADO_BASICO_MIN = new BigDecimal("0.40");

    private final FiscalDocumentService fiscalDocumentService;
    private final BasketClassifier classifier;

    BasketInsightService(FiscalDocumentService fiscalDocumentService, BasketClassifier classifier) {
        this.fiscalDocumentService = fiscalDocumentService;
        this.classifier = classifier;
    }

    /**
     * @throws br.com.carrinhoesperto.fiscal.FiscalDocumentNotFoundException the document
     *         does not exist or is not the caller's (404)
     */
    @Transactional(readOnly = true)
    public BasketInsightResponse forOwnedDocument(UUID documentId) {
        FiscalDocumentResponse document = fiscalDocumentService.getOwnedDetail(documentId);

        Map<BasketCategory, Bucket> buckets = new EnumMap<>(BasketCategory.class);
        BigDecimal basketTotal = BigDecimal.ZERO;
        for (FiscalItemResponse item : document.items()) {
            BasketCategory category = classifier.classify(item.rawDescription());
            BigDecimal value = item.totalPrice() == null ? BigDecimal.ZERO : item.totalPrice();
            buckets.computeIfAbsent(category, c -> new Bucket()).add(value);
            basketTotal = basketTotal.add(value);
        }

        List<CategoryBreakdown> categories = new ArrayList<>();
        for (Map.Entry<BasketCategory, Bucket> entry : buckets.entrySet()) {
            BasketCategory category = entry.getKey();
            Bucket bucket = entry.getValue();
            categories.add(new CategoryBreakdown(
                category.name(),
                category.label(),
                bucket.count,
                bucket.value,
                share(bucket.value, basketTotal),
                category.superfluous()));
        }
        categories.sort(Comparator.comparing(CategoryBreakdown::totalValue).reversed());

        BigDecimal superfluousShare = share(sumWhere(buckets, BasketCategory::superfluous), basketTotal);
        BigDecimal basicShare = share(valueOf(buckets, BasketCategory.BASICO_IN_NATURA), basketTotal);
        BigDecimal unclassifiedShare = share(valueOf(buckets, BasketCategory.NAO_CLASSIFICADO), basketTotal);

        BasketSeal seal = seal(superfluousShare, basicShare, unclassifiedShare);
        List<String> messages = messages(seal, superfluousShare, basicShare, unclassifiedShare);

        return new BasketInsightResponse(document.id(), seal.name(), seal.label(),
            categories, superfluousShare, basicShare, unclassifiedShare, messages);
    }

    private static BasketSeal seal(BigDecimal superfluousShare, BigDecimal basicShare,
                                   BigDecimal unclassifiedShare) {
        if (unclassifiedShare.compareTo(SEM_DADOS_LIMIT) > 0) {
            return BasketSeal.SEM_DADOS;
        }
        if (superfluousShare.compareTo(MUITOS_SUPERFLUOS_LIMIT) >= 0) {
            return BasketSeal.MUITOS_SUPERFLUOS;
        }
        if (superfluousShare.compareTo(EQUILIBRADO_SUPERFLUO_LIMIT) < 0
                && basicShare.compareTo(EQUILIBRADO_BASICO_MIN) >= 0) {
            return BasketSeal.EQUILIBRADO;
        }
        return BasketSeal.ATENCAO;
    }

    private static List<String> messages(BasketSeal seal, BigDecimal superfluousShare,
                                         BigDecimal basicShare, BigDecimal unclassifiedShare) {
        List<String> out = new ArrayList<>();
        switch (seal) {
            case SEM_DADOS -> out.add("Não reconhecemos itens suficientes desta nota (" + pct(unclassifiedShare)
                + "% do valor não classificado) para avaliar o carrinho.");
            case MUITOS_SUPERFLUOS -> {
                out.add(pct(superfluousShare) + "% do valor da nota foi em doces, salgadinhos e bebidas de baixo valor nutricional.");
                out.add("Vale equilibrar com mais itens básicos in natura na próxima compra.");
            }
            case EQUILIBRADO -> {
                out.add("Itens básicos in natura representam " + pct(basicShare) + "% da compra.");
                out.add("Doces e bebidas de baixo valor nutricional ficaram em " + pct(superfluousShare) + "%.");
            }
            case ATENCAO -> {
                out.add("Doces e bebidas de baixo valor nutricional somam " + pct(superfluousShare) + "% do valor da nota.");
                out.add("Básicos in natura estão em " + pct(basicShare) + "% — dá para aumentar essa fatia.");
            }
        }
        return out;
    }

    private static BigDecimal share(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return part.divide(total, 4, RoundingMode.HALF_UP);
    }

    private static int pct(BigDecimal share) {
        return share.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private static BigDecimal sumWhere(Map<BasketCategory, Bucket> buckets,
                                       java.util.function.Predicate<BasketCategory> predicate) {
        return buckets.entrySet().stream()
            .filter(e -> predicate.test(e.getKey()))
            .map(e -> e.getValue().value)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal valueOf(Map<BasketCategory, Bucket> buckets, BasketCategory category) {
        Bucket bucket = buckets.get(category);
        return bucket == null ? BigDecimal.ZERO : bucket.value;
    }

    private static final class Bucket {
        private int count;
        private BigDecimal value = BigDecimal.ZERO;

        void add(BigDecimal amount) {
            count++;
            value = value.add(amount);
        }
    }
}
