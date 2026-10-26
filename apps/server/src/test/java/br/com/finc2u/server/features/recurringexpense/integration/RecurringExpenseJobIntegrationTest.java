package br.com.finc2u.server.features.recurringexpense.integration;

import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.job.RecurringExpenseJob;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;

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

@SpringBootTest(properties = "app.scheduling.enabled=true")
class RecurringExpenseJobIntegrationTest {

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
    private RecurringExpenseRepository recurringExpenseRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private RecurringExpenseJob recurringExpenseJob;

    @Autowired
    private MonthlySummaryService monthlySummaryService;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

    // Sobe o contexto com o agendamento ligado: o cron e o fuso (SpEL do bean clock) precisam resolver para ele subir.
    @Test
    void shouldScheduleTheJobForTheFirstDayOfEveryMonthAt3am() {
        var cronExpressions = scheduledAnnotationBeanPostProcessor.getScheduledTasks().stream()
            .map(ScheduledTask::getTask)
            .filter(CronTask.class::isInstance)
            .map(task -> ((CronTask) task).getExpression())
            .toList();

        assertTrue(cronExpressions.contains("0 0 3 1 * *"));
    }

    @Test
    void job_shouldMaterializeTheActiveModelsAndIgnoreTheEndedOnes() {
        var userId = registerUser("job.ativos@email.com");
        saveModel(userId, "Spotify", YearMonth.of(2026, 8), null);
        saveModel(userId, "Antigo", YearMonth.of(2026, 1), YearMonth.of(2026, 9));

        recurringExpenseJob.materializeActiveRecurringExpenses();

        var instances = instancesOf(userId);
        assertEquals(List.of(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 25)),
            instances.stream().map(Expense::getDueDate).toList());
        assertTrue(instances.stream().allMatch(expense -> "Spotify".equals(expense.getDescription())));
    }

    @Test
    void job_shouldNotDuplicateTheExpenses_whenItRunsAgain() {
        var userId = registerUser("job.repetido@email.com");
        saveModel(userId, "Spotify", YearMonth.of(2026, 8), null);

        recurringExpenseJob.materializeActiveRecurringExpenses();
        recurringExpenseJob.materializeActiveRecurringExpenses();

        assertEquals(2, instancesOf(userId).size());
    }

    // O usuário apagou a despesa de outubro: o job do mês seguinte não a recria, e a de novembro continua valendo.
    @Test
    void job_shouldNotRecreateTheMonthTheUserDeleted() {
        var userId = registerUser("job.pulo@email.com");
        saveModel(userId, "Spotify", YearMonth.of(2026, 8), null);
        recurringExpenseJob.materializeActiveRecurringExpenses();
        expenseService.delete(instancesOf(userId).getFirst().getId());

        recurringExpenseJob.materializeActiveRecurringExpenses();

        assertEquals(List.of(LocalDate.of(2026, 11, 25)), instancesOf(userId).stream().map(Expense::getDueDate).toList());
    }

    // O job é um gatilho de fundo: as despesas que ele gera entram nos resumos que já existem, sem ninguém pedir.
    @Test
    void job_shouldRecalculateTheExistingSummariesOfTheMonthsItGenerates() {
        var userId = registerUser("job.resumo@email.com");
        monthlySummaryService.recalculateFrom(userId, YearMonth.of(2026, 10));
        saveModel(userId, "Spotify", YearMonth.of(2026, 8), null);

        recurringExpenseJob.materializeActiveRecurringExpenses();

        assertEquals(0, BigDecimal.valueOf(21.90)
            .compareTo(monthlySummaryService.getByUserAndPeriod(userId, 10, 2026).summary().getTotalExpenses()));
    }

    private UUID registerUser(String email) {
        var user = User.builder()
            .name("Ana")
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1)).getId();
    }

    // Grava o modelo direto no repositório, sem passar pelo service: assim nenhuma despesa nasce antes do job.
    private void saveModel(UUID userId, String description, YearMonth startMonth, YearMonth endMonth) {
        var model = RecurringExpense.of(description, BigDecimal.valueOf(21.90), 25, startMonth, endMonth);
        model.setUser(userService.getById(userId));
        model.setCategory(categoryService.getByUser(userId).getFirst());
        recurringExpenseRepository.save(model);
    }

    private List<Expense> instancesOf(UUID userId) {
        return expenseRepository.findByUserId(userId).stream()
            .sorted(Comparator.comparing(Expense::getDueDate))
            .toList();
    }

}
