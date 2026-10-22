package br.com.finc2u.server.features.recurringexpense.integration;

import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseSkipRepository;
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

import static org.junit.jupiter.api.Assertions.*;

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

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private RecurringExpenseSkipRepository skipRepository;

    @Test
    void create_shouldMaterializeTheCurrentAndTheNextMonth() {
        var userId = registerUser("recorrente.criar@email.com");

        var model = recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), null), userId);

        var instances = instancesOf(userId);
        assertEquals(List.of(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 25)),
            instances.stream().map(Expense::getDueDate).toList());
        assertTrue(instances.stream().allMatch(expense -> expense.getExpenseType() == ExpenseType.FIXED
            && expense.getPaymentStatus() == PaymentStatus.PENDING
            && model.getId().equals(expense.getRecurringExpenseId())));
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

    // Os resumos que já existiam nos meses das despesas geradas são recalculados, sem ninguém chamar o POST do resumo.
    @Test
    void create_shouldRecalculateTheExistingSummariesOfTheMonthsItGenerates() {
        var userId = registerUser("recorrente.recalcula@email.com");
        monthlySummaryService.calculateOrRecalculate(userId, 10, 2026);
        monthlySummaryService.calculateOrRecalculate(userId, 11, 2026);

        recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), null), userId);

        assertEquals(0, BigDecimal.valueOf(21.90)
            .compareTo(monthlySummaryService.getByUserAndPeriod(userId, 10, 2026).summary().getTotalExpenses()));
        assertEquals(0, BigDecimal.valueOf(21.90)
            .compareTo(monthlySummaryService.getByUserAndPeriod(userId, 11, 2026).summary().getTotalExpenses()));
    }

    // O usuário apagou a despesa de outubro que a assinatura gerou: o vencimento fica registrado como pulado.
    @Test
    void deleteGeneratedExpense_shouldRecordTheSkippedMonth() {
        var userId = registerUser("recorrente.pulo@email.com");
        var model = recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), null), userId);
        var october = instancesOf(userId).getFirst();

        expenseService.delete(october.getId());

        assertTrue(skipRepository.existsByRecurringExpenseIdAndDueDate(model.getId(), LocalDate.of(2026, 10, 25)));
        assertFalse(skipRepository.existsByRecurringExpenseIdAndDueDate(model.getId(), LocalDate.of(2026, 11, 25)));
    }

    // A despesa apagada não volta quando o resumo é recalculado: nem o do próprio mês, nem o do mês seguinte (que olha o anterior).
    @Test
    void deleteGeneratedExpense_shouldNotComeBackWhenTheSummariesAreRecalculated() {
        var userId = registerUser("recorrente.pulo.resumo@email.com");
        recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), null), userId);
        monthlySummaryService.calculateOrRecalculate(userId, 10, 2026);
        var october = instancesOf(userId).getFirst();

        expenseService.delete(october.getId());
        var octoberSummary = monthlySummaryService.calculateOrRecalculate(userId, 10, 2026).summary();
        monthlySummaryService.calculateOrRecalculate(userId, 11, 2026);

        assertEquals(0, BigDecimal.ZERO.compareTo(octoberSummary.getTotalExpenses()));
        assertEquals(List.of(LocalDate.of(2026, 11, 25)), instancesOf(userId).stream().map(Expense::getDueDate).toList());
    }

    @Test
    void deleteExpenseLaunchedByTheUser_shouldNotRecordASkip() {
        var userId = registerUser("recorrente.avulsa@email.com");
        var model = recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), null), userId);
        var launched = Expense.builder()
            .description("Mercado")
            .value(BigDecimal.TEN)
            .dueDate(LocalDate.of(2026, 10, 25))
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PENDING)
            .build();
        var saved = expenseService.create(userId, new ExpenseCommand(launched, null, null,
            categoryService.getByUser(userId).getFirst().getId(), null));

        expenseService.delete(saved.getId());

        assertFalse(skipRepository.existsByRecurringExpenseIdAndDueDate(model.getId(), LocalDate.of(2026, 10, 25)));
    }

    // Apagar o modelo apaga os pulos dele (a FK recusaria o contrário).
    @Test
    void deleteModel_shouldRemoveItsSkips() {
        var userId = registerUser("recorrente.pulo.apagar@email.com");
        var model = recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), null), userId);
        expenseService.delete(instancesOf(userId).getFirst().getId());
        assertTrue(skipRepository.existsByRecurringExpenseIdAndDueDate(model.getId(), LocalDate.of(2026, 10, 25)));

        recurringExpenseService.delete(model.getId());

        assertFalse(skipRepository.existsByRecurringExpenseIdAndDueDate(model.getId(), LocalDate.of(2026, 10, 25)));
        assertTrue(recurringExpenseService.getByUser(userId).isEmpty());
    }

    @Test
    void delete_shouldKeepTheGeneratedExpensesWithoutTheModel() {
        var userId = registerUser("recorrente.apagar@email.com");
        var model = recurringExpenseService.create(subscription(userId, YearMonth.of(2026, 8), null), userId);

        recurringExpenseService.delete(model.getId());

        var instances = instancesOf(userId);
        assertEquals(2, instances.size());
        assertTrue(instances.stream().allMatch(expense -> expense.getRecurringExpenseId() == null));
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
