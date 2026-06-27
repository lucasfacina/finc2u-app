package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.extra.entity.Extra;
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

    private static Extra extra(BigDecimal value) {
        return Extra.builder()
                .value(value)
                .build();
    }

    @Test
    void from_shouldReturnAllZeros_whenListsAreEmpty() {
        MonthlySummaryTotals totals = MonthlySummaryTotals.from(List.of(), List.of());

        assertEquals(BigDecimal.ZERO, totals.totalExpenses());
        assertEquals(BigDecimal.ZERO, totals.totalPaid());
        assertEquals(BigDecimal.ZERO, totals.totalPending());
        assertEquals(BigDecimal.ZERO, totals.totalExtras());
        assertEquals(BigDecimal.ZERO, totals.totalFixed());
    }

    @Test
    void from_shouldSumAllExpenses_forTotalExpenses() {
        List<Expense> expenses = List.of(
                expense(BigDecimal.valueOf(100), ExpenseType.FIXED, PaymentStatus.PAID),
                expense(BigDecimal.valueOf(200), ExpenseType.VARIABLE, PaymentStatus.PENDING)
        );

        MonthlySummaryTotals totals = MonthlySummaryTotals.from(expenses, List.of());

        assertEquals(0, BigDecimal.valueOf(300).compareTo(totals.totalExpenses()));
    }

    @Test
    void from_shouldSeparatePaidAndPending() {
        List<Expense> expenses = List.of(
                expense(BigDecimal.valueOf(150), ExpenseType.VARIABLE, PaymentStatus.PAID),
                expense(BigDecimal.valueOf(80), ExpenseType.VARIABLE, PaymentStatus.PENDING),
                expense(BigDecimal.valueOf(70), ExpenseType.VARIABLE, PaymentStatus.PENDING)
        );

        MonthlySummaryTotals totals = MonthlySummaryTotals.from(expenses, List.of());

        assertEquals(0, BigDecimal.valueOf(150).compareTo(totals.totalPaid()));
        assertEquals(0, BigDecimal.valueOf(150).compareTo(totals.totalPending()));
    }

    @Test
    void from_shouldSumOnlyFixed_forTotalFixed() {
        List<Expense> expenses = List.of(
                expense(BigDecimal.valueOf(50), ExpenseType.FIXED, PaymentStatus.PAID),
                expense(BigDecimal.valueOf(30), ExpenseType.FIXED, PaymentStatus.PENDING),
                expense(BigDecimal.valueOf(200), ExpenseType.VARIABLE, PaymentStatus.PAID),
                expense(BigDecimal.valueOf(100), ExpenseType.PARCELED, PaymentStatus.PENDING)
        );

        MonthlySummaryTotals totals = MonthlySummaryTotals.from(expenses, List.of());

        assertEquals(0, BigDecimal.valueOf(80).compareTo(totals.totalFixed()));
    }

    @Test
    void from_shouldSumAllExtras() {
        List<Extra> extras = List.of(extra(BigDecimal.valueOf(500)), extra(BigDecimal.valueOf(300)));

        MonthlySummaryTotals totals = MonthlySummaryTotals.from(List.of(), extras);

        assertEquals(0, BigDecimal.valueOf(800).compareTo(totals.totalExtras()));
    }

}