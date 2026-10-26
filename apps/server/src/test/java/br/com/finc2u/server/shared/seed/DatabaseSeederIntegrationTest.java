package br.com.finc2u.server.shared.seed;

import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.extraincome.repository.ExtraIncomeRepository;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Sobe o seed completo (o CommandLineRunner roda na subida do contexto) e o repete: a segunda subida não pode duplicar nada.
@SpringBootTest(properties = "app.seeders.enabled=true")
class DatabaseSeederIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }

    }

    @Autowired
    private DatabaseSeeder databaseSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExtraIncomeRepository extraIncomeRepository;

    @Autowired
    private RecurringExpenseRepository recurringExpenseRepository;

    @Autowired
    private MonthlySummaryService monthlySummaryService;

    @Autowired
    private SalaryHistoryService salaryHistoryService;

    @Autowired
    private CategoryService categoryService;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.findByEmail("facina3@gmail.com").orElseThrow();
    }

    @Test
    void seed_shouldGiveTheCurrentMonthExpensesAndExtraIncomes() {
        var summary = monthlySummaryService.recalculateFrom(user.getId(), YearMonth.of(2026, 10)).summary();

        assertTrue(summary.getTotalExpenses().signum() > 0);
        assertTrue(summary.getTotalExtras().signum() > 0);
        // O seed traz despesas vencidas e pendentes e despesas fixas: os dois totais novos têm que aparecer no resumo de hoje.
        assertTrue(summary.getTotalOverdue().signum() > 0);
        assertTrue(summary.getTotalFixed().signum() > 0);
    }

    @Test
    void seed_shouldIncludeAnOverdueExpense() {
        var expenses = expenseRepository.findByUserId(user.getId());

        assertTrue(expenses.stream().anyMatch(expense -> expense.isOverdue(TODAY)));
    }

    // Hoje é 21/10: a Netflix vence no dia 15 de cada mês, então a de outubro já venceu e não nasce atrasada; vale a partir do próximo vencimento.
    @Test
    void seed_shouldMaterializeTheRecurringExpensesFromTheNextDueDateOn() {
        var netflix = expenseRepository.findByUserId(user.getId()).stream()
            .filter(expense -> "Netflix".equals(expense.getDescription()))
            .map(Expense::getDueDate)
            .sorted()
            .toList();

        assertEquals(List.of(LocalDate.of(2026, 11, 15)), netflix);
    }

    @Test
    void seed_shouldNotGenerateAnyRecurringExpenseAlreadyOverdue() {
        var generated = expenseRepository.findByUserId(user.getId()).stream()
            .filter(expense -> expense.getRecurringExpenseId() != null)
            .toList();

        assertFalse(generated.isEmpty());
        assertTrue(generated.stream().noneMatch(expense -> expense.isOverdue(TODAY)));
    }

    @Test
    void seed_shouldPutTheOngoingPurchaseInstallmentsInTheFollowingMonths() {
        var installments = expenseRepository.findByUserId(user.getId()).stream()
            .filter(expense -> "Eletrônico".equals(expense.getDescription()))
            .toList();

        assertEquals(7, installments.size());
        assertTrue(installments.stream().anyMatch(expense -> YearMonth.from(expense.getDueDate()).equals(YearMonth.of(2026, 11))));
    }

    // O usuário do seed é cadastrado pelo caminho de um cadastro real: o primeiro salário e as categorias padrão vêm dos listeners.
    @Test
    void seed_shouldRegisterTheUserLikeARealSignUp_withTheFirstSalaryAndTheDefaultCategories() {
        var salaries = salaryHistoryService.getByUser(user.getId());

        assertEquals(1, salaries.size());
        assertEquals(0, new BigDecimal("3000.00").compareTo(salaries.getFirst().getBaseSalary()));
        assertEquals(LocalDate.of(2026, 1, 1), salaries.getFirst().getEffectiveFrom());
        assertEquals(8, categoryService.getByUser(user.getId()).size());
    }

    @Test
    void seed_shouldBeIdempotent_whenItRunsAgainOnAPopulatedDatabase() {
        var expensesBefore = expenseRepository.findByUserId(user.getId()).size();
        var extraIncomesBefore = extraIncomeRepository.findByUserId(user.getId()).size();
        var modelsBefore = recurringExpenseRepository.findByUserId(user.getId()).size();

        databaseSeeder.run();

        assertEquals(expensesBefore, expenseRepository.findByUserId(user.getId()).size());
        assertEquals(extraIncomesBefore, extraIncomeRepository.findByUserId(user.getId()).size());
        assertEquals(modelsBefore, recurringExpenseRepository.findByUserId(user.getId()).size());
        assertEquals(1, salaryHistoryService.getByUser(user.getId()).size());
        assertEquals(8, categoryService.getByUser(user.getId()).size());
    }

}
