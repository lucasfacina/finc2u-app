package br.com.finc2u.server.features.spreadsheetimport.reader;

import br.com.finc2u.server.features.spreadsheetimport.enums.EntryKind;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetCard;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetData;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetEntry;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetMonthTotals;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
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

    private static final List<String> MONTHS =
        List.of("JANEIRO", "FEVEREIRO", "MARÇO", "ABRIL", "MAIO", "JUNHO", "JULHO",
            "AGOSTO", "SETEMBRO", "OUTUBRO", "NOVEMBRO", "DEZEMBRO"
        );

    private static final Pattern SHEET_NAME =
        Pattern.compile("^GASTOS (\\d{4})$");

    private static final Pattern CARD_HEADER =
        Pattern.compile("^(.+?)\\s*Fechamento:\\s*(\\d{1,2})\\s*Vencimento:\\s*(\\d{1,2})$");

    private static final Pattern INSTALLMENT_TEXT =
        Pattern.compile("^(\\d{1,2})/(\\d{1,3})\\b");

    private static final Pattern PAID_MARK =
        Pattern.compile("\\s*-?\\s*\\bPG\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PAID_WORD =
        Pattern.compile("\\bPG\\b", Pattern.CASE_INSENSITIVE);

    private enum Section {NONE, CARD, FIXED, GENERAL, BILLS, EXTRAS, INCOME}

    public SpreadsheetData read(Path file, YearMonth firstBlockMonth) {
        validate(file);
        List<SpreadsheetEntry> entries = new ArrayList<>();
        List<SpreadsheetMonthTotals> totals = new ArrayList<>();
        Map<String, SpreadsheetCard> cards = new LinkedHashMap<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(OPCPackage.open(file.toFile(), PackageAccess.READ))) {
            for (Sheet sheet : workbook) {
                Matcher name = SHEET_NAME.matcher(sheet.getSheetName().trim());
                if (name.matches()) {
                    readSheet(sheet, Integer.parseInt(name.group(1)), firstBlockMonth, entries, totals, cards);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler a planilha.", e);
        } catch (InvalidFormatException e) {
            throw new IllegalArgumentException("O arquivo não é uma planilha .xlsx válida.", e);
        }
        return new SpreadsheetData(List.copyOf(cards.values()), List.copyOf(entries), List.copyOf(totals));
    }

    private void validate(Path file) {
        if (!Files.isRegularFile(file) || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("A planilha deve ser um arquivo .xlsx existente.");
        }
        try {
            if (Files.size(file) > MAX_BYTES) {
                throw new IllegalArgumentException("A planilha passa do tamanho máximo de 10 MB.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler a planilha.", e);
        }
    }

    private void readSheet(Sheet sheet, int year, YearMonth firstBlockMonth, List<SpreadsheetEntry> entries,
                           List<SpreadsheetMonthTotals> totals, Map<String, SpreadsheetCard> cards) {
        Row monthRow = sheet.getRow(1);
        if (monthRow == null) {
            return;
        }
        for (int i = 0; i < MONTHS.size(); i++) {
            int column = FIRST_BLOCK_COLUMN + BLOCK_WIDTH * i;
            YearMonth blockMonth = YearMonth.of(year, i + 1);
            if (!MONTHS.get(i).equals(upper(text(monthRow.getCell(column)))) || blockMonth.isBefore(firstBlockMonth)) {
                continue;
            }
            readBlock(sheet, column, blockMonth.plusMonths(1), entries, totals, cards);
        }
    }

    private void readBlock(Sheet sheet, int column, YearMonth paymentMonth, List<SpreadsheetEntry> entries,
                           List<SpreadsheetMonthTotals> totals, Map<String, SpreadsheetCard> cards) {
        Section section = Section.NONE;
        String card = null;
        List<SpreadsheetEntry> cardEntries = new ArrayList<>();
        BigDecimal totalToPay = null;
        BigDecimal totalToReceive = null;
        for (int read = FIRST_DATA_ROW; read <= sheet.getLastRowNum(); read++) {
            Row row = sheet.getRow(read);
            if (row == null) {
                continue;
            }
            String label = text(row.getCell(column));
            BigDecimal value = number(row.getCell(column + 1));
            Cell extra = row.getCell(column + 2);
            String key = upper(label);

            if ("DINHEIRO EM CAIXA".equals(key)) {
                break;
            }
            Matcher header = label == null
                ? null
                : CARD_HEADER.matcher(label.replaceAll("\\s+", " "));
            if (header != null && header.matches()) {
                card = header.group(1).trim();
                cards.put(card, new SpreadsheetCard(card, Integer.parseInt(header.group(2)), Integer.parseInt(header.group(3))));
                entries.addAll(cardEntries);
                cardEntries.clear();
                section = Section.CARD;
            } else if ("FIXOS".equals(key)) {
                section = Section.FIXED;
            } else if ("GERAIS".equals(key)) {
                section = Section.GENERAL;
            } else if ("TOTAL DA FATURA".equals(key)) {
                if (isPaid(text(extra))) {
                    cardEntries.replaceAll(SpreadsheetEntry::markedPaid);
                }
                entries.addAll(cardEntries);
                cardEntries.clear();
                section = Section.NONE;
                continue;
            } else if ("CONTAS FIXAS".equals(key)) {
                section = Section.BILLS;
            } else if ("TOTAL DE CONTAS".equals(key)) {
                section = Section.EXTRAS;
                continue;
            } else if ("TOTAL DE EXTRAS".equals(key)) {
                section = Section.NONE;
                continue;
            } else if ("TOTAL A PAGAR".equals(key)) {
                totalToPay = value;
                section = Section.NONE;
                continue;
            } else if ("SALÁRIO".equals(key)) {
                section = Section.INCOME;
                continue;
            } else if ("TOTAL A RECEBER".equals(key)) {
                totalToReceive = value;
                section = Section.NONE;
                continue;
            }

            if (value == null || value.signum() == 0) {
                continue;
            }
            SpreadsheetEntry entry = switch (section) {
                case CARD -> cardEntry(
                    EntryKind.CARD_ITEM,
                    paymentMonth,
                    card,
                    null, //description
                    value,
                    extra
                );

                case FIXED -> cardEntry(
                    EntryKind.CARD_FIXED,
                    paymentMonth,
                    card,
                    text(extra),
                    value,
                    null // parcel
                );

                case GENERAL -> cardEntry(
                    EntryKind.CARD_GENERAL,
                    paymentMonth,
                    card,
                    null, // description
                    value,
                    null // parcel
                );

                case BILLS -> entry(
                    EntryKind.BILL,
                    paymentMonth,
                    text(extra),
                    value,
                    null // observation
                );

                case EXTRAS -> label == null || "EXTRAS".equals(key)
                    ? null
                    : entry(EntryKind.EXTRA, paymentMonth, label, value, extra);

                case INCOME -> label == null
                    ? null
                    : entry(EntryKind.INCOME, paymentMonth, label, value, null);

                case NONE -> null;
            };
            if (entry == null) {
                continue;
            }
            // Os itens do cartão esperam o "PG" do total da fatura, que vem depois deles.
            (entry.card() == null
                ? entries
                : cardEntries).add(entry);
        }
        entries.addAll(cardEntries);
        totals.add(new SpreadsheetMonthTotals(paymentMonth, totalToPay, totalToReceive));
    }

    private SpreadsheetEntry cardEntry(EntryKind kind, YearMonth paymentMonth, String card, String description, BigDecimal value, Cell parcel) {
        if (card == null) {
            return null;
        }
        int[] installment = installment(parcel);
        return new SpreadsheetEntry(paymentMonth, kind, card, description, value,
            installment == null
                ? null
                : installment[0],
            installment == null
                ? null
                : installment[1], false);
    }

    // Contas fixas e recebimentos carregam o "- PG" na própria descrição; nos extras o "PG" (ou "n/m - PG") vem na terceira coluna.
    private SpreadsheetEntry entry(EntryKind kind, YearMonth paymentMonth, String description, BigDecimal value, Cell observation) {
        boolean paid = isPaid(description) || isPaid(text(observation));
        int[] installment = installment(observation);
        return new SpreadsheetEntry(paymentMonth, kind, null, withoutPaidMark(description), value,
            installment == null
                ? null
                : installment[0],
            installment == null
                ? null
                : installment[1], paid);
    }

    // Devolve {n, m} da parcela, vinda como data (mês = n, dia = m) ou como texto "n/m ...", ou nulo se a célula não for uma parcela.
    private int[] installment(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA
            ? cell.getCachedFormulaResultType()
            : cell.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            var date = cell.getLocalDateTimeCellValue();
            return validInstallment(date.getMonthValue(), date.getDayOfMonth());
        }
        String text = text(cell);
        if (text != null) {
            Matcher matcher = INSTALLMENT_TEXT.matcher(text);
            if (matcher.find()) {
                return validInstallment(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            }
        }
        return null;
    }

    private int[] validInstallment(int number, int total) {
        return number >= 1 && number <= total
            ? new int[]{number, total}
            : null;
    }

    private boolean isPaid(String text) {
        return text != null && PAID_WORD.matcher(text).find();
    }

    private String withoutPaidMark(String text) {
        return text == null
            ? null
            : PAID_MARK.matcher(text).replaceAll("").trim();
    }

    private String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type != CellType.STRING) {
            return null;
        }
        String value = Normalizer.normalize(cell.getStringCellValue(), Normalizer.Form.NFC).trim();
        return value.isEmpty()
            ? null
            : value;
    }

    private String upper(String text) {
        return text == null
            ? null
            : text.toUpperCase(Locale.ROOT);
    }

    // Só número de verdade (os "--------------" e os textos viram nulo). Arredonda em centavos: o valor de uma fórmula pode vir com ruído de ponto flutuante.
    private BigDecimal number(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type != CellType.NUMERIC || DateUtil.isCellDateFormatted(cell)) {
            return null;
        }
        return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
    }

}
