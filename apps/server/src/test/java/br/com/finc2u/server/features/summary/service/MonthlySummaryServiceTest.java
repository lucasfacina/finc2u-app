package br.com.finc2u.server.features.summary.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.repository.MonthlySummaryRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonthlySummaryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Mock
    private MonthlySummaryRepository monthlySummaryRepository;
    @Mock
    private ExpenseService expenseService;
    @Mock
    private ExtraIncomeService extraIncomeService;
    @Mock
    private UserService userService;
    @Mock
    private SalaryHistoryService salaryHistoryService;
    @Mock
    private RecurringExpenseMaterializer recurringExpenseMaterializer;

    @InjectMocks
    private MonthlySummaryService monthlySummaryService;

    private UUID userId;
    private User user;
    private final int MONTH = 6, YEAR = 2026;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
            .savingsBalance(BigDecimal.valueOf(1000))
            .build();
        user.setId(userId);
    }

    private Expense expenseOf(BigDecimal value, PaymentStatus status, LocalDate dueDate) {
        return Expense.builder()
            .value(value)
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(status)
            .dueDate(dueDate)
            .user(user)
            .build();
    }

    private ExtraIncome extraIncomeOf(BigDecimal value) {
        return ExtraIncome.builder()
            .value(value)
            .date(LocalDate.of(YEAR, MONTH, 10))
            .user(user)
            .build();
    }

    private void stubRepositories(
        List<Expense> expenses,
        List<ExtraIncome> extraIncomes,
        MonthlySummary existing,
        MonthlySummary prevMonth) {

        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseService.getByUserAndPeriod(eq(userId), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(expenses);
        when(extraIncomeService.getByUserAndPeriod(eq(userId), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(extraIncomes);
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, MONTH, YEAR))
            .thenReturn(Optional.ofNullable(existing));
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 5, YEAR))
            .thenReturn(Optional.ofNullable(prevMonth));
        when(monthlySummaryRepository.save(any(MonthlySummary.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(salaryHistoryService.getBaseSalaryForPeriod(eq(userId), any(LocalDate.class)))
            .thenReturn(BigDecimal.valueOf(3000));
    }

    // B1: o resumo de um mês futuro enxerga a assinatura porque as despesas recorrentes do mês são geradas antes da soma.
    @Test
    void calculateOrRecalculate_shouldMaterializeTheRecurringExpensesOfTheMonthBeforeReadingTheExpenses() {
        var subscription = expenseOf(BigDecimal.valueOf(21.90), PaymentStatus.PENDING, LocalDate.of(YEAR, MONTH, 25));
        stubRepositories(List.of(subscription), List.of(), null, null);

        var result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR);

        var inOrder = inOrder(recurringExpenseMaterializer, expenseService);
        inOrder.verify(recurringExpenseMaterializer).materializeForUser(userId, YearMonth.of(YEAR, MONTH));
        inOrder.verify(expenseService).getByUserAndPeriod(eq(userId), any(LocalDate.class), any(LocalDate.class));
        assertEquals(0, BigDecimal.valueOf(21.90).compareTo(result.getTotalExpenses()));
    }

    @Test
    void calculateOrRecalculate_shouldComputeTotalsAndSave() {
        Expense expense1 = expenseOf(
            BigDecimal.valueOf(500),
            PaymentStatus.PAID,
            TODAY.plusDays(10)
        );
        Expense expense2 = expenseOf(
            BigDecimal.valueOf(300),
            PaymentStatus.PENDING,
            TODAY.plusDays(20)
        );

        stubRepositories(
            List.of(expense1, expense2),
            List.of(),
            null,
            null
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR);

        assertEquals(0, BigDecimal.valueOf(800).compareTo(result.getTotalExpenses()));
        assertEquals(0, BigDecimal.valueOf(500).compareTo(result.getTotalPaid()));
        assertEquals(0, BigDecimal.valueOf(300).compareTo(result.getTotalPending()));
        verify(monthlySummaryRepository).save(any(MonthlySummary.class));
    }

    @Test
    void calculateOrRecalculate_shouldIncludeAvulsoExpense_whenNoCardAccount() {
        Expense avulso = expenseOf(
            BigDecimal.valueOf(200),
            PaymentStatus.PENDING,
            LocalDate.of(YEAR, MONTH, 5)
        );

        avulso.setCardAccount(null);

        stubRepositories(
            List.of(avulso),
            List.of(),
            null,
            null
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR);

        assertEquals(0, BigDecimal.valueOf(200).compareTo(result.getTotalExpenses()));
    }

    @Test
    void calculateOrRecalculate_shouldNotChangeExpenses_whenPendingIsOverdue() {
        Expense overdue = expenseOf(
            BigDecimal.valueOf(400),
            PaymentStatus.PENDING,
            TODAY.minusDays(1)
        );

        stubRepositories(
            List.of(overdue),
            List.of(),
            null,
            null
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR);

        assertEquals(PaymentStatus.PENDING, overdue.getPaymentStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalPaid()));
        assertEquals(0, BigDecimal.valueOf(400).compareTo(result.getTotalPending()));
    }

    @Test
    void calculateOrRecalculate_shouldIncludeExtras_inTotalExtrasAndRemainingAmount() {
        // totalIncome = baseSalary(3000) + extras(500) = 3500  (cashBalance não compõe renda)
        // remainingAmount = 3500 - totalExpenses(0) = 3500
        stubRepositories(
            List.of(),
            List.of(extraIncomeOf(BigDecimal.valueOf(500))),
            null,
            null
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR);

        assertEquals(0, BigDecimal.valueOf(500).compareTo(result.getTotalExtras()));
        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.getRemainingAmount()));
    }

    @Test
    void calculateOrRecalculate_shouldAutoFillCashBalance_fromPreviousMonthRemaining() {
        MonthlySummary previousSummary = MonthlySummary.builder()
            .month(5)
            .year(YEAR)
            .user(user)
            .remainingAmount(BigDecimal.valueOf(350))
            .build();

        stubRepositories(
            List.of(),
            List.of(),
            null,
            previousSummary
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR);

        assertEquals(0, BigDecimal.valueOf(350).compareTo(result.getCashBalance()));
    }

    @Test
    void calculateOrRecalculate_shouldUsePreviousMonthRemaining_asCashBalance() {
        MonthlySummary previousSummary = MonthlySummary.builder()
            .month(5)
            .year(YEAR)
            .user(user)
            .remainingAmount(BigDecimal.valueOf(350))
            .build();

        stubRepositories(
            List.of(),
            List.of(),
            null,
            previousSummary
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR);

        assertEquals(0, BigDecimal.valueOf(350).compareTo(result.getCashBalance()));
    }

    @Test
    void calculateOrRecalculate_shouldSnapshotSavingsBalance_fromUser() {
        stubRepositories(
            List.of(),
            List.of(),
            null,
            null
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR);

        assertEquals(0, BigDecimal.valueOf(1000).compareTo(result.getSavingsBalance()));
    }

    @Test
    void calculateOrUpdate_shouldRecalculateExistingSummary_whenAlreadyExists() {
        MonthlySummary existing = MonthlySummary.builder()
            .month(MONTH)
            .year(YEAR)
            .user(user)
            .cashBalance(BigDecimal.valueOf(100))
            .build();

        stubRepositories(
            List.of(),
            List.of(),
            existing,
            null
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getCashBalance()));
        verify(monthlySummaryRepository, times(1)).save(any(MonthlySummary.class));
    }

    @Test
    void getByUserAndPeriod_shouldReturnSummary_whenExists() {
        MonthlySummary summary = MonthlySummary.builder()
            .month(MONTH)
            .year(YEAR)
            .user(user)
            .build();
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, MONTH, YEAR))
            .thenReturn(Optional.ofNullable(summary));

        MonthlySummary result = monthlySummaryService.getByUserAndPeriod(userId, MONTH, YEAR);

        assertNotNull(result);
        assertEquals(MONTH, result.getMonth());
        assertEquals(YEAR, result.getYear());
    }

    @Test
    void getByUserAndPeriod_shouldThrow_whenNotFound() {
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, MONTH, YEAR))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> monthlySummaryService.getByUserAndPeriod(userId, MONTH, YEAR));
    }

    @Test
    void getByUser_shouldReturnList() {
        MonthlySummary summary = MonthlySummary.builder()
            .month(MONTH)
            .year(YEAR)
            .user(user)
            .build();
        when(monthlySummaryRepository.findByUserId(userId))
            .thenReturn(List.of(summary));

        List<MonthlySummary> result = monthlySummaryService.getByUser(userId);

        assertEquals(1, result.size());
    }

}
