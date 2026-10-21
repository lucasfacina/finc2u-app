package br.com.finc2u.server.features.recurringexpense.form;

import br.com.finc2u.server.features.category.form.CategoryResponse;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

public record RecurringExpenseResponse(
    UUID id,
    String description,
    BigDecimal value,
    Integer dayOfMonth,
    YearMonth startMonth,
    YearMonth endMonth,
    LocalDateTime createdAt,
    String cardName,
    CategoryResponse category
) {

    public static RecurringExpenseResponse from(RecurringExpense recurringExpense) {
        return new RecurringExpenseResponse(
            recurringExpense.getId(),
            recurringExpense.getDescription(),
            recurringExpense.getValue(),
            recurringExpense.getDayOfMonth(),
            YearMonth.from(recurringExpense.getStartMonth()),
            recurringExpense.getEndMonth() != null
                ? YearMonth.from(recurringExpense.getEndMonth())
                : null,
            recurringExpense.getCreatedAt(),
            recurringExpense.getCardAccount() != null
                ? recurringExpense.getCardAccount().getCardName()
                : null,
            CategoryResponse.from(recurringExpense.getCategory())
        );
    }

}
