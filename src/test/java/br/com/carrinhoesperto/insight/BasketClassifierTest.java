package br.com.carrinhoesperto.insight;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the M006.1 keyword classifier (docs/basket-insight.md). Pure
 * logic — no Spring context. Coverage is partial by design; these pin the
 * behaviour that the seal depends on (supérfluo vs. básico) and the
 * boundary/accent handling.
 */
class BasketClassifierTest {

    private final BasketClassifier classifier = new BasketClassifier();

    @ParameterizedTest
    @CsvSource({
        "REFRIGERANTE COCA-COLA 2L,           BEBIDA_BAIXO_VALOR",
        "GUARANA ANTARCTICA LATA,             BEBIDA_BAIXO_VALOR",
        "SUCO EM PO TANG LARANJA,             BEBIDA_BAIXO_VALOR",
        "CERVEJA PILSEN 350ML,                BEBIDA_BAIXO_VALOR",
        "CHOCOLATE AO LEITE 90G,              DOCE_SUPERFLUO",
        "BISCOITO RECHEADO MORANGO,           DOCE_SUPERFLUO",
        "SALGADINHO DORITOS QUEIJO,           DOCE_SUPERFLUO",
        "SORVETE POTE 2L,                     DOCE_SUPERFLUO",
        "LEITE CONDENSADO ITALAC 395G,        DOCE_SUPERFLUO",
        "DETERGENTE YPE NEUTRO,               LIMPEZA",
        "SABAO EM PO OMO 1KG,                 LIMPEZA",
        "AGUA SANITARIA QBOA 1L,              LIMPEZA",
        "SABONETE DOVE 90G,                   HIGIENE",
        "CREME DENTAL COLGATE,                HIGIENE",
        "PAPEL HIGIENICO NEVE 12,             HIGIENE",
        "MACARRAO ESPAGUETE 500G,             ALIMENTO_PROCESSADO",
        "PRESUNTO FATIADO SADIA,              ALIMENTO_PROCESSADO",
        "IOGURTE NATURAL DANONE,              ALIMENTO_PROCESSADO",
        "OLEO DE SOJA SOYA 900ML,             ALIMENTO_PROCESSADO",
        "PAO FRANCES KG,                      ALIMENTO_PROCESSADO",
        "ARROZ BRANCO TIPO 1 5KG,             BASICO_IN_NATURA",
        "FEIJAO CARIOCA 1KG,                  BASICO_IN_NATURA",
        "OVOS BRANCOS GRANDES DZ,             BASICO_IN_NATURA",
        "BANANA PRATA KG,                     BASICO_IN_NATURA",
        "PEITO DE FRANGO RESFRIADO,           BASICO_IN_NATURA",
        "LEITE INTEGRAL JUSSARA 1L,           BASICO_IN_NATURA",
        "TAPETE EMBORRACHADO 40X60,           NAO_CLASSIFICADO",
        "PILHA ALCALINA AA,                   NAO_CLASSIFICADO",
    })
    void classifiesRepresentativeDescriptions(String description, BasketCategory expected) {
        assertThat(classifier.classify(description)).isEqualTo(expected);
    }

    @Test
    void isCaseAndAccentInsensitive() {
        assertThat(classifier.classify("refrigerante guaraná")).isEqualTo(BasketCategory.BEBIDA_BAIXO_VALOR);
        assertThat(classifier.classify("Pão Francês")).isEqualTo(BasketCategory.ALIMENTO_PROCESSADO);
        assertThat(classifier.classify("FEIJÃO PRETO")).isEqualTo(BasketCategory.BASICO_IN_NATURA);
    }

    @Test
    void matchesAtWordStartNotMidWord() {
        // "OVO" must not fire on "NOVO"; this is a clock, not a carton of eggs.
        assertThat(classifier.classify("RELOGIO DE PAREDE NOVO")).isEqualTo(BasketCategory.NAO_CLASSIFICADO);
    }

    @Test
    void blankOrNullIsUnclassified() {
        assertThat(classifier.classify(null)).isEqualTo(BasketCategory.NAO_CLASSIFICADO);
        assertThat(classifier.classify("   ")).isEqualTo(BasketCategory.NAO_CLASSIFICADO);
    }
}
