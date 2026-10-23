package br.com.finc2u.server.features.spreadsheetimport.reader;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.features.spreadsheetimport.enums.SheetSection;
import br.com.finc2u.server.features.spreadsheetimport.vo.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Pattern;

/*
 * Lê a planilha de gastos do autor (uma aba "GASTOS <ano>" por ano, um bloco de 4 colunas por mês: descrição, valor e
 * parcela/observação) e devolve registros neutros, sem gravar nada. Só entende o leiaute itemizado (a partir de 2025); as
 * abas de anos anteriores, que têm outro leiaute, são ignoradas, assim como os blocos de meses anteriores a `firstBlockMonth`.
 * O bloco do mês M é pago em M+1 (o de dezembro, em janeiro do ano seguinte).
 *
 * É código de uso único e de confiança limitada, porque abre arquivo de terceiros: só aceita .xlsx de até 10 MB, abre em
 * modo de leitura, mantém os limites padrão do POI contra zip bomb e lê o valor já calculado das fórmulas (nunca as executa).
 * A parcela "n/m" costuma vir como data (o Excel lê "4/12" como 4 de dezembro): o mês é o n e o dia é o m.
 */
@Component
public class SpreadsheetReader {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int FIRST_BLOCK_COLUMN = 2;
    private static final int BLOCK_WIDTH = 4;
    private static final int FIRST_DATA_ROW = 4;
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private static final Pattern SHEET_NAME = Pattern.compile("^GASTOS (\\d{4})$");
    private static final Pattern CARD_HEADER = Pattern.compile("^(.+?)\\s*Fechamento:\\s*(\\d{1,2})\\s*Vencimento:\\s*(\\d{1,2})$");
    private static final Pattern INSTALLMENT_TEXT = Pattern.compile("^(\\d{1,2})/(\\d{1,3})\\b");
    private static final Pattern PAID_MARK = Pattern.compile("\\s*-?\\s*\\bPG\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAID_WORD = Pattern.compile("\\bPG\\b", Pattern.CASE_INSENSITIVE);

    public SpreadsheetData read(Path file, YearMonth firstBlockMonth) {
        validate(file);

        var entries = new ArrayList<SpreadsheetEntry>();
        var totals = new ArrayList<SpreadsheetMonthTotals>();
        var cards = new LinkedHashMap<String, SpreadsheetCard>();

        try (var workbook = new XSSFWorkbook(OPCPackage.open(file.toFile(), PackageAccess.READ))) {
            for (var sheet : workbook) {
                var name = SHEET_NAME.matcher(sheet.getSheetName().trim());
                if (!name.matches()) continue;

                readSheet(sheet, Integer.parseInt(name.group(1)), firstBlockMonth, entries, totals, cards);
            }
        } catch (IOException exception) {
            throw new BusinessException("Não foi possível ler a planilha.", exception);
        } catch (InvalidFormatException exception) {
            throw new BusinessException("O arquivo não é uma planilha .xlsx válida.", exception);
        }

        return SpreadsheetData.of(List.copyOf(cards.values()), entries, totals);
    }

    private void validate(Path file) {
        var isXlsx = Files.isRegularFile(file) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx");
        if (!isXlsx) throw new BusinessException("A planilha deve ser um arquivo .xlsx existente.");

        var size = sizeOf(file);
        if (size > MAX_BYTES) throw new BusinessException("A planilha passa do tamanho máximo de 10 MB.");
    }

    private long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new BusinessException("Não foi possível ler a planilha.", exception);
        }
    }

    private void readSheet(Sheet sheet, int year, YearMonth firstBlockMonth, List<SpreadsheetEntry> entries, List<SpreadsheetMonthTotals> totals, Map<String, SpreadsheetCard> cards) {
        var monthRow = sheet.getRow(1);
        if (monthRow == null) return;

        for (var month : Month.values()) {
            var column = FIRST_BLOCK_COLUMN + BLOCK_WIDTH * month.ordinal();
            var blockMonth = YearMonth.of(year, month);
            var isMonthBlock = monthLabel(month).equals(upper(text(monthRow.getCell(column))));
            var isBeforeFirstBlock = blockMonth.isBefore(firstBlockMonth);
            if (!isMonthBlock || isBeforeFirstBlock) continue;

            readBlock(sheet, column, blockMonth.plusMonths(1), entries, totals, cards);
        }
    }

    private void readBlock(Sheet sheet, int column, YearMonth paymentMonth, List<SpreadsheetEntry> entries, List<SpreadsheetMonthTotals> totals, Map<String, SpreadsheetCard> cards) {
        var section = SheetSection.NONE;
        var cardEntries = new ArrayList<SpreadsheetEntry>();
        String card = null;
        BigDecimal totalToPay = null;
        BigDecimal totalToReceive = null;

        for (var read = FIRST_DATA_ROW; read <= sheet.getLastRowNum(); read++) {
            var row = sheet.getRow(read);
            if (row == null) continue;

            var label = text(row.getCell(column));
            var value = number(row.getCell(column + 1));
            var extra = row.getCell(column + 2);
            var key = upper(label);
            if ("DINHEIRO EM CAIXA".equals(key)) break;

            var header = label == null
                ? null
                : CARD_HEADER.matcher(label.replaceAll("\\s+", " "));

            if (header != null && header.matches()) {
                card = header.group(1).trim();
                cards.put(card, SpreadsheetCard.of(card, Integer.parseInt(header.group(2)), Integer.parseInt(header.group(3))));
                entries.addAll(cardEntries);
                cardEntries.clear();
                section = SheetSection.CARD;
            } else if ("FIXOS".equals(key)) {
                section = SheetSection.FIXED;
            } else if ("GERAIS".equals(key)) {
                section = SheetSection.GENERAL;
            } else if ("TOTAL DA FATURA".equals(key)) {
                if (isPaid(text(extra))) cardEntries.replaceAll(SpreadsheetEntry::markedPaid);

                entries.addAll(cardEntries);
                cardEntries.clear();
                section = SheetSection.NONE;
                continue;
            } else if ("CONTAS FIXAS".equals(key)) {
                section = SheetSection.BILLS;
            } else if ("TOTAL DE CONTAS".equals(key)) {
                section = SheetSection.EXTRAS;
                continue;
            } else if ("TOTAL DE EXTRAS".equals(key)) {
                section = SheetSection.NONE;
                continue;
            } else if ("TOTAL A PAGAR".equals(key)) {
                totalToPay = value;
                section = SheetSection.NONE;
                continue;
            } else if ("SALÁRIO".equals(key)) {
                section = SheetSection.INCOME;
                continue;
            } else if ("TOTAL A RECEBER".equals(key)) {
                totalToReceive = value;
                section = SheetSection.NONE;
                continue;
            }

            if (value == null || value.signum() == 0) continue;

            var entry = entryOf(section, paymentMonth, card, label, key, value, extra);
            if (entry == null) continue;

            // Os itens do cartão esperam o "PG" do total da fatura, que vem depois deles.
            var target = entry.card() == null
                ? entries
                : cardEntries;
            target.add(entry);
        }

        entries.addAll(cardEntries);
        totals.add(SpreadsheetMonthTotals.of(paymentMonth, totalToPay, totalToReceive));
    }

    // Contas fixas e recebimentos carregam o "- PG" na própria descrição; nos extras o "PG" (ou "n/m - PG") vem na terceira coluna.
    private SpreadsheetEntry entryOf(SheetSection section, YearMonth paymentMonth, String card, String label, String key, BigDecimal value, Cell extra) {
        var isCardSection = section == SheetSection.CARD || section == SheetSection.FIXED || section == SheetSection.GENERAL;
        if (isCardSection && card == null) return null;

        return switch (section) {
            case CARD -> SpreadsheetEntry.cardItem(paymentMonth, card, value, installment(extra));
            case FIXED -> SpreadsheetEntry.cardFixed(paymentMonth, card, text(extra), value);
            case GENERAL -> SpreadsheetEntry.cardGeneral(paymentMonth, card, value);
            case BILLS -> SpreadsheetEntry.bill(paymentMonth, withoutPaidMark(text(extra)), value, isPaid(text(extra)));
            case EXTRAS -> label == null || "EXTRAS".equals(key)
                ? null
                : SpreadsheetEntry.extra(paymentMonth, withoutPaidMark(label), value, installment(extra), isPaid(label) || isPaid(text(extra)));
            case INCOME -> label == null
                ? null
                : SpreadsheetEntry.income(paymentMonth, withoutPaidMark(label), value, isPaid(label));
            case NONE -> null;
        };
    }

    // Devolve a parcela, vinda como data (mês = n, dia = m) ou como texto "n/m ...", ou nulo se a célula não for uma parcela.
    private InstallmentMark installment(Cell cell) {
        if (cell == null) return null;

        var type = typeOf(cell);
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            var date = cell.getLocalDateTimeCellValue();
            return installmentMark(date.getMonthValue(), date.getDayOfMonth());
        }

        var text = text(cell);
        if (text == null) return null;

        var matcher = INSTALLMENT_TEXT.matcher(text);
        if (!matcher.find()) return null;

        return installmentMark(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    private InstallmentMark installmentMark(int number, int total) {
        if (!InstallmentMark.isValid(number, total)) return null;

        return InstallmentMark.of(number, total);
    }

    private boolean isPaid(String text) {
        return text != null && PAID_WORD.matcher(text).find();
    }

    private String withoutPaidMark(String text) {
        return text == null
            ? null
            : PAID_MARK.matcher(text).replaceAll("").trim();
    }

    private String monthLabel(Month month) {
        return month.getDisplayName(TextStyle.FULL, PT_BR).toUpperCase(PT_BR);
    }

    private CellType typeOf(Cell cell) {
        return cell.getCellType() == CellType.FORMULA
            ? cell.getCachedFormulaResultType()
            : cell.getCellType();
    }

    private String text(Cell cell) {
        if (cell == null) return null;
        if (typeOf(cell) != CellType.STRING) return null;

        var value = Normalizer.normalize(cell.getStringCellValue(), Normalizer.Form.NFC).trim();
        if (value.isEmpty()) return null;

        return value;
    }

    private String upper(String text) {
        return text == null
            ? null
            : text.toUpperCase(Locale.ROOT);
    }

    // Só número de verdade (os "--------------" e os textos viram nulo).
    // Arredonda em centavos: o valor de uma fórmula pode vir com ruído de ponto flutuante.
    private BigDecimal number(Cell cell) {
        if (cell == null) return null;

        var isNumber = typeOf(cell) == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell);
        if (!isNumber) return null;

        return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
    }

}
