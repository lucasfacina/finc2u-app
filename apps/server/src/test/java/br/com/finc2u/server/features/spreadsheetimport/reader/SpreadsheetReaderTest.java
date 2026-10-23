package br.com.finc2u.server.features.spreadsheetimport.reader;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.features.spreadsheetimport.enums.EntryKind;
import br.com.finc2u.server.features.spreadsheetimport.support.SpreadsheetWorkbookBuilder;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetData;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpreadsheetReaderTest {

    private static final String NUBANK = "NUBANK                                      Fechamento: 09                                              Vencimento: 15";
    private static final String PLACEHOLDER = "--------------";
    private static final YearMonth FROM = YearMonth.of(2025, 4);

    @TempDir
    Path dir;

    private final SpreadsheetReader reader = new SpreadsheetReader();

    private SpreadsheetData read(SpreadsheetWorkbookBuilder builder) {
        return reader.read(builder.build(dir.resolve("planilha.xlsx")), FROM);
    }

    private SpreadsheetWorkbookBuilder june() {
        return new SpreadsheetWorkbookBuilder();
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void read_shouldDecodeTheCardAndItsInstallmentFromADateCell() {
        var builder = june();
        builder.month(2025, 6)
            .line(NUBANK, 59.64, LocalDate.of(2025, 3, 10))
            .line(null, 22.92, PLACEHOLDER)
            .line("TOTAL DA FATURA", 82.56, null);

        var data = read(builder);

        assertEquals(1, data.cards().size());
        assertEquals("NUBANK", data.cards().getFirst().name());
        assertEquals(9, data.cards().getFirst().closingDay());
        assertEquals(15, data.cards().getFirst().dueDay());
        assertEquals(2, data.entries().size());
        var installment = data.entries().getFirst();
        assertEquals(EntryKind.CARD_ITEM, installment.kind());
        assertEquals("NUBANK", installment.card());
        assertEquals(YearMonth.of(2025, 7), installment.paymentMonth());
        assertEquals(money("59.64"), installment.value());
        assertEquals(3, installment.installment());
        assertEquals(10, installment.totalInstallments());
        assertNull(data.entries().get(1).installment());
    }

    @Test
    void read_shouldPayTheDecemberBlockInJanuaryOfTheNextYear() {
        var builder = june();
        builder.month(2025, 12).line(NUBANK, 10, null);

        var data = read(builder);

        assertEquals(YearMonth.of(2026, 1), data.entries().getFirst().paymentMonth());
    }

    @Test
    void read_shouldMarkEveryItemOfTheInvoicePaid_onlyForTheCardWithPG() {
        var builder = june();
        builder.month(2025, 6)
            .line(NUBANK, 10, null)
            .line("GERAIS", 5, PLACEHOLDER)
            .line("TOTAL DA FATURA", 15, "PG")
            .line("RENNER                              Fechamento: 30 Vencimento: 15", 7, null)
            .line("TOTAL DA FATURA", 7, null);

        var entries = read(builder).entries();

        assertEquals(3, entries.size());
        assertTrue(entries.getFirst().paid());
        assertTrue(entries.get(1).paid());
        assertEquals("RENNER", entries.get(2).card());
        assertFalse(entries.get(2).paid());
    }

    @Test
    void read_shouldTakeTheFixedDescriptionFromTheThirdColumnAndTheGeneralFromTheSection() {
        var builder = june();
        builder.month(2025, 6)
            .line(NUBANK, 10, PLACEHOLDER)
            .line("FIXOS", 20.9, "Netflix")
            .line(null, 27.9, "Spotify")
            .line("GERAIS", -19.15, PLACEHOLDER)
            .line("TOTAL DA FATURA", 0, "PG");

        var entries = read(builder).entries();

        assertEquals(List.of(EntryKind.CARD_ITEM, EntryKind.CARD_FIXED, EntryKind.CARD_FIXED, EntryKind.CARD_GENERAL),
            entries.stream().map(SpreadsheetEntry::kind).toList());
        assertEquals("Netflix", entries.get(1).description());
        assertEquals("Spotify", entries.get(2).description());
        assertNull(entries.get(3).description());
        assertEquals(money("-19.15"), entries.get(3).value());
    }

    @Test
    void read_shouldReadTheBillsWithoutThePGMarkAndSkipZeroedOnes() {
        var builder = june();
        builder.month(2025, 6)
            .line("CONTAS FIXAS", 1014, "FACULDADE - PG")
            .line(null, 0, "REMÉDIOS")
            .line(null, 99.9, "COMBÚSTIVEL")
            .line("TOTAL DE CONTAS", 1113.9, null);

        var entries = read(builder).entries();

        assertEquals(2, entries.size());
        assertEquals("FACULDADE", entries.getFirst().description());
        assertTrue(entries.getFirst().paid());
        assertEquals("COMBÚSTIVEL", entries.get(1).description());
        assertFalse(entries.get(1).paid());
        assertEquals(EntryKind.BILL, entries.get(1).kind());
    }

    @Test
    void read_shouldReadTheExtrasIgnoringThePlaceholdersAndDecodingTheTextInstallment() {
        var builder = june();
        builder.month(2025, 6)
            .line("TOTAL DE CONTAS", 0, null)
            .line("VIAGEM CABO FRIO", 205, "PG")
            .line("ÔNIBUS", 30, "04/11 - PG")
            .line("AR CONDICIONADO", 150, LocalDate.of(2026, 9, 21))
            .line("EXTRAS", PLACEHOLDER, PLACEHOLDER)
            .line("TOTAL DE EXTRAS", 385, null);

        var entries = read(builder).entries();

        assertEquals(3, entries.size());
        assertTrue(entries.getFirst().paid());
        assertNull(entries.getFirst().installment());
        assertEquals(4, entries.get(1).installment());
        assertEquals(11, entries.get(1).totalInstallments());
        assertTrue(entries.get(1).paid());
        assertEquals(9, entries.get(2).installment());
        assertEquals(21, entries.get(2).totalInstallments());
        assertFalse(entries.get(2).paid());
        assertEquals(EntryKind.EXTRA, entries.get(2).kind());
    }

    @Test
    void read_shouldReadTheIncomeWithoutThePGMarkAndSkipZeroedOnes() {
        var builder = june();
        builder.month(2025, 6)
            .line("SALÁRIO", null, null)
            .line("Salário 5º dia útil", 0, null)
            .line("Parte Mãe - PG", 2600, null)
            .line("Seguro Desemprego", 1518, null)
            .line("Extras", 0, null)
            .line("TOTAL A RECEBER", 4118, null);

        var entries = read(builder).entries();

        assertEquals(2, entries.size());
        assertEquals("Parte Mãe", entries.getFirst().description());
        assertTrue(entries.getFirst().paid());
        assertEquals(EntryKind.INCOME, entries.getFirst().kind());
        assertEquals("Seguro Desemprego", entries.get(1).description());
        assertFalse(entries.get(1).paid());
    }

    @Test
    void read_shouldKeepTheTotalsThatTheSheetCalculates() {
        var builder = june();
        builder.month(2025, 6)
            .line(NUBANK, 10, null)
            .line("TOTAL A PAGAR ", 10, null)
            .line("SALDO DO MÊS", 5, null)
            .line("SALÁRIO", null, null)
            .line("Seguro Desemprego", 15, null)
            .line("TOTAL A RECEBER", 15, null);

        var totals = read(builder).totals();

        assertEquals(1, totals.size());
        assertEquals(YearMonth.of(2025, 7), totals.getFirst().paymentMonth());
        assertEquals(money("10.00"), totals.getFirst().totalToPay());
        assertEquals(money("15.00"), totals.getFirst().totalToReceive());
    }

    @Test
    void read_shouldIgnoreTheCashBalanceSectionAfterTheIncome() {
        var builder = june();
        builder.month(2025, 6)
            .line("SALÁRIO", null, null)
            .line("TOTAL A RECEBER", 0, null)
            .line("DINHEIRO EM CAIXA", null, null)
            .line("Santander", 1000, null)
            .line("Parte Mãe - PG", 2600, null);

        assertTrue(read(builder).entries().isEmpty());
    }

    @Test
    void read_shouldSkipTheBlocksBeforeTheFirstMonthAndTheSheetsOfOtherLayouts() {
        var builder = june().otherSheet("GASTOS 2024").otherSheet("Resumo");
        builder.month(2025, 3).line(NUBANK, 10, null);
        builder.month(2025, 4).line(NUBANK, 20, null);

        var entries = read(builder).entries();

        assertEquals(1, entries.size());
        assertEquals(money("20.00"), entries.getFirst().value());
        assertEquals(YearMonth.of(2025, 5), entries.getFirst().paymentMonth());
    }

    @Test
    void read_shouldUseTheCalculatedValueOfFormulasAndRoundToCents() {
        var builder = june();
        builder.month(2025, 6).line(NUBANK, "=0.1+0.2", null).line(null, "=10+5.5", null);

        var entries = read(builder).entries();

        assertEquals(money("0.30"), entries.getFirst().value());
        assertEquals(money("15.50"), entries.get(1).value());
    }

    @Test
    void read_shouldIgnoreAnInstallmentWhoseNumberPassesTheTotal() {
        var builder = june();
        builder.month(2025, 6).line(NUBANK, 10, LocalDate.of(2025, 12, 3));

        assertNull(read(builder).entries().getFirst().installment());
    }

    @Test
    void read_shouldFail_whenTheFileDoesNotExistOrIsNotXlsx() throws IOException {
        var csv = Files.writeString(dir.resolve("planilha.csv"), "a;b");

        assertThrows(BusinessException.class, () -> reader.read(dir.resolve("nao-existe.xlsx"), FROM));
        assertThrows(BusinessException.class, () -> reader.read(csv, FROM));
    }

    @Test
    void read_shouldFail_whenTheFileIsTooBig() throws IOException {
        var big = dir.resolve("grande.xlsx");
        Files.write(big, new byte[10 * 1024 * 1024 + 1]);

        assertThrows(BusinessException.class, () -> reader.read(big, FROM));
    }

    @Test
    void read_shouldFail_whenTheContentIsNotAWorkbook() throws IOException {
        var fake = Files.writeString(dir.resolve("falsa.xlsx"), "isto não é um zip");

        assertThrows(RuntimeException.class, () -> reader.read(fake, FROM));
    }

}
