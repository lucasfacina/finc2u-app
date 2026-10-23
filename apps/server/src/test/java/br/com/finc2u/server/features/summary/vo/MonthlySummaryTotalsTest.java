package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonthlySummaryTotalsTest {

    private static Expense expense(BigDecimal value, ExpenseType type, PaymentStatus status) {
        return Expense.builder()
            .value(value)
            .expenseType(type)
            .paymentStatus(status)
            .build();
    }

    private static ExtraIncome extraIncome(BigDecimal value) {
        return ExtraIncome.builder()
            .value(value)
            .build();
    }

    @Test
    void from_shouldReturnAllZeros_whenListsAreEmpty() {
        var totals = MonthlySummaryTotals.from(List.of(), List.of());

        assertEquals(BigDecimal.ZERO, totals.totalExpenses());
        assertEquals(BigDecimal.ZERO, totals.totalPaid());
        assertEquals(BigDecimal.ZERO, totals.totalPending());
        assertEquals(BigDecimal.ZERO, totals.totalExtras());
        assertEquals(BigDecimal.ZERO, totals.totalFixed());
    }

    @Test
    void from_shouldSumAllExpenses_forTotalExpenses() {
        var expenses = List.of(
            expense(BigDecimal.valueOf(100), ExpenseType.FIXED, PaymentStatus.PAID),
            expense(BigDecimal.valueOf(200), ExpenseType.VARIABLE, PaymentStatus.PENDING)
        );

        var totals = MonthlySummaryTotals.from(expenses, List.of());

        assertEquals(0, BigDecimal.valueOf(300).compareTo(totals.totalExpenses()));
    }

    @Test
    void from_shouldSeparatePaidAndPending() {
        var expenses = List.of(
            expense(BigDecimal.valueOf(150), ExpenseType.VARIABLE, PaymentStatus.PAID),
            expense(BigDecimal.valueOf(80), ExpenseType.VARIABLE, PaymentStatus.PENDING),
            expense(BigDecimal.valueOf(70), ExpenseType.VARIABLE, PaymentStatus.PENDING)
        );

        var totals = MonthlySummaryTotals.from(expenses, List.of());

        assertEquals(0, BigDecimal.valueOf(150).compareTo(totals.totalPaid()));
        assertEquals(0, BigDecimal.valueOf(150).compareTo(totals.totalPending()));
    }

    @Test
    void from_shouldSumOnlyFixed_forTotalFixed() {
        var expenses = List.of(
            expense(BigDecimal.valueOf(50), ExpenseType.FIXED, PaymentStatus.PAID),
            expense(BigDecimal.valueOf(30), ExpenseType.FIXED, PaymentStatus.PENDING),
            expense(BigDecimal.valueOf(200), ExpenseType.VARIABLE, PaymentStatus.PAID),
            expense(BigDecimal.valueOf(100), ExpenseType.PARCELED, PaymentStatus.PENDING)
        );

        var totals = MonthlySummaryTotals.from(expenses, List.of());

        assertEquals(0, BigDecimal.valueOf(80).compareTo(totals.totalFixed()));
    }

    @Test
    void from_shouldSumAllExtras() {
        var extraIncomes = List.of(extraIncome(BigDecimal.valueOf(500)), extraIncome(BigDecimal.valueOf(300)));

        var totals = MonthlySummaryTotals.from(List.of(), extraIncomes);

        assertEquals(0, BigDecimal.valueOf(800).compareTo(totals.totalExtras()));
    }

}
