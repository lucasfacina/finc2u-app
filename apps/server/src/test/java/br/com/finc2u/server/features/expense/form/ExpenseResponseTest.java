package br.com.finc2u.server.features.expense.form;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseResponseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    private final Category category = Category.of("Compras");

    @Test
    void listOf_shouldConvertEachExpenseKeepingTheOrder() {
        var expenses = List.of(expense("Notebook", TODAY.plusDays(5)), expense("Aluguel", TODAY.plusDays(1)), expense("Spotify", TODAY.plusDays(9)));

        var responses = ExpenseResponse.listOf(expenses, TODAY);

        assertEquals(List.of("Notebook", "Aluguel", "Spotify"), responses.stream().map(ExpenseResponse::description).toList());
    }

    @Test
    void listOf_shouldFlagTheOverdueRelativeToTheReferenceDate() {
        var overdue = expense("Atrasada", TODAY.minusDays(1));
        var onTime = expense("Em dia", TODAY);

        var responses = ExpenseResponse.listOf(List.of(overdue, onTime), TODAY);

        assertTrue(responses.get(0).overdue());
        assertFalse(responses.get(1).overdue());
    }

    @Test
    void listOf_shouldReturnAnEmptyList_whenThereAreNoExpenses() {
        assertEquals(List.of(), ExpenseResponse.listOf(List.of(), TODAY));
    }

    @Test
    void byMonth_shouldConvertTheExpensesOfEachMonthKeepingTheKeys() {
        var july = YearMonth.of(2026, 7);
        var august = YearMonth.of(2026, 8);
        var byMonth = Map.of(
            july, List.of(expense("Notebook 2/3", LocalDate.of(2026, 7, 10)), expense("Celular 1/2", LocalDate.of(2026, 7, 20))),
            august, List.of(expense("Notebook 3/3", LocalDate.of(2026, 8, 10))));

        var responses = ExpenseResponse.byMonth(byMonth, TODAY);

        assertEquals(Set.of(july, august), responses.keySet());
        assertEquals(List.of("Notebook 2/3", "Celular 1/2"), responses.get(july).stream().map(ExpenseResponse::description).toList());
        assertEquals(List.of("Notebook 3/3"), responses.get(august).stream().map(ExpenseResponse::description).toList());
    }

    @Test
    void byMonth_shouldFlagTheOverdueRelativeToTheReferenceDate() {
        var june = YearMonth.of(2026, 6);

        var responses = ExpenseResponse.byMonth(Map.of(june, List.of(expense("Atrasada", TODAY.minusDays(3)))), TODAY);

        assertTrue(responses.get(june).getFirst().overdue());
    }

    @Test
    void byMonth_shouldReturnAnEmptyMap_whenThereAreNoMonths() {
        assertTrue(ExpenseResponse.byMonth(Map.of(), TODAY).isEmpty());
    }

    private Expense expense(String description, LocalDate dueDate) {
        return Expense.builder()
            .description(description)
            .value(BigDecimal.TEN)
            .dueDate(dueDate)
            .paymentStatus(PaymentStatus.PENDING)
            .category(category)
            .build();
    }

}
