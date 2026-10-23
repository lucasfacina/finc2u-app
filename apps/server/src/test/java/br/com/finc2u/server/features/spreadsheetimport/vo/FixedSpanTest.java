package br.com.finc2u.server.features.spreadsheetimport.vo;

import br.com.finc2u.server.features.spreadsheetimport.enums.EntryKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FixedSpanTest {

    private SpreadsheetEntry fixed(String description, String value, int month) {
        return new SpreadsheetEntry(YearMonth.of(2026, month), EntryKind.CARD_FIXED, "NUBANK", description, new BigDecimal(value), null, null, false);
    }

    @Test
    void from_shouldJoinConsecutiveMonthsWithTheSameValue() {
        var spans = FixedSpan.from(List.of(fixed("Netflix", "20.90", 3), fixed("Netflix", "20.90", 1), fixed("Netflix", "20.90", 2)));

        assertEquals(1, spans.size());
        assertEquals(YearMonth.of(2026, 1), spans.getFirst().firstMonth());
        assertEquals(YearMonth.of(2026, 3), spans.getFirst().lastMonth());
        assertEquals(3, spans.getFirst().entries().size());
    }

    @Test
    void from_shouldStartAnotherSpan_whenTheValueChanges() {
        var spans = FixedSpan.from(List.of(fixed("iCloud", "14.90", 1), fixed("iCloud", "19.90", 2), fixed("iCloud", "19.90", 3)));

        assertEquals(2, spans.size());
        assertEquals(new BigDecimal("14.90"), spans.get(0).value());
        assertEquals(new BigDecimal("19.90"), spans.get(1).value());
    }

    @Test
    void from_shouldStartAnotherSpan_whenAMonthIsMissing() {
        var spans = FixedSpan.from(List.of(fixed("Discord", "24.90", 1), fixed("Discord", "24.90", 3)));

        assertEquals(2, spans.size());
    }

    @Test
    void from_shouldKeepDifferentItemsApart_ignoringTheCaseOfTheName() {
        var spans = FixedSpan.from(List.of(fixed("Netflix", "20.90", 1), fixed("NETFLIX", "20.90", 2), fixed("Spotify", "27.90", 1)));

        assertEquals(2, spans.size());
    }

}
