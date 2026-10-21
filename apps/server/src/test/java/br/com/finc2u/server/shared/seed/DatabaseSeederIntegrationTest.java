package br.com.finc2u.server.shared.seed;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.extraincome.repository.ExtraIncomeRepository;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Sobe o seed completo (o CommandLineRunner roda na subida do contexto) e o repete: é a "subida 2 vezes" do checklist.
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

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.findByEmail("facina3@gmail.com").orElseThrow();
    }

    @Test
    void seed_shouldGiveTheCurrentMonthExpensesAndExtraIncomes() {
        var summary = monthlySummaryService.calculateOrRecalculate(user.getId(), 10, 2026);

        assertTrue(summary.getTotalExpenses().signum() > 0);
        assertTrue(summary.getTotalExtras().signum() > 0);
    }

    @Test
    void seed_shouldIncludeAnOverdueExpense() {
        var expenses = expenseRepository.findByUserId(user.getId());

        assertTrue(expenses.stream().anyMatch(expense -> expense.isOverdue(TODAY)));
    }

    @Test
    void seed_shouldMaterializeTheRecurringExpensesOfTheCurrentAndTheNextMonth() {
        var netflix = expenseRepository.findByUserId(user.getId()).stream()
            .filter(expense -> "Netflix".equals(expense.getDescription()))
            .map(Expense::getDueDate)
            .sorted()
            .toList();

        assertEquals(List.of(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15)), netflix);
    }

    @Test
    void seed_shouldPutTheOngoingPurchaseInstallmentsInTheFollowingMonths() {
        var installments = expenseRepository.findByUserId(user.getId()).stream()
            .filter(expense -> "Eletrônico".equals(expense.getDescription()))
            .toList();

        assertEquals(7, installments.size());
        assertTrue(installments.stream().anyMatch(expense -> YearMonth.from(expense.getDueDate()).equals(YearMonth.of(2026, 11))));
    }

    @Test
    void seed_shouldBeIdempotent_whenItRunsAgainOnAPopulatedDatabase() {
        var expensesBefore = expenseRepository.findByUserId(user.getId()).size();
        var extraIncomesBefore = extraIncomeRepository.findByUserId(user.getId()).size();
        var modelsBefore = recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(user.getId()).size();

        databaseSeeder.run();

        assertEquals(expensesBefore, expenseRepository.findByUserId(user.getId()).size());
        assertEquals(extraIncomesBefore, extraIncomeRepository.findByUserId(user.getId()).size());
        assertEquals(modelsBefore, recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(user.getId()).size());
    }

}
