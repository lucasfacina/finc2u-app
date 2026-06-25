package br.com.finc2u.server.features.expense.form;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.tag.form.TagResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        String description,
        BigDecimal value,
        LocalDate dueDate,
        LocalDateTime createdAt,
        ExpenseType expenseType,
        PaymentStatus paymentStatus,
        Integer currentInstallment,
        Integer totalInstallment,
        String cardName,
        List<TagResponse> tags
) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getDescription(),
                expense.getValue(),
                expense.getDueDate(),
                expense.getCreatedAt(),
                expense.getExpenseType(),
                expense.getPaymentStatus(),
                expense.getCurrentInstallment(),
                expense.getTotalInstallment(),
                expense.getCardAccount() != null
                        ? expense.getCardAccount().getCardName()
                        : null,
                expense.getTags() != null
                        ? expense.getTags().stream().map(TagResponse::from).toList()
                        : List.of()
        );
    }

}
