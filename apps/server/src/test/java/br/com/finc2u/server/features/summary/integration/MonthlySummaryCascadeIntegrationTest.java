package br.com.finc2u.server.features.summary.integration;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * A cascata do carryover contra o banco de verdade: o remainingAmount de um mês vira o cashBalance do seguinte, os meses que
 * não têm resumo não são criados, e apagar um resumo faz o seguinte perder o carryover (a consulta antes do cálculo enxerga a
 * exclusão ainda pendente na mesma transação).
 */
@SpringBootTest
class MonthlySummaryCascadeIntegrationTest {

    // Meses no futuro: o status padrão da despesa é PENDING e não exige informar o pagamento. Salário: 3000 desde 2026-01-01.
    private static final int YEAR = 2027;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private MonthlySummaryService monthlySummaryService;

    @Test
    void recalculateFrom_shouldPassTheChangeOfTheMonthOnToTheExistingLaterMonths() {
        var userId = registerUser("cascata.propagar@email.com");
        expenseInMarch(userId, 500);
        calculateInOrder(userId, 3, 4, 5);
        assertEquals(2500, remaining(userId, 3));
        assertEquals(5500, remaining(userId, 4));
        expenseInMarch(userId, 200);

        monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, 3));

        assertEquals(2300, remaining(userId, 3));
        assertEquals(2300, summary(userId, 4).getCashBalance().intValue());
        assertEquals(5300, remaining(userId, 4));
        assertEquals(5300, summary(userId, 5).getCashBalance().intValue());
        assertEquals(8300, remaining(userId, 5));
    }

    @Test
    void recalculateFrom_shouldNotCreateTheMonthsThatHaveNoSummary_andFlagTheOneAfterTheGap() {
        var userId = registerUser("cascata.lacuna@email.com");
        expenseInMarch(userId, 500);
        calculateInOrder(userId, 3, 5);

        monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, 3));

        assertEquals(List.of(3, 5), monthsOf(userId));
        assertTrue(summary(userId, 5).getPreviousMonthMissing());
        assertEquals(0, summary(userId, 5).getCashBalance().intValue());
    }

    @Test
    void recalculateFrom_shouldCreateTheRequestedMonth_whenItHasNoSummary() {
        var userId = registerUser("cascata.criar@email.com");
        expenseInMarch(userId, 500);

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, 3));

        assertTrue(result.created());
        assertEquals(List.of(3), monthsOf(userId));
    }

    @Test
    void delete_shouldMakeTheExistingLaterSummariesLoseTheCarryOverOfTheDeletedOne() {
        var userId = registerUser("cascata.apagar@email.com");
        expenseInMarch(userId, 500);
        calculateInOrder(userId, 3, 4, 5);

        monthlySummaryService.delete(userId, 3, YEAR);

        assertEquals(List.of(4, 5), monthsOf(userId));
        assertTrue(summary(userId, 4).getPreviousMonthMissing());
        assertEquals(0, summary(userId, 4).getCashBalance().intValue());
        assertEquals(3000, remaining(userId, 4));
        assertEquals(3000, summary(userId, 5).getCashBalance().intValue());
        assertEquals(6000, remaining(userId, 5));
    }

    private void calculateInOrder(UUID userId, int... months) {
        for (var month : months) {
            monthlySummaryService.calculateOrRecalculate(userId, month, YEAR);
        }
    }

    private MonthlySummary summary(UUID userId, int month) {
        return monthlySummaryService.getByUserAndPeriod(userId, month, YEAR).summary();
    }

    private int remaining(UUID userId, int month) {
        return summary(userId, month).getRemainingAmount().intValue();
    }

    private List<Integer> monthsOf(UUID userId) {
        return monthlySummaryService.getByUser(userId).stream()
            .map(detail -> detail.summary().getMonth())
            .sorted()
            .toList();
    }

    private void expenseInMarch(UUID userId, int value) {
        var expense = Expense.builder()
            .description("Despesa")
            .value(BigDecimal.valueOf(value))
            .dueDate(LocalDate.of(YEAR, 3, 10))
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PENDING)
            .build();
        var category = categoryService.getByUser(userId).stream()
            .map(Category::getId)
            .findFirst()
            .orElseThrow();
        expenseService.create(userId, ExpenseCommand.of(expense).withCategoryId(category));
    }

    private UUID registerUser(String email) {
        var user = User.builder()
            .name("Ana")
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1)).getId();
    }

}
