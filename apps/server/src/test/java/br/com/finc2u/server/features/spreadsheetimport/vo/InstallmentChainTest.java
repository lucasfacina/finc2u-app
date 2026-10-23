package br.com.finc2u.server.features.spreadsheetimport.vo;

import br.com.finc2u.server.features.spreadsheetimport.enums.EntryKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallmentChainTest {

    private SpreadsheetEntry entry(String card, String value, YearMonth month, int number, int total) {
        return new SpreadsheetEntry(month, EntryKind.CARD_ITEM, card, null, new BigDecimal(value), number, total, false);
    }

    @Test
    void from_shouldLinkTheInstallmentsOfOnePurchaseAcrossMonths_evenWhenTheCentsChange() {
        var chains = InstallmentChain.from(List.of(
            entry("NUBANK", "13.69", YearMonth.of(2026, 1), 3, 10),
            entry("NUBANK", "13.64", YearMonth.of(2026, 2), 4, 10),
            entry("NUBANK", "13.69", YearMonth.of(2026, 3), 5, 10)));

        assertEquals(1, chains.size());
        assertEquals(List.of(3, 4, 5), chains.getFirst().entries().stream().map(SpreadsheetEntry::installment).toList());
        assertEquals(10, chains.getFirst().total());
    }

    // Duas compras do mesmo mês e total: cada uma segue a sua, pela ordem dos valores.
    @Test
    void from_shouldKeepTwoPurchasesOfTheSameMonthAndTotalApart() {
        var chains = InstallmentChain.from(List.of(
            entry("NUBANK", "123.76", YearMonth.of(2026, 1), 1, 10),
            entry("NUBANK", "13.69", YearMonth.of(2026, 1), 1, 10),
            entry("NUBANK", "123.67", YearMonth.of(2026, 2), 2, 10),
            entry("NUBANK", "13.64", YearMonth.of(2026, 2), 2, 10)));

        assertEquals(2, chains.size());
        var values = chains.stream().map(chain -> chain.entries().stream().map(e -> e.value().toPlainString()).toList()).toList();
        assertTrue(values.contains(List.of("13.69", "13.64")));
        assertTrue(values.contains(List.of("123.76", "123.67")));
    }

    @Test
    void from_shouldNotLinkInstallmentsOfDifferentCardsTotalsOrOrigins() {
        var chains = InstallmentChain.from(List.of(
            entry("NUBANK", "10", YearMonth.of(2026, 1), 1, 3),
            entry("MERCADO PAGO", "10", YearMonth.of(2026, 2), 2, 3),
            entry("NUBANK", "10", YearMonth.of(2026, 2), 2, 4),
            entry("NUBANK", "10", YearMonth.of(2026, 2), 1, 3)));

        assertEquals(4, chains.size());
    }

    @Test
    void from_shouldLinkAcrossAMonthTheSheetSkipped() {
        var chains = InstallmentChain.from(List.of(
            entry("NUBANK", "50", YearMonth.of(2026, 1), 1, 4),
            entry("NUBANK", "50", YearMonth.of(2026, 3), 3, 4)));

        assertEquals(1, chains.size());
        assertEquals(YearMonth.of(2026, 1), chains.getFirst().first().paymentMonth());
    }

    @Test
    void from_shouldIgnoreEntriesThatAreNotInstallments() {
        var single = new SpreadsheetEntry(YearMonth.of(2026, 1), EntryKind.CARD_ITEM, "NUBANK", null, BigDecimal.TEN, null, null, false);

        assertTrue(InstallmentChain.from(List.of(single)).isEmpty());
    }

}
