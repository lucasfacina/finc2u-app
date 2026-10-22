package br.com.finc2u.server.features.spreadsheetimport.vo;

import java.math.BigDecimal;
import java.time.YearMonth;

public record SpreadsheetMonthTotals(YearMonth paymentMonth, BigDecimal totalToPay, BigDecimal totalToReceive) {

}
