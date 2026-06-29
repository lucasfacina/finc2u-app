package br.com.finc2u.server.features.expense.filter;

import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseFilter(
        Integer month,
        Integer year,
        PaymentStatus status,
        ExpenseType type,
        UUID cardAccountId,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
