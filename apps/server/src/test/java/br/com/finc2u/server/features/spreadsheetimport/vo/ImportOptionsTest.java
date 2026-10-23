package br.com.finc2u.server.features.spreadsheetimport.vo;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportOptionsTest {

    @Test
    void categoryFor_shouldMatchPartOfTheDescriptionIgnoringCaseAndAccents() {
        var options = new ImportOptions(Map.of("combustivel", "Transporte"), null, null);

        assertEquals("Transporte", options.categoryFor("COMBÚSTIVEL do carro"));
    }

    @Test
    void categoryFor_shouldTakeTheFirstRuleThatMatches() {
        var rules = new LinkedHashMap<String, String>();
        rules.put("facul", "Educação");
        rules.put("a", "Outras");

        assertEquals("Educação", new ImportOptions(rules, null, null).categoryFor("FACULDADE"));
    }

    @Test
    void categoryFor_shouldFallBackToOthers_whenNothingMatchesOrThereIsNoDescription() {
        var options = new ImportOptions(Map.of("facul", "Educação"), null, null);

        assertEquals("Outros", options.categoryFor("PRESENTE"));
        assertEquals("Outros", options.categoryFor(null));
    }

    @Test
    void categoryFor_shouldUseTheInformedDefaultCategory() {
        assertEquals("Diversos", new ImportOptions(null, "  Diversos ", null).categoryFor("qualquer"));
    }

    @Test
    void constructor_shouldRejectADueDayOutOfTheMonth() {
        assertThrows(IllegalArgumentException.class, () -> new ImportOptions(null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new ImportOptions(null, null, 32));
    }

}
