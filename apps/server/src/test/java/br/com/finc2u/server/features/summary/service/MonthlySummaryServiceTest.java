package br.com.finc2u.server.features.summary.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.extra.entity.Extra;
import br.com.finc2u.server.features.extra.repository.ExtraRepository;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryId;
import br.com.finc2u.server.features.summary.repository.MonthlySummaryRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.entity.UserConfiguration;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonthlySummaryServiceTest {

    @Mock
    private MonthlySummaryRepository monthlySummaryRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private ExtraRepository extraRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private MonthlySummaryService monthlySummaryService;

    private UUID userId;
    private User user;
    private final int MONTH = 6, YEAR = 2026;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .userConfiguration(UserConfiguration.builder()
                        .baseSalary(BigDecimal.valueOf(3000))
                        .savingsBalance(BigDecimal.valueOf(1000))
                        .build())
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

    private Extra extraOf(BigDecimal value) {
        return Extra.builder()
                .value(value)
                .date(LocalDate.of(YEAR, MONTH, 10))
                .user(user)
                .build();
    }

    private void stubRepositories(
            List<Expense> expenses,
            List<Extra> extras,
            MonthlySummary existing,
            MonthlySummary prevMonth) {

        when(userService.getById(userId)).thenReturn(user);
        when(expenseRepository.findByUserIdAndDueDateBetween(eq(userId), any(LocalDate.class), any(LocalDate.class))).thenReturn(expenses);
        when(extraRepository.findByUserIdAndDateBetween(eq(userId), any(LocalDate.class), any(LocalDate.class))).thenReturn(extras);
        when(monthlySummaryRepository.findByUserIdAndIdMonthAndIdYear(userId, MONTH, YEAR)).thenReturn(Optional.ofNullable(existing));
        when(monthlySummaryRepository.findByUserIdAndIdMonthAndIdYear(userId, 5, YEAR)).thenReturn(Optional.ofNullable(prevMonth));
        when(monthlySummaryRepository.save(any(MonthlySummary.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void calculateOrRecalculate_shouldComputeTotalsAndSave() {
        Expense expense1 = expenseOf(
                BigDecimal.valueOf(500),
                PaymentStatus.PAID,
                LocalDate.now().plusDays(10)
        );
        Expense expense2 = expenseOf(
                BigDecimal.valueOf(300),
                PaymentStatus.PENDING,
                LocalDate.now().plusDays(20)
        );

        stubRepositories(
                List.of(expense1, expense2),
                List.of(),
                null,
                null
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR, null);

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

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR, null);

        assertEquals(0, BigDecimal.valueOf(200).compareTo(result.getTotalExpenses()));
    }

    @Test
    void calculateOrRecalculate_shouldMarkOverduePendingAsPaid_beforeAggregating() {
        Expense overdue = expenseOf(
                BigDecimal.valueOf(400),
                PaymentStatus.PENDING,
                LocalDate.now().minusDays(1)
        );

        stubRepositories(
                List.of(overdue),
                List.of(),
                null,
                null
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR, null);

        assertEquals(0, BigDecimal.valueOf(400).compareTo(result.getTotalPaid()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalPending()));
    }

    @Test
    void calculateOrRecalculate_shouldIncludeExtras_inTotalExtrasAndRemainingAmount() {
        // totalIncome = baseSalary(3000) + extras(500) = 3500  (cashBalance não compõe renda)
        // remainingAmount = 3500 - totalExpenses(0) = 3500
        stubRepositories(
                List.of(),
                List.of(extraOf(BigDecimal.valueOf(500))),
                null,
                null
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR, null);

        assertEquals(0, BigDecimal.valueOf(500).compareTo(result.getTotalExtras()));
        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.getRemainingAmount()));
    }

    @Test
    void calculateOrRecalculate_shouldAutoFillCashBalance_fromPreviousMonthRemaining() {
        MonthlySummary previousSummary = MonthlySummary.builder()
                .id(new MonthlySummaryId(userId, 5, YEAR))
                .user(user)
                .remainingAmount(BigDecimal.valueOf(350))
                .build();

        stubRepositories(
                List.of(),
                List.of(),
                null,
                previousSummary
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR, null);

        assertEquals(0, BigDecimal.valueOf(350).compareTo(result.getCashBalance()));
    }

    @Test
    void calculateOrRecalculate_shouldUseRequestedCashBalance_overPreviousMonth() {
        MonthlySummary previousSummary = MonthlySummary.builder()
                .id(new MonthlySummaryId(userId, 5, YEAR))
                .user(user)
                .remainingAmount(BigDecimal.valueOf(350))
                .build();

        stubRepositories(
                List.of(),
                List.of(),
                null,
                previousSummary
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR, BigDecimal.valueOf(999));

        assertEquals(0, BigDecimal.valueOf(999).compareTo(result.getCashBalance()));
    }

    @Test
    void calculateOrRecalculate_shouldSnapshotSavingsBalance_fromUserConfiguration() {
        stubRepositories(
                List.of(),
                List.of(),
                null,
                null
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR, null);

        assertEquals(0, BigDecimal.valueOf(1000).compareTo(result.getSavingsBalance()));
    }

    @Test
    void calculateOrUpdate_shouldRecalculateExistingSummary_whenAlreadyExists() {
        MonthlySummary existing = MonthlySummary.builder()
                .id(new MonthlySummaryId(userId, MONTH, YEAR))
                .user(user)
                .cashBalance(BigDecimal.valueOf(100))
                .build();

        stubRepositories(
                List.of(),
                List.of(),
                existing,
                null
        );

        MonthlySummary result = monthlySummaryService.calculateOrRecalculate(userId, MONTH, YEAR, null);

        assertEquals(0, BigDecimal.valueOf(100).compareTo(result.getCashBalance()));
        verify(monthlySummaryRepository, times(1)).save(any(MonthlySummary.class));
    }

    @Test
    void getByUserAndPeriod_shouldReturnSummary_whenExists() {
        MonthlySummary summary = MonthlySummary.builder()
                .id(new MonthlySummaryId(userId, MONTH, YEAR))
                .user(user)
                .build();
        when(monthlySummaryRepository.findByUserIdAndIdMonthAndIdYear(userId, MONTH, YEAR)).thenReturn(Optional.ofNullable(summary));

        MonthlySummary result = monthlySummaryService.getByUserAndPeriod(userId, MONTH, YEAR);

        assertNotNull(result);
        assertEquals(MONTH, result.getMonth());
        assertEquals(YEAR, result.getYear());
    }

    @Test
    void getByUserAndPeriod_shouldThrow_whenNotFound() {
        when(monthlySummaryRepository.findByUserIdAndIdMonthAndIdYear(userId, MONTH, YEAR)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> monthlySummaryService.getByUserAndPeriod(userId, MONTH, YEAR));
    }

    @Test
    void getByUser_shouldReturnList() {
        MonthlySummary summary = MonthlySummary.builder()
                .id(new MonthlySummaryId(userId, MONTH, YEAR))
                .user(user)
                .build();
        when(monthlySummaryRepository.findByUserId(userId)).thenReturn(List.of(summary));

        List<MonthlySummary> result = monthlySummaryService.getByUser(userId);

        assertEquals(1, result.size());
    }

}