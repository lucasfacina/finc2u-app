package br.com.finc2u.server.features.spreadsheetimport.vo;

import java.util.List;

public record SpreadsheetData(
    List<SpreadsheetCard> cards,
    List<SpreadsheetEntry> entries,
    List<SpreadsheetMonthTotals> totals
) {

}
