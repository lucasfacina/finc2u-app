package br.com.finc2u.server.shared.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextOrderTest {

    private static final List<String> EXPECTED = List.of("Água", "alimentação", "Alimentação", "Casa", "ônibus", "Ótica", "Zebra");

    @Test
    void by_shouldSortInPortugueseOrder_accentsWithTheirBaseLetter() {
        var names = new ArrayList<>(EXPECTED);
        Collections.shuffle(names, new Random(7));

        names.sort(TextOrder.by(name -> name));

        assertEquals(EXPECTED, names);
    }

    @Test
    void by_shouldGiveTheSameOrder_whateverTheInputOrder() {
        for (var seed = 0; seed < 20; seed++) {
            var names = new ArrayList<>(EXPECTED);
            Collections.shuffle(names, new Random(seed));

            names.sort(TextOrder.by(name -> name));

            assertEquals(EXPECTED, names, "seed " + seed);
        }
    }

    @Test
    void by_shouldPutTheLowercaseFirst_whenTheTextsDifferOnlyInCase() {
        var names = new ArrayList<>(List.of("Alimentação", "alimentação"));

        names.sort(TextOrder.by(name -> name));

        assertEquals(List.of("alimentação", "Alimentação"), names);
    }

    @Test
    void by_shouldBreakTheTieByCodePoint_whenTheCollatorSeesTheSameText() {
        var composed = "Ação";
        var decomposed = "Ação";
        var first = new ArrayList<>(List.of(composed, decomposed));
        var second = new ArrayList<>(List.of(decomposed, composed));

        first.sort(TextOrder.by(name -> name));
        second.sort(TextOrder.by(name -> name));

        assertEquals(first, second);
    }

    @Test
    void by_shouldReadTheTextFromTheObject() {
        record Item(String name) {
        }
        var items = new ArrayList<>(List.of(new Item("Zebra"), new Item("Água"), new Item("casa")));

        items.sort(TextOrder.by(Item::name));

        assertEquals(List.of("Água", "casa", "Zebra"), items.stream().map(Item::name).toList());
    }

}
