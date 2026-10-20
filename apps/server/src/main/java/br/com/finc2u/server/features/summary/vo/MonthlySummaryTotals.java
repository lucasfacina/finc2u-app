package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;

public record MonthlySummaryTotals(
    BigDecimal totalExpenses,
    BigDecimal totalPaid,
    BigDecimal totalPending,
    BigDecimal totalExtras,
    BigDecimal totalFixed
) {

    public static MonthlySummaryTotals from(List<Expense> expenses, List<ExtraIncome> extraIncomes) {
        return new MonthlySummaryTotals(
            summarizeExpenses(expenses, expense -> true),
            summarizeExpenses(expenses, expense -> expense.getPaymentStatus() == PaymentStatus.PAID),
            summarizeExpenses(expenses, expense -> expense.getPaymentStatus() == PaymentStatus.PENDING),

            extraIncomes.stream()
                .map(ExtraIncome::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add),

            summarizeExpenses(expenses, expense -> expense.getExpenseType() == ExpenseType.FIXED));
    }

    private static BigDecimal summarizeExpenses(List<Expense> expenses, Predicate<Expense> filter) {
        return expenses.stream()
            .filter(filter)
            .map(Expense::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
