package br.com.finc2u.server.features.recurringexpense.integration;

import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
import br.com.finc2u.server.features.recurringexpense.vo.RecurringExpenseCommand;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RecurringExpenseIntegrationTest {

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
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private RecurringExpenseService recurringExpenseService;

    @Autowired
    private MonthlySummaryService monthlySummaryService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Test
    void create_shouldMaterializeTheCurrentAndTheNextMonth() {
        var userId = registerUser("recorrente.criar@email.com");

        var model = recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), null), userId);

        var instances = instancesOf(userId);
        assertEquals(List.of(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 25)),
            instances.stream().map(Expense::getDueDate).toList());
        assertTrue(instances.stream().allMatch(expense -> expense.getExpenseType() == ExpenseType.FIXED
            && expense.getPaymentStatus() == PaymentStatus.PENDING
            && model.getId().equals(expense.getRecurringExpense().getId())));
    }

    // B1: a assinatura aparece no resumo de um mês futuro, e recalcular de novo não duplica nada.
    @Test
    void monthlySummary_shouldSeeTheSubscriptionOfAFutureMonth_andNeverDuplicateIt() {
        var userId = registerUser("recorrente.resumo@email.com");
        recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), null), userId);

        var december = monthlySummaryService.calculateOrRecalculate(userId, 12, 2026).summary();
        monthlySummaryService.calculateOrRecalculate(userId, 12, 2026);
        var january = monthlySummaryService.calculateOrRecalculate(userId, 1, 2027).summary();

        assertEquals(0, BigDecimal.valueOf(21.90).compareTo(december.getTotalExpenses()));
        assertEquals(0, BigDecimal.valueOf(21.90).compareTo(january.getTotalExpenses()));
        assertEquals(4, instancesOf(userId).size()); // out, nov (na criação), dez e jan
    }

    @Test
    void monthlySummary_shouldNotRecreateTheMonthsThatAlreadyPassed() {
        var userId = registerUser("recorrente.passado@email.com");
        recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), null), userId);

        var september = monthlySummaryService.calculateOrRecalculate(userId, 9, 2026).summary();

        assertEquals(0, BigDecimal.ZERO.compareTo(september.getTotalExpenses()));
        assertEquals(2, instancesOf(userId).size());
    }

    @Test
    void monthlySummary_shouldStopAtTheEndMonthOfTheModel() {
        var userId = registerUser("recorrente.fim@email.com");
        recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), YearMonth.of(2026, 11)), userId);

        var december = monthlySummaryService.calculateOrRecalculate(userId, 12, 2026).summary();

        assertEquals(0, BigDecimal.ZERO.compareTo(december.getTotalExpenses()));
        assertEquals(2, instancesOf(userId).size());
    }

    @Test
    void delete_shouldKeepTheGeneratedExpensesWithoutTheModel() {
        var userId = registerUser("recorrente.apagar@email.com");
        var model = recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), null), userId);

        recurringExpenseService.delete(model.getId());

        var instances = instancesOf(userId);
        assertEquals(2, instances.size());
        assertTrue(instances.stream().allMatch(expense -> expense.getRecurringExpense() == null));
        assertTrue(recurringExpenseService.getByUser(userId).isEmpty());
    }

    private UUID registerUser(String email) {
        var user = User.builder()
            .name("Ana")
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1)).getId();
    }

    private RecurringExpenseCommand subscription(UUID userId, YearMonth startMonth, YearMonth endMonth) {
        var model = RecurringExpense.of("Spotify", BigDecimal.valueOf(21.90), 25, startMonth, endMonth);
        var categoryId = categoryService.getByUser(userId).getFirst().getId();
        return new RecurringExpenseCommand(model, categoryId, null);
    }

    private List<Expense> instancesOf(UUID userId) {
        return expenseRepository.findByUserId(userId).stream()
            .sorted(Comparator.comparing(Expense::getDueDate))
            .toList();
    }

}
