package br.com.carrinhoesperto.fiscal.ingestion.saopaulo;

import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalPaymentRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link SaoPauloNfceParser} against the sanitized production
 * fixture (M003 §19). Verifies exact fiscal extraction, weighted items,
 * duplicate lines kept separate, split payments, and that fields absent from the
 * public HTML (GTIN, cEANTrib, NCM, CEST, per-item discount) stay {@code null}.
 */
class SaoPauloNfceParserTest {

    private static final String ACCESS_KEY = "35260900371046000117650080001172161001384659";

    private static byte[] fixture;
    private final SaoPauloNfceParser parser = new SaoPauloNfceParser();

    @BeforeAll
    static void loadFixture() throws Exception {
        try (InputStream in = SaoPauloNfceParserTest.class
                .getResourceAsStream("/fixtures/nfce/sao-paulo-consulta-qrcode.html")) {
            assertThat(in).isNotNull();
            fixture = in.readAllBytes();
        }
    }

    private FiscalDocumentRequest parse() {
        return parser.parse(fixture, ACCESS_KEY);
    }

    @Test
    void extractsDocumentLevelFields() {
        FiscalDocumentRequest doc = parse();

        assertThat(doc.accessKey()).isEqualTo(ACCESS_KEY);
        assertThat(doc.documentNumber()).isEqualTo(117216L);
        assertThat(doc.series()).isEqualTo(8);
        assertThat(doc.issuedAt()).isEqualTo("2026-09-06T14:57:04-03:00");
        assertThat(doc.issuer().cnpj()).isEqualTo("00371046000117");
        assertThat(doc.issuer().legalName()).isEqualTo("SM SUPERMERCADOS MENDONCA LTDA");
        assertThat(doc.issuer().address()).contains("Deputado Virgilio de Carvalho Pinto, 601");
    }

    @Test
    void extractsThreeWayTotals() {
        FiscalDocumentRequest doc = parse();

        // merchandise - discount = payable  (328.11 - 12.88 = 315.23)
        assertThat(doc.totalItemsAmount()).isEqualByComparingTo("328.11");
        assertThat(doc.totalDiscount()).isEqualByComparingTo("12.88");
        assertThat(doc.totalAmount()).isEqualByComparingTo("315.23");
    }

    @Test
    void extractsAllTwentyTwoItemsInOrder() {
        FiscalDocumentRequest doc = parse();

        assertThat(doc.items()).hasSize(22);
        assertThat(doc.items()).extracting(FiscalItemRequest::rawDescription)
            .startsWith("SALSICHA HOT DOG PERDIGAO", "COSTELA PORCO kg", "PAO ALHO ZINHO 300G")
            .endsWith("LAVA ROUPA LIQ.OMO LA.PER", "LEITE JUSSARA INTEGRAL 1L");
    }

    @Test
    void preservesAWeightedItemWithoutRounding() {
        FiscalItemRequest salsicha = parse().items().get(0);

        assertThat(salsicha.rawDescription()).isEqualTo("SALSICHA HOT DOG PERDIGAO");
        assertThat(salsicha.supplierItemCode()).isEqualTo("169196");
        assertThat(salsicha.quantity()).isEqualByComparingTo("0.9944");
        assertThat(salsicha.unit()).isEqualTo("KG");
        assertThat(salsicha.unitPrice()).isEqualByComparingTo("10.750201");
        assertThat(salsicha.totalPrice()).isEqualByComparingTo("10.69");
    }

    @Test
    void keepsDuplicateItemLinesSeparate() {
        var items = parse().items();

        FiscalItemRequest first = items.get(7);
        FiscalItemRequest second = items.get(8);
        assertThat(first.rawDescription()).isEqualTo("LEITE COND.ITALAC TP 395G");
        assertThat(second.rawDescription()).isEqualTo("LEITE COND.ITALAC TP 395G");
        assertThat(first.supplierItemCode()).isEqualTo("756440");
        assertThat(second.supplierItemCode()).isEqualTo("756440");
        assertThat(items).filteredOn(i -> "LEITE COND.ITALAC TP 395G".equals(i.rawDescription())).hasSize(2);
    }

