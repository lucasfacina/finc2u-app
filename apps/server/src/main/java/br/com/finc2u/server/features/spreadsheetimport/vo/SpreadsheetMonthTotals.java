package br.com.finc2u.server.features.spreadsheetimport.vo;

import java.math.BigDecimal;
import java.time.YearMonth;

public record SpreadsheetMonthTotals(YearMonth paymentMonth, BigDecimal totalToPay, BigDecimal totalToReceive) {

    public static SpreadsheetMonthTotals of(YearMonth paymentMonth, BigDecimal totalToPay, BigDecimal totalToReceive) {
        return new SpreadsheetMonthTotals(paymentMonth, totalToPay, totalToReceive);
    }

}
