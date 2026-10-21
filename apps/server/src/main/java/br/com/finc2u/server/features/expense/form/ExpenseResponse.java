package br.com.finc2u.server.features.expense.form;

import br.com.finc2u.server.features.category.form.CategoryResponse;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExpenseResponse(
    UUID id,
    String description,
    BigDecimal value,
    LocalDate dueDate,
    LocalDateTime createdAt,
    ExpenseType expenseType,
    PaymentStatus paymentStatus,
    boolean overdue,
    LocalDate paidAt,
    Integer currentInstallment,
    Integer totalInstallment,
    String cardName,
    CategoryResponse category
) {

    // referenceDate é "hoje" (vem do Clock do controller): o atraso é derivado dela, não lido do relógio aqui.
    public static ExpenseResponse from(Expense expense, LocalDate referenceDate) {
        return new ExpenseResponse(
            expense.getId(),
            expense.getDescription(),
            expense.getValue(),
            expense.getDueDate(),
            expense.getCreatedAt(),
            expense.getExpenseType(),
            expense.getPaymentStatus(),
            expense.isOverdue(referenceDate),
            expense.getPaidAt(),
            expense.getCurrentInstallment(),
            expense.getTotalInstallment(),
            expense.getCardAccount() != null
                ? expense.getCardAccount().getCardName()
                : null,
            CategoryResponse.from(expense.getCategory())
        );
    }

}
