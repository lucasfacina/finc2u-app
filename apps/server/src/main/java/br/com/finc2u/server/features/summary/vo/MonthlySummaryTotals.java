package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.extra.entity.Extra;

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

    // Agrega os totais a partir das listas de despesas e extras do período.
    public static MonthlySummaryTotals from(List<Expense> expenses, List<Extra> extras) {
        return new MonthlySummaryTotals(
                summarizeExpenses(expenses, expense -> true),
                summarizeExpenses(expenses, expense -> expense.getPaymentStatus() == PaymentStatus.PAID),
                summarizeExpenses(expenses, expense -> expense.getPaymentStatus() == PaymentStatus.PENDING),

                extras.stream()
                        .map(Extra::getValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),

                summarizeExpenses(expenses, expense -> expense.getExpenseType() == ExpenseType.FIXED));
    }

    // Soma os valores das despesas que satisfazem o filtro fornecido.
    private static BigDecimal summarizeExpenses(List<Expense> expenses, Predicate<Expense> filter) {
        return expenses.stream()
                .filter(filter)
                .map(Expense::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
