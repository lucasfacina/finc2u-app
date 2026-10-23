package br.com.finc2u.server.features.spreadsheetimport.vo;

public record SpreadsheetCard(String name, int closingDay, int dueDay) {

    public static SpreadsheetCard of(String name, int closingDay, int dueDay) {
        return new SpreadsheetCard(name, closingDay, dueDay);
    }

}
