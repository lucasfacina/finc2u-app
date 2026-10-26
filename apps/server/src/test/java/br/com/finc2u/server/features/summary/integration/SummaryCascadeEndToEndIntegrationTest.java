package br.com.finc2u.server.features.summary.integration;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * A cascata de ponta a ponta: quem altera os dados (despesa, ganho extra, salário) avisa, o listener recalcula os resumos que já
 * existem dos meses afetados em diante, e a próxima leitura já enxerga o carryover novo, sem ninguém chamar o POST do resumo.
 * Salário de 3000 desde 2026-01-01; abril e maio só existem se o teste os calcula (não são criados pela cascata).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SummaryCascadeEndToEndIntegrationTest {

    // Meses no futuro: o status padrão da despesa é PENDING e não exige informar o pagamento.
    private static final int YEAR = 2027;

    // Guarda os avisos publicados, para provar que o cálculo do resumo não publica de novo (sem recursão).
    @TestConfiguration
    static class PublishedEventsConfig {

        @Bean
        PublishedEvents publishedEvents() {
            return new PublishedEvents();
        }

    }

    static class PublishedEvents {

        private final List<PeriodDataChangedEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(PeriodDataChangedEvent event) {
            events.add(event);
        }

        List<PeriodDataChangedEvent> of(UUID userId) {
            return events.stream().filter(event -> userId.equals(event.userId())).toList();
        }

    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PublishedEvents publishedEvents;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private ExtraIncomeService extraIncomeService;

    @Autowired
    private SalaryHistoryService salaryHistoryService;

    @Autowired
    private RecurringExpenseRepository recurringExpenseRepository;

    @Autowired
    private MonthlySummaryService monthlySummaryService;

    @BeforeEach
    void clearPublishedEvents() {
        publishedEvents.events.clear();
    }

    // ---- pelo HTTP: o que o cliente enxerga ----

    @Test
    void postExpense_shouldUpdateTheSummariesOfTheMonthAndOfTheFollowingOnes() throws Exception {
        var userId = registerUser("ponta.http.despesa@email.com");
        expense(userId, 500, 3);
        calculateInOrder(userId, 3, 4, 5);

        mockMvc.perform(post("/expenses").param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"description": "Mercado", "value": 200, "expenseType": "VARIABLE",
                     "dueDate": "%d-03-15", "categoryId": "%s"}
                    """.formatted(YEAR, firstCategoryId(userId))))
            .andExpect(status().isCreated());

        getSummary(userId, 3).andExpect(jsonPath("$.remainingAmount").value(2300));
        getSummary(userId, 4).andExpect(jsonPath("$.cashBalance").value(2300))
            .andExpect(jsonPath("$.remainingAmount").value(5300));
        getSummary(userId, 5).andExpect(jsonPath("$.cashBalance").value(5300))
            .andExpect(jsonPath("$.remainingAmount").value(8300));
    }

    @Test
    void deleteSummary_shouldMakeTheNextExistingOneLoseTheCarryOver() throws Exception {
        var userId = registerUser("ponta.http.apagar@email.com");
        expense(userId, 500, 3);
        calculateInOrder(userId, 3, 4);

        mockMvc.perform(delete("/monthly-summary/{userId}/{year}/{month}", userId, YEAR, 3))
            .andExpect(status().isNoContent());

        getSummary(userId, 4).andExpect(jsonPath("$.previousMonthMissing").value(true))
            .andExpect(jsonPath("$.cashBalance").value(0))
            .andExpect(jsonPath("$.remainingAmount").value(3000));
    }

    // ---- pelos services: cada operação que altera dados do período ----

    @Test
    void updateExpense_shouldRecalculateFromTheEarlierOfTheTwoMonths() {
        var userId = registerUser("ponta.despesa.mover@email.com");
        expense(userId, 500, 3);
        var may = expense(userId, 400, 5);
        calculateInOrder(userId, 3, 4, 5);
        assertEquals(8100, remaining(userId, 5));

        var moved = Expense.builder()
            .description("Despesa")
            .value(BigDecimal.valueOf(400))
            .dueDate(LocalDate.of(YEAR, 3, 20))
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PENDING)
            .build();
        expenseService.update(may.getId(), ExpenseCommand.of(moved).withCategoryId(firstCategoryId(userId)));

        assertEquals(2100, remaining(userId, 3));
        assertEquals(2100, summary(userId, 4).getCashBalance().intValue());
        assertEquals(5100, remaining(userId, 4));
        assertEquals(0, summary(userId, 5).getTotalExpenses().intValue());
        assertEquals(8100, remaining(userId, 5));
    }

    @Test
    void updateStatus_shouldMoveTheValueFromPendingToPaidInTheSummary() {
        var userId = registerUser("ponta.despesa.status@email.com");
        var march = expense(userId, 500, 3);
        calculateInOrder(userId, 3);
        assertEquals(500, summary(userId, 3).getTotalPending().intValue());

        expenseService.updateStatus(march.getId(), PaymentStatus.PAID, null);

        assertEquals(500, summary(userId, 3).getTotalPaid().intValue());
        assertEquals(0, summary(userId, 3).getTotalPending().intValue());
    }

    @Test
    void deleteExpense_shouldGiveTheValueBackToTheMonthAndToTheFollowingOnes() {
        var userId = registerUser("ponta.despesa.apagar@email.com");
        var march = expense(userId, 500, 3);
        calculateInOrder(userId, 3, 4);

        expenseService.delete(march.getId());

        assertEquals(3000, remaining(userId, 3));
        assertEquals(3000, summary(userId, 4).getCashBalance().intValue());
        assertEquals(6000, remaining(userId, 4));
    }

    @Test
    void installmentPurchase_shouldReachEveryMonthWithAnInstallment_andItsRemovalToo() {
        var userId = registerUser("ponta.parcelada@email.com");
        calculateInOrder(userId, 3, 4, 5);
        var purchase = Expense.builder()
            .description("Notebook")
            .value(BigDecimal.valueOf(300))
            .dueDate(LocalDate.of(YEAR, 3, 10))
            .expenseType(ExpenseType.PARCELED)
            .paymentStatus(PaymentStatus.PENDING)
            .currentInstallment(1)
            .totalInstallment(3)
            .build();

        var first = expenseService.create(userId, ExpenseCommand.of(purchase).withCategoryId(firstCategoryId(userId)));

        assertEquals(2700, remaining(userId, 3));
        assertEquals(5400, remaining(userId, 4));
        assertEquals(8100, remaining(userId, 5));

        expenseService.deleteInstallmentGroup(first.getInstallmentGroupId());

        assertEquals(3000, remaining(userId, 3));
        assertEquals(6000, remaining(userId, 4));
        assertEquals(9000, remaining(userId, 5));
    }

    @Test
    void extraIncome_shouldReachTheMonthAndTheFollowingOnes_butNotThePreviousOnes() {
        var userId = registerUser("ponta.ganho@email.com");
        expense(userId, 500, 3);
        calculateInOrder(userId, 3, 4, 5);

        extraIncomeService.create(ExtraIncome.builder()
            .description("Freela")
            .value(BigDecimal.valueOf(1000))
            .date(LocalDate.of(YEAR, 4, 10))
            .build(), userId);

        assertEquals(2500, remaining(userId, 3));
        assertEquals(6500, remaining(userId, 4));
        assertEquals(6500, summary(userId, 5).getCashBalance().intValue());
        assertEquals(9500, remaining(userId, 5));
    }

    @Test
    void newSalary_shouldReachTheMonthsFromItsEffectiveDateOn() {
        var userId = registerUser("ponta.salario@email.com");
        expense(userId, 500, 3);
        calculateInOrder(userId, 3, 4, 5);

        salaryHistoryService.create(SalaryHistory.builder()
            .baseSalary(BigDecimal.valueOf(4000))
            .effectiveFrom(LocalDate.of(YEAR, 4, 1))
            .build(), userId);

        assertEquals(3000, summary(userId, 3).getBaseSalary().intValue());
        assertEquals(2500, remaining(userId, 3));
        assertEquals(4000, summary(userId, 4).getBaseSalary().intValue());
        assertEquals(6500, remaining(userId, 4));
        assertEquals(10500, remaining(userId, 5));
    }

    @Test
    void cascade_shouldNotCreateTheMonthThatHasNoSummary_andFlagTheOneAfterTheGap() {
        var userId = registerUser("ponta.lacuna@email.com");
        expense(userId, 500, 3);
        calculateInOrder(userId, 3, 5);

        expense(userId, 200, 3);

        assertEquals(2300, remaining(userId, 3));
        assertEquals(List.of(3, 5), monthsOf(userId));
        assertTrue(summary(userId, 5).getPreviousMonthMissing());
    }

    // ---- sem recursão ----

    // O cálculo do resumo gera as despesas recorrentes do mês (createFromRecurrence): se isso publicasse o aviso, o recálculo
    // dispararia outro recálculo. Aqui o recálculo cria a despesa do modelo e o único aviso é o da despesa lançada.
    @Test
    void recalculation_shouldNotPublishAnotherEvent_evenWhenItGeneratesRecurringExpenses() {
        var userId = registerUser("ponta.recursao@email.com");
        calculateInOrder(userId, 3);
        var model = RecurringExpense.of("Spotify", BigDecimal.valueOf(50), 25, YearMonth.of(2026, 1), null);
        model.setUser(userService.getById(userId));
        model.setCategory(categoryService.getByUser(userId).getFirst());
        recurringExpenseRepository.save(model);
        publishedEvents.events.clear();

        expense(userId, 200, 3);

        assertEquals(250, summary(userId, 3).getTotalExpenses().intValue());
        assertEquals(List.of(new PeriodDataChangedEvent(userId, YearMonth.of(YEAR, 3))), publishedEvents.of(userId));
    }

    private ResultActions getSummary(UUID userId, int month) throws Exception {
        return mockMvc.perform(get("/monthly-summary/{userId}/{year}/{month}", userId, YEAR, month))
            .andExpect(status().isOk());
    }

    private void calculateInOrder(UUID userId, int... months) {
        for (var month : months) {
            monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, month));
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

    private UUID firstCategoryId(UUID userId) {
        return categoryService.getByUser(userId).stream()
            .map(Category::getId)
            .findFirst()
            .orElseThrow();
    }

    private Expense expense(UUID userId, int value, int month) {
        var expense = Expense.builder()
            .description("Despesa")
            .value(BigDecimal.valueOf(value))
            .dueDate(LocalDate.of(YEAR, month, 10))
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PENDING)
            .build();
        return expenseService.create(userId, ExpenseCommand.of(expense).withCategoryId(firstCategoryId(userId)));
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
