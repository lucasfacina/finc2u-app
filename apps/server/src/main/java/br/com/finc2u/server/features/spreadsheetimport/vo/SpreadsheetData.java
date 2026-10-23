package br.com.finc2u.server.features.spreadsheetimport.vo;

import java.util.List;

public record SpreadsheetData(
    List<SpreadsheetCard> cards,
    List<SpreadsheetEntry> entries,
    List<SpreadsheetMonthTotals> totals
) {

    public static SpreadsheetData of(List<SpreadsheetCard> cards, List<SpreadsheetEntry> entries, List<SpreadsheetMonthTotals> totals) {
        return new SpreadsheetData(List.copyOf(cards), List.copyOf(entries), List.copyOf(totals));
    }

}
