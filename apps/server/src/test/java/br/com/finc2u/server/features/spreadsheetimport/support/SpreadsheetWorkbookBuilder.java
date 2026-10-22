package br.com.finc2u.server.features.spreadsheetimport.support;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/*
 * Monta em memória uma planilha sintética no leiaute da planilha real (uma aba "GASTOS <ano>", um bloco de 4 colunas por
 * mês, linhas a partir da 5ª), para os testes não dependerem de nenhum arquivo pessoal. Cada `line` ocupa a próxima linha
 * do bloco: número vira número, `LocalDate` vira data, texto iniciado por "=" vira fórmula (com o valor calculado salvo, como
 * o Excel faz) e o resto vira texto.
 */
public class SpreadsheetWorkbookBuilder {

    private static final String[] MONTHS = {"JANEIRO", "FEVEREIRO", "MARÇO", "ABRIL", "MAIO", "JUNHO", "JULHO", "AGOSTO",
        "SETEMBRO", "OUTUBRO", "NOVEMBRO", "DEZEMBRO"};

    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final CellStyle dateStyle = workbook.createCellStyle();

    public SpreadsheetWorkbookBuilder() {
        CreationHelper helper = workbook.getCreationHelper();
        dateStyle.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy"));
    }

    public Block month(int year, int month) {
        Sheet sheet = workbook.getSheet("GASTOS " + year);
        if (sheet == null) {
            sheet = workbook.createSheet("GASTOS " + year);
        }
        Block block = new Block(sheet, 2 + 4 * (month - 1));
        Row header = sheet.getRow(1) == null
            ? sheet.createRow(1)
            : sheet.getRow(1);
        header.createCell(block.column).setCellValue(MONTHS[month - 1]);
        return block;
    }

    // Uma aba com outro nome ou de outro leiaute, que o leitor tem de ignorar.
    public SpreadsheetWorkbookBuilder otherSheet(String name) {
        workbook.createSheet(name).createRow(1).createCell(2).setCellValue("JANEIRO");
        return this;
    }

    public Path build(Path file) {
        workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
        try (OutputStream out = Files.newOutputStream(file)) {
            workbook.write(out);
            workbook.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    public class Block {

        private final Sheet sheet;
        private final int column;
        private int nextRow = 4;

        private Block(Sheet sheet, int column) {
            this.sheet = sheet;
            this.column = column;
        }

        public Block line(Object label, Object value, Object observation) {
            Row row = sheet.getRow(nextRow) == null
                ? sheet.createRow(nextRow)
                : sheet.getRow(nextRow);
            nextRow++;
            write(row, column, label);
            write(row, column + 1, value);
            write(row, column + 2, observation);
            return this;
        }

        private void write(Row row, int index, Object content) {
            if (content == null) {
                return;
            }
            var cell = row.createCell(index);
            switch (content) {
                case Number number -> cell.setCellValue(number.doubleValue());
                case LocalDate date -> {
                    cell.setCellValue(date);
                    cell.setCellStyle(dateStyle);
                }
                case String text when text.startsWith("=") -> cell.setCellFormula(text.substring(1));
                default -> cell.setCellValue(content.toString());
            }
        }

    }

}
