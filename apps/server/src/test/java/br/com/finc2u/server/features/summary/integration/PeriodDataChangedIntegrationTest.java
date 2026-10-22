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
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/*
 * O aviso de dados alterados contra o banco de verdade: o listener roda antes do commit, na transação de quem alterou os
 * dados. Se o recálculo falhar, a alteração inteira é desfeita; resumo que não existe não é criado.
 */
@SpringBootTest
class PeriodDataChangedIntegrationTest {

    // Meses no futuro: o status padrão da despesa é PENDING e não exige informar o pagamento. Salário: 3000 desde 2026-01-01.
    private static final int YEAR = 2027;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ExpenseService expenseService;

    @MockitoSpyBean
    private MonthlySummaryService monthlySummaryService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void event_shouldRecalculateTheExistingSummaries_whenTheTransactionCommits() {
        var userId = registerUser("evento.commit@email.com");
        expense(userId, 500, 3);
        calculateInOrder(userId, 3, 4);

        inTransaction(() -> {
            expense(userId, 200, 3);
            eventPublisher.publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(YEAR, 3)));
        });

        assertEquals(2300, remaining(userId, 3));
        assertEquals(2300, summary(userId, 4).getCashBalance().intValue());
        assertEquals(5300, remaining(userId, 4));
    }

    @Test
    void event_shouldRollBackTheWholeChange_whenTheRecalculationFails() {
        var userId = registerUser("evento.falha@email.com");
        expense(userId, 500, 3);
        calculateInOrder(userId, 3);
        doThrow(new IllegalStateException("falha no recálculo"))
            .when(monthlySummaryService).recalculateExistingFrom(any(UUID.class), any(YearMonth.class));

        assertThrows(IllegalStateException.class, () -> inTransaction(() -> {
            expense(userId, 200, 3);
            eventPublisher.publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(YEAR, 3)));
        }));

        assertEquals(1, expenseService.getByUser(userId).size());
        assertEquals(2500, remaining(userId, 3));
    }

    @Test
    void event_shouldNotCreateASummary_forAUserWhoHasNone() {
        var userId = registerUser("evento.sem.resumo@email.com");
        expense(userId, 500, 3);

        inTransaction(() -> eventPublisher.publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(YEAR, 3))));

        assertTrue(monthlySummaryService.getByUser(userId).isEmpty());
    }

    // A despesa saiu de um mês sem resumo (fevereiro) para um com resumo (abril): o `from` é fevereiro e abril é recalculado.
    @Test
    void event_shouldRecalculateALaterSummary_evenWhenTheMonthOfTheEventHasNone() {
        var userId = registerUser("evento.lacuna@email.com");
        expense(userId, 500, 4);
        calculateInOrder(userId, 4);

        inTransaction(() -> {
            expense(userId, 300, 4);
            eventPublisher.publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(YEAR, 2)));
        });

        assertEquals(2200, remaining(userId, 4));
        assertEquals(List.of(4), monthsOf(userId));
    }

    @Test
    void event_shouldRecalculateRightAway_whenThereIsNoTransactionInProgress() {
        var userId = registerUser("evento.sem.transacao@email.com");
        expense(userId, 500, 3);
        calculateInOrder(userId, 3);
        expense(userId, 200, 3);

        eventPublisher.publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(YEAR, 3)));

        assertEquals(2300, remaining(userId, 3));
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
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

    private void expense(UUID userId, int value, int month) {
        var expense = Expense.builder()
            .description("Despesa")
            .value(BigDecimal.valueOf(value))
            .dueDate(LocalDate.of(YEAR, month, 10))
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PENDING)
            .build();
        var category = categoryService.getByUser(userId).stream()
            .map(Category::getId)
            .findFirst()
            .orElseThrow();
        expenseService.create(userId, new ExpenseCommand(expense, null, null, category, null, null));
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
