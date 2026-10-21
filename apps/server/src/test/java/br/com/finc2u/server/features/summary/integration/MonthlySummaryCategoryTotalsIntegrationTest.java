package br.com.finc2u.server.features.summary.integration;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Os totais por categoria contra o banco de verdade: gravados no cálculo, substituídos no recálculo (a unique
 * (resumo, categoria) não pode estourar), lidos no histórico fora da transação e apagados junto do resumo (FK).
 */
@SpringBootTest
class MonthlySummaryCategoryTotalsIntegrationTest {

    // Meses no futuro: o status padrão da despesa é PENDING e não exige informar o pagamento.
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
    void calculate_shouldStoreTheTotalOfEachCategory() {
        var userId = registerUser("totais.gravar@email.com");
        expense(userId, "Mercado", 300, 3, "Alimentação");
        expense(userId, "Feira", 50, 3, "Alimentação");
        expense(userId, "Cinema", 70, 3, "Lazer");

        var result = monthlySummaryService.calculateOrRecalculate(userId, 3, YEAR);

        assertEquals(Map.of("Alimentação", 350, "Lazer", 70), totalsByName(result.categoryTotals()));
    }

    @Test
    void recalculate_shouldReplaceTheOldTotals_withoutDuplicatingOrViolatingTheUnique() {
        var userId = registerUser("totais.recalcular@email.com");
        expense(userId, "Mercado", 300, 3, "Alimentação");
        monthlySummaryService.calculateOrRecalculate(userId, 3, YEAR);
        expense(userId, "Cinema", 70, 3, "Lazer");
        expense(userId, "Feira", 50, 3, "Alimentação");

        var result = monthlySummaryService.calculateOrRecalculate(userId, 3, YEAR);
        var stored = monthlySummaryService.getByUserAndPeriod(userId, 3, YEAR).categoryTotals();

        assertFalse(result.created());
        assertEquals(Map.of("Alimentação", 350, "Lazer", 70), totalsByName(stored));
        assertEquals(2, stored.size());
    }

    @Test
    void recalculate_shouldDropTheCategoryThatNoLongerHasExpenses() {
        var userId = registerUser("totais.remover@email.com");
        var cinema = expense(userId, "Cinema", 70, 3, "Lazer");
        expense(userId, "Mercado", 300, 3, "Alimentação");
        monthlySummaryService.calculateOrRecalculate(userId, 3, YEAR);
        expenseService.delete(cinema.getId());

        monthlySummaryService.calculateOrRecalculate(userId, 3, YEAR);
        var stored = monthlySummaryService.getByUserAndPeriod(userId, 3, YEAR).categoryTotals();

        assertEquals(Map.of("Alimentação", 300), totalsByName(stored));
    }

    @Test
    void history_shouldReadTheTotalsOfEverySummary_outsideTheTransaction() {
        var userId = registerUser("totais.historico@email.com");
        expense(userId, "Mercado", 300, 3, "Alimentação");
        expense(userId, "Cinema", 70, 4, "Lazer");
        monthlySummaryService.calculateOrRecalculate(userId, 3, YEAR);
        monthlySummaryService.calculateOrRecalculate(userId, 4, YEAR);

        var history = monthlySummaryService.getByUser(userId);

        assertEquals(2, history.size());
        var byMonth = history.stream().collect(Collectors.toMap(detail -> detail.summary().getMonth(), detail -> detail));
        assertEquals(Map.of("Alimentação", 300), totalsByName(byMonth.get(3).categoryTotals()));
        assertEquals(Map.of("Lazer", 70), totalsByName(byMonth.get(4).categoryTotals()));
    }

    @Test
    void delete_shouldRemoveTheSummaryTogetherWithItsCategoryTotals_andKeepTheCategories() {
        var userId = registerUser("totais.apagar@email.com");
        expense(userId, "Mercado", 300, 3, "Alimentação");
        monthlySummaryService.calculateOrRecalculate(userId, 3, YEAR);

        monthlySummaryService.delete(userId, 3, YEAR);

        assertTrue(monthlySummaryService.getByUser(userId).isEmpty());
        assertEquals(8, categoryService.getByUser(userId).size());
    }

    private Map<String, Integer> totalsByName(List<MonthlySummaryCategoryTotal> totals) {
        return totals.stream().collect(Collectors.toMap(
            total -> total.getCategory().getName(), total -> total.getTotal().intValue()));
    }

    private Expense expense(UUID userId, String description, int value, int month, String categoryName) {
        var expense = Expense.builder()
            .description(description)
            .value(BigDecimal.valueOf(value))
            .dueDate(LocalDate.of(YEAR, month, 10))
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PENDING)
            .build();
        var category = categoryService.getByUser(userId).stream()
            .filter(candidate -> categoryName.equals(candidate.getName()))
            .map(Category::getId)
            .findFirst()
            .orElseThrow();
        return expenseService.create(userId, new ExpenseCommand(expense, null, null, category, null));
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