    @Test
    void preservesAnIntegerQuantityItem() {
        FiscalItemRequest leite = parse().items().get(21);

        assertThat(leite.rawDescription()).isEqualTo("LEITE JUSSARA INTEGRAL 1L");
        assertThat(leite.supplierItemCode()).isEqualTo("697002");
        assertThat(leite.quantity()).isEqualByComparingTo("12");
        assertThat(leite.unit()).isEqualTo("UN");
        assertThat(leite.unitPrice()).isEqualByComparingTo("5.18");
        assertThat(leite.totalPrice()).isEqualByComparingTo("62.16");
    }

    @Test
    void leavesFieldsAbsentFromThePublicPageNull() {
        assertThat(parse().items()).allSatisfy(item -> {
            assertThat(item.gtin()).isNull();
            assertThat(item.gtinTaxable()).isNull();
            assertThat(item.ncm()).isNull();
            assertThat(item.cest()).isNull();
            assertThat(item.discount()).isNull();
        });
    }

    @Test
    void extractsBothPaymentsWithMethodAndAmount() {
        var payments = parse().payments();

        assertThat(payments).hasSize(2);
        assertThat(payments).extracting(FiscalPaymentRequest::method)
            .containsExactly("Cartão de Débito", "Cartão de Crédito");
        assertThat(payments.get(0).amount()).isEqualByComparingTo("313.26");
        assertThat(payments.get(1).amount()).isEqualByComparingTo("1.97");
        assertThat(payments).extracting(FiscalPaymentRequest::methodDescription).containsOnlyNulls();
    }

    @Test
    void neverExtractsConsumerData() {
        // The parser has no code path that reads .clConteudoConsumidor / .ulListaConsumidor.
        // Sanity: nothing consumer-shaped leaks into the structured request.
        FiscalDocumentRequest doc = parse();
        assertThat(doc.toString()).doesNotContain("CONSUMIDOR DE TESTE").doesNotContain("000.000.000-00");
    }

    /**
     * A full, current capture of the same receipt straight from SEFAZ-SP
     * (sanitized only for CPF). It differs from the trimmed fixture above by
     * carrying the extra markup the live page emits — notably a
     * {@code <label class="tx">Troco</label><span class="totalNumb">NaN</span>}
     * row, which used to blow up number parsing.
     */
    @Nested
    class AgainstAFullLiveCapture {

        private byte[] live;

        {
            try (InputStream in = SaoPauloNfceParserTest.class.getResourceAsStream(
                    "/fixtures/nfce/sao-paulo-consulta-qrcode-live-troco-nan.html")) {
                assertThat(in).isNotNull();
                live = in.readAllBytes();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Test
        void parsesWithoutChokingOnTheNaNTrocoRow() {
            assertThatCode(() -> parser.parse(live, ACCESS_KEY)).doesNotThrowAnyException();
        }

        @Test
        void extractsTheSameFiscalTruthAsTheTrimmedFixture() {
            FiscalDocumentRequest doc = parser.parse(live, ACCESS_KEY);

            assertThat(doc.documentNumber()).isEqualTo(117216L);
            assertThat(doc.series()).isEqualTo(8);
            assertThat(doc.issuedAt()).isEqualTo("2026-09-06T14:57:04-03:00");
            assertThat(doc.issuer().cnpj()).isEqualTo("00371046000117");
            assertThat(doc.totalItemsAmount()).isEqualByComparingTo("328.11");
            assertThat(doc.totalDiscount()).isEqualByComparingTo("12.88");
            assertThat(doc.totalAmount()).isEqualByComparingTo("315.23");
            assertThat(doc.items()).hasSize(22);
        }

        @Test
        void doesNotTreatTrocoAsAPayment() {
            var payments = parser.parse(live, ACCESS_KEY).payments();

            assertThat(payments).extracting(FiscalPaymentRequest::method)
                .containsExactly("Cartão de Débito", "Cartão de Crédito");
            assertThat(payments).noneMatch(p -> p.method().toLowerCase().contains("troco"));
        }
    }
}
