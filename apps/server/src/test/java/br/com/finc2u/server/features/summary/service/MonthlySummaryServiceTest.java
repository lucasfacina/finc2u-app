package br.com.finc2u.server.features.summary.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;
import br.com.finc2u.server.features.summary.repository.MonthlySummaryCategoryTotalRepository;
import br.com.finc2u.server.features.summary.repository.MonthlySummaryRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
    private MonthlySummaryCategoryTotalRepository categoryTotalRepository;
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

    @Spy
    @SuppressWarnings("unused") // injetado no service pelo @InjectMocks
    private Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Captor
    private ArgumentCaptor<List<MonthlySummaryCategoryTotal>> categoryTotalsCaptor;

    @InjectMocks
    private MonthlySummaryService monthlySummaryService;

    private UUID userId;
    private User user;
    private Category groceries;
    private Category leisure;
    private final int MONTH = 6, YEAR = 2026;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
            .savingsBalance(BigDecimal.valueOf(1000))
            .build();
        user.setId(userId);
        groceries = categoryOf("Alimentação");
        leisure = categoryOf("Lazer");
    }

    private Category categoryOf(String name) {
        var category = Category.builder()
            .name(name)
            .build();
        category.setId(UUID.randomUUID());
        return category;
    }

    private Expense expenseOf(BigDecimal value, PaymentStatus status, LocalDate dueDate) {
        return Expense.builder()
            .value(value)
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(status)
            .dueDate(dueDate)
            .user(user)
            .category(groceries)
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
    void recalculateFrom_shouldMaterializeTheRecurringExpensesOfTheMonthBeforeReadingTheExpenses() {
        var subscription = expenseOf(BigDecimal.valueOf(21.90), PaymentStatus.PENDING, LocalDate.of(YEAR, MONTH, 25));
        stubRepositories(List.of(subscription), List.of(), null, null);

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        var inOrder = inOrder(recurringExpenseMaterializer, expenseService);
        inOrder.verify(recurringExpenseMaterializer).materializeForUser(userId, YearMonth.of(YEAR, MONTH));
        inOrder.verify(expenseService).getByUserAndPeriod(eq(userId), any(LocalDate.class), any(LocalDate.class));
        assertEquals(0, BigDecimal.valueOf(21.90).compareTo(result.getTotalExpenses()));
    }

    @Test
    void recalculateFrom_shouldComputeTotalsAndSave() {
        var expense1 = expenseOf(
            BigDecimal.valueOf(500),
            PaymentStatus.PAID,
            TODAY.plusDays(10)
        );
        var expense2 = expenseOf(
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

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(0, BigDecimal.valueOf(800).compareTo(result.getTotalExpenses()));
        assertEquals(0, BigDecimal.valueOf(500).compareTo(result.getTotalPaid()));
        assertEquals(0, BigDecimal.valueOf(300).compareTo(result.getTotalPending()));
        verify(monthlySummaryRepository).save(any(MonthlySummary.class));
    }

    // O atraso é derivado da data do Clock (hoje = TODAY): pendente com vencimento antes dela entra no totalOverdue.
    @Test
    void recalculateFrom_shouldDeriveTheOverdueTotalFromTheClock() {
        var overdue = expenseOf(BigDecimal.valueOf(120), PaymentStatus.PENDING, TODAY.minusDays(5));
        var notYetDue = expenseOf(BigDecimal.valueOf(80), PaymentStatus.PENDING, TODAY.plusDays(5));
        var paidLate = expenseOf(BigDecimal.valueOf(300), PaymentStatus.PAID, TODAY.minusDays(5));
        stubRepositories(List.of(overdue, notYetDue, paidLate), List.of(), null, null);

        var summary = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(0, BigDecimal.valueOf(120).compareTo(summary.getTotalOverdue()));
    }

    // O resumo guarda o "hoje" do Clock com que foi calculado.
    @Test
    void recalculateFrom_shouldStoreTheClockDateAsTheReferenceDate() {
        stubRepositories(List.of(), List.of(), null, null);

        var summary = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(TODAY, summary.getReferenceDate());
    }

    @Test
    void recalculateFrom_shouldSumTheFixedExpenses() {
        var fixed = expenseOf(BigDecimal.valueOf(20.90), PaymentStatus.PAID, TODAY.plusDays(2));
        fixed.setExpenseType(ExpenseType.FIXED);
        var variable = expenseOf(BigDecimal.valueOf(350), PaymentStatus.PAID, TODAY.plusDays(2));
        stubRepositories(List.of(fixed, variable), List.of(), null, null);

        var summary = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(0, BigDecimal.valueOf(20.90).compareTo(summary.getTotalFixed()));
    }

    @Test
    void recalculateFrom_shouldIncludeAvulsoExpense_whenNoCardAccount() {
        var avulso = expenseOf(
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

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(0, BigDecimal.valueOf(200).compareTo(result.getTotalExpenses()));
    }

    @Test
    void recalculateFrom_shouldNotChangeExpenses_whenPendingIsOverdue() {
        var overdue = expenseOf(
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

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(PaymentStatus.PENDING, overdue.getPaymentStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalPaid()));
        assertEquals(0, BigDecimal.valueOf(400).compareTo(result.getTotalPending()));
    }

    @Test
    void recalculateFrom_shouldIncludeExtras_inTotalExtrasAndRemainingAmount() {
        // totalIncome = baseSalary(3000) + extras(500) = 3500  (cashBalance não compõe renda)
        // remainingAmount = 3500 - totalExpenses(0) = 3500
        stubRepositories(
            List.of(),
            List.of(extraIncomeOf(BigDecimal.valueOf(500))),
            null,
            null
        );

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(0, BigDecimal.valueOf(500).compareTo(result.getTotalExtras()));
        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.getRemainingAmount()));
    }

    @Test
    void recalculateFrom_shouldAutoFillCashBalance_fromPreviousMonthRemaining() {
        var previousSummary = MonthlySummary.builder()
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

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(0, BigDecimal.valueOf(350).compareTo(result.getCashBalance()));
    }

    @Test
    void recalculateFrom_shouldFlagThePreviousMonthAsMissing_whenThereIsNoPreviousSummary() {
        stubRepositories(
            List.of(),
            List.of(),
            null,
            null
        );

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertTrue(result.getPreviousMonthMissing());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getCashBalance()));
    }

    @Test
    void recalculateFrom_shouldNotFlagThePreviousMonthAsMissing_whenThePreviousSummaryExists() {
        var previousSummary = MonthlySummary.builder()
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

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertFalse(result.getPreviousMonthMissing());
    }

    @Test
    void recalculateFrom_shouldUsePreviousMonthRemaining_asCashBalance() {
        var previousSummary = MonthlySummary.builder()
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

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(0, BigDecimal.valueOf(350).compareTo(result.getCashBalance()));
    }

    @Test
    void recalculateFrom_shouldSnapshotSavingsBalance_fromUser() {
        stubRepositories(
            List.of(),
            List.of(),
            null,
            null
        );

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(0, BigDecimal.valueOf(1000).compareTo(result.getSavingsBalance()));
    }

    // O usuário sem poupança informada (nula) conta como zero no snapshot: é o User quem garante, o calculador recebe o número.
    @Test
    void recalculateFrom_shouldSnapshotZeroSavings_whenTheUserHasNoSavingsBalance() {
        user.setSavingsBalance(null);
        stubRepositories(List.of(), List.of(), null, null);

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getSavingsBalance()));
    }

    @Test
    void calculateOrUpdate_shouldRecalculateExistingSummary_whenAlreadyExists() {
        var existing = MonthlySummary.builder()
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

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH)).summary();

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getCashBalance()));
        verify(monthlySummaryRepository, times(1)).save(any(MonthlySummary.class));
    }

    // O service decide se o cálculo criou o resumo ou recalculou um existente (o controller só traduz em 201 ou 200).
    @Test
    void recalculateFrom_shouldReportCreated_whenThePeriodHadNoSummary() {
        stubRepositories(List.of(), List.of(), null, null);

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH));

        assertTrue(result.created());
    }

    @Test
    void recalculateFrom_shouldReportNotCreated_whenThePeriodAlreadyHadASummary() {
        var existing = MonthlySummary.builder()
            .month(MONTH)
            .year(YEAR)
            .user(user)
            .build();
        stubRepositories(List.of(), List.of(), existing, null);

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH));

        assertFalse(result.created());
        assertSame(existing, result.summary());
    }

    @Test
    void getByUserAndPeriod_shouldReturnSummaryWithItsCategoryTotals_whenExists() {
        var summaryId = UUID.randomUUID();
        var summary = MonthlySummary.builder()
            .month(MONTH)
            .year(YEAR)
            .user(user)
            .build();
        summary.setId(summaryId);
        var groceriesTotal = MonthlySummaryCategoryTotal.of(summary, groceries, BigDecimal.valueOf(300));
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, MONTH, YEAR))
            .thenReturn(Optional.of(summary));
        when(categoryTotalRepository.findBySummaryIdIn(List.of(summaryId)))
            .thenReturn(List.of(groceriesTotal));

        var result = monthlySummaryService.getByUserAndPeriod(userId, MONTH, YEAR);

        assertSame(summary, result.summary());
        assertEquals(List.of(groceriesTotal), result.categoryTotals());
    }

    // A ordem não vem do banco: o service ordena os totais pelo nome da categoria, em português.
    @Test
    void getByUserAndPeriod_shouldSortTheCategoryTotalsInPortugueseOrder_whateverTheOrderTheRepositoryReturns() {
        var summary = summaryOf(MONTH);
        var totals = Stream.of("Zebra", "ônibus", "Alimentação", "Água", "Ótica", "alimentação")
            .map(name -> MonthlySummaryCategoryTotal.of(summary, categoryOf(name), BigDecimal.TEN))
            .toList();
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, MONTH, YEAR))
            .thenReturn(Optional.of(summary));
        when(categoryTotalRepository.findBySummaryIdIn(List.of(summary.getId())))
            .thenReturn(totals);

        var result = monthlySummaryService.getByUserAndPeriod(userId, MONTH, YEAR);

        assertEquals(List.of("Água", "alimentação", "Alimentação", "ônibus", "Ótica", "Zebra"),
            result.categoryTotals().stream().map(total -> total.getCategory().getName()).toList());
    }

    @Test
    void getByUser_shouldThrowResourceNotFound_andReadNothing_whenTheUserDoesNotExist() {
        when(userService.getById(userId)).thenThrow(new ResourceNotFoundException("Usuário não encontrado com o ID: " + userId));

        assertThrows(ResourceNotFoundException.class, () -> monthlySummaryService.getByUser(userId));

        verifyNoInteractions(monthlySummaryRepository, categoryTotalRepository);
    }

    @Test
    void getByUser_shouldSortTheCategoryTotalsOfEachSummaryInPortugueseOrder() {
        var may = summaryOf(5);
        var june = summaryOf(6);
        when(monthlySummaryRepository.findByUserId(userId))
            .thenReturn(List.of(may, june));
        when(categoryTotalRepository.findBySummaryIdIn(List.of(may.getId(), june.getId())))
            .thenReturn(List.of(
                MonthlySummaryCategoryTotal.of(may, categoryOf("Zebra"), BigDecimal.TEN),
                MonthlySummaryCategoryTotal.of(june, categoryOf("ônibus"), BigDecimal.TEN),
                MonthlySummaryCategoryTotal.of(may, categoryOf("Água"), BigDecimal.TEN),
                MonthlySummaryCategoryTotal.of(june, categoryOf("Ótica"), BigDecimal.TEN)));

        var result = monthlySummaryService.getByUser(userId);

        assertEquals(List.of("Água", "Zebra"),
            result.get(0).categoryTotals().stream().map(total -> total.getCategory().getName()).toList());
        assertEquals(List.of("ônibus", "Ótica"),
            result.get(1).categoryTotals().stream().map(total -> total.getCategory().getName()).toList());
    }

    @Test
    void getByUserAndPeriod_shouldReturnAnEmptyCategoryList_whenTheSummaryHasNoExpenses() {
        var summary = MonthlySummary.builder()
            .month(MONTH)
            .year(YEAR)
            .user(user)
            .build();
        summary.setId(UUID.randomUUID());
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, MONTH, YEAR))
            .thenReturn(Optional.of(summary));

        var result = monthlySummaryService.getByUserAndPeriod(userId, MONTH, YEAR);

        assertTrue(result.categoryTotals().isEmpty());
    }

    @Test
    void getByUserAndPeriod_shouldThrow_whenNotFound() {
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, MONTH, YEAR))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> monthlySummaryService.getByUserAndPeriod(userId, MONTH, YEAR));
        verifyNoInteractions(categoryTotalRepository);
    }

    // O histórico não faz uma consulta por resumo: os totais de todos vêm numa chamada só e são distribuídos por resumo.
    @Test
    void getByUser_shouldReadTheCategoryTotalsOfAllTheSummariesInASingleCall() {
        var may = summaryOf(5);
        var june = summaryOf(6);
        var mayTotal = MonthlySummaryCategoryTotal.of(may, groceries, BigDecimal.valueOf(100));
        var juneGroceries = MonthlySummaryCategoryTotal.of(june, groceries, BigDecimal.valueOf(200));
        var juneLeisure = MonthlySummaryCategoryTotal.of(june, leisure, BigDecimal.valueOf(50));
        when(monthlySummaryRepository.findByUserId(userId))
            .thenReturn(List.of(may, june));
        when(categoryTotalRepository.findBySummaryIdIn(List.of(may.getId(), june.getId())))
            .thenReturn(List.of(mayTotal, juneGroceries, juneLeisure));

        var result = monthlySummaryService.getByUser(userId);

        assertEquals(2, result.size());
        assertEquals(List.of(mayTotal), result.get(0).categoryTotals());
        assertEquals(List.of(juneGroceries, juneLeisure), result.get(1).categoryTotals());
        verify(categoryTotalRepository, times(1)).findBySummaryIdIn(anyCollection());
    }

    @Test
    void getByUser_shouldNotQueryTheCategoryTotals_whenThereAreNoSummaries() {
        when(monthlySummaryRepository.findByUserId(userId))
            .thenReturn(List.of());

        var result = monthlySummaryService.getByUser(userId);

        assertTrue(result.isEmpty());
        verifyNoInteractions(categoryTotalRepository);
    }

    // ---- totais por categoria ----

    @Test
    void recalculateFrom_shouldSaveTheTotalPerCategory_sortedByCategoryName() {
        var groceryShopping = expenseOf(BigDecimal.valueOf(300), PaymentStatus.PAID, TODAY.plusDays(2));
        var groceryDelivery = expenseOf(BigDecimal.valueOf(50), PaymentStatus.PENDING, TODAY.plusDays(3));
        var cinema = expenseOf(BigDecimal.valueOf(70), PaymentStatus.PENDING, TODAY.plusDays(4));
        cinema.setCategory(leisure);
        stubRepositories(List.of(cinema, groceryShopping, groceryDelivery), List.of(), null, null);

        monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH));

        verify(categoryTotalRepository).saveAll(categoryTotalsCaptor.capture());
        var saved = categoryTotalsCaptor.getValue();
        assertEquals(List.of("Alimentação", "Lazer"), saved.stream().map(total -> total.getCategory().getName()).toList());
        assertEquals(0, BigDecimal.valueOf(350).compareTo(saved.get(0).getTotal()));
        assertEquals(0, BigDecimal.valueOf(70).compareTo(saved.get(1).getTotal()));
    }

    @Test
    void recalculateFrom_shouldNotDeleteAnyCategoryTotal_whenTheSummaryIsNew() {
        stubRepositories(List.of(expenseOf(BigDecimal.TEN, PaymentStatus.PENDING, TODAY.plusDays(1))), List.of(), null, null);

        monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH));

        verify(categoryTotalRepository, never()).deleteBySummaryId(any());
    }

    // No recálculo os totais antigos saem antes dos novos entrarem (senão o mesmo mês teria duas linhas por categoria).
    @Test
    void recalculateFrom_shouldReplaceTheOldCategoryTotals_whenTheSummaryAlreadyExists() {
        var existing = summaryOf(MONTH);
        stubRepositories(List.of(expenseOf(BigDecimal.TEN, PaymentStatus.PENDING, TODAY.plusDays(1))), List.of(), existing, null);

        monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH));

        var inOrder = inOrder(categoryTotalRepository);
        inOrder.verify(categoryTotalRepository).deleteBySummaryId(existing.getId());
        inOrder.verify(categoryTotalRepository).saveAll(anyList());
    }

    @Test
    void recalculateFrom_shouldSaveNoCategoryTotal_whenTheMonthHasNoExpense() {
        stubRepositories(List.of(), List.of(), null, null);

        monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH));

        verify(categoryTotalRepository).saveAll(categoryTotalsCaptor.capture());
        assertTrue(categoryTotalsCaptor.getValue().isEmpty());
    }

    // ---- exclusão ----

    // Os totais por categoria saem antes do resumo (FK).
    @Test
    void delete_shouldRemoveTheCategoryTotalsBeforeTheSummary() {
        var summary = summaryOf(MONTH);
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, MONTH, YEAR))
            .thenReturn(Optional.of(summary));

        monthlySummaryService.delete(userId, MONTH, YEAR);

        var inOrder = inOrder(categoryTotalRepository, monthlySummaryRepository);
        inOrder.verify(categoryTotalRepository).deleteBySummaryId(summary.getId());
        inOrder.verify(monthlySummaryRepository).delete(summary);
    }

    @Test
    void delete_shouldThrow_whenTheSummaryDoesNotExist() {
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, MONTH, YEAR))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> monthlySummaryService.delete(userId, MONTH, YEAR));
        verify(monthlySummaryRepository, never()).delete(any(MonthlySummary.class));
    }

    // ---- recalculateFrom: o mês pedido e, em ordem, os resumos existentes seguintes ----

    private void stubServices() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseService.getByUserAndPeriod(eq(userId), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());
        when(extraIncomeService.getByUserAndPeriod(eq(userId), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());
        when(monthlySummaryRepository.save(any(MonthlySummary.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(salaryHistoryService.getBaseSalaryForPeriod(eq(userId), any(LocalDate.class)))
            .thenReturn(BigDecimal.valueOf(3000));
    }

    @Test
    void recalculateFrom_shouldCalculateTheRequestedMonth_andReturnItsResult() {
        stubServices();

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, MONTH));

        assertTrue(result.created());
        assertEquals(MONTH, result.summary().getMonth());
        verify(monthlySummaryRepository).findFromPeriod(userId, YEAR, MONTH + 1);
    }

    // A cascata começa no mês seguinte também na virada do ano.
    @Test
    void recalculateFrom_shouldLookForTheLaterSummariesStartingInJanuary_whenTheMonthIsDecember() {
        stubServices();

        monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, 12));

        verify(monthlySummaryRepository).findFromPeriod(userId, YEAR + 1, 1);
    }

    // Cada mês lê o anterior já atualizado: o remainingAmount de um vira o cashBalance do seguinte.
    @Test
    void recalculateFrom_shouldPassTheRemainingAmountOnToEachExistingLaterMonth_inOrder() {
        stubServices();
        var june = summaryOf(6);
        var july = summaryOf(7);
        var august = summaryOf(8);
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 5, YEAR)).thenReturn(Optional.empty());
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 6, YEAR)).thenReturn(Optional.of(june));
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 7, YEAR)).thenReturn(Optional.of(july));
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 8, YEAR)).thenReturn(Optional.of(august));
        when(monthlySummaryRepository.findFromPeriod(userId, YEAR, 7)).thenReturn(List.of(july, august));
        when(expenseService.getByUserAndPeriod(eq(userId), eq(LocalDate.of(YEAR, 6, 1)), any(LocalDate.class)))
            .thenReturn(List.of(expenseOf(BigDecimal.valueOf(500), PaymentStatus.PENDING, TODAY.plusDays(10))));

        var result = monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, 6));

        assertFalse(result.created());
        assertEquals(0, BigDecimal.valueOf(2500).compareTo(june.getRemainingAmount()));
        assertEquals(0, BigDecimal.valueOf(2500).compareTo(july.getCashBalance()));
        assertEquals(0, BigDecimal.valueOf(5500).compareTo(july.getRemainingAmount()));
        assertEquals(0, BigDecimal.valueOf(5500).compareTo(august.getCashBalance()));
        assertEquals(0, BigDecimal.valueOf(8500).compareTo(august.getRemainingAmount()));
    }

    // Lacuna entre os meses: o resumo que não existe não é criado, e o seguinte a ela fica sem o carryover, avisando.
    @Test
    void recalculateFrom_shouldNotCreateAMissingMonth_andFlagTheOneAfterTheGap() {
        stubServices();
        var august = MonthlySummary.builder()
            .month(8)
            .year(YEAR)
            .user(user)
            .cashBalance(BigDecimal.valueOf(999))
            .build();
        august.setId(UUID.randomUUID());
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 5, YEAR)).thenReturn(Optional.empty());
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 6, YEAR)).thenReturn(Optional.empty());
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 7, YEAR)).thenReturn(Optional.empty());
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 8, YEAR)).thenReturn(Optional.of(august));
        when(monthlySummaryRepository.findFromPeriod(userId, YEAR, 7)).thenReturn(List.of(august));
        var savedMonths = ArgumentCaptor.forClass(MonthlySummary.class);

        monthlySummaryService.recalculateFrom(userId, YearMonth.of(YEAR, 6));

        verify(monthlySummaryRepository, times(2)).save(savedMonths.capture());
        assertEquals(List.of(6, 8), savedMonths.getAllValues().stream().map(MonthlySummary::getMonth).toList());
        assertTrue(august.getPreviousMonthMissing());
        assertEquals(0, BigDecimal.ZERO.compareTo(august.getCashBalance()));
    }

    // Apagar o resumo de um mês tira o carryover do seguinte (que existe e é recalculado).
    @Test
    void delete_shouldRecalculateTheExistingLaterSummaries_whichLoseTheCarryOver() {
        stubServices();
        var june = summaryOf(6);
        var july = summaryOf(7);
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 6, YEAR))
            .thenReturn(Optional.of(june))
            .thenReturn(Optional.empty());
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 7, YEAR)).thenReturn(Optional.of(july));
        when(monthlySummaryRepository.findFromPeriod(userId, YEAR, 7)).thenReturn(List.of(july));

        monthlySummaryService.delete(userId, 6, YEAR);

        var inOrder = inOrder(monthlySummaryRepository);
        inOrder.verify(monthlySummaryRepository).delete(june);
        inOrder.verify(monthlySummaryRepository).findFromPeriod(userId, YEAR, 7);
        assertTrue(july.getPreviousMonthMissing());
        assertEquals(0, BigDecimal.ZERO.compareTo(july.getCashBalance()));
    }

    @Test
    void delete_shouldRecalculateNothing_whenThereIsNoLaterSummary() {
        var june = summaryOf(6);
        when(monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, 6, YEAR)).thenReturn(Optional.of(june));

        monthlySummaryService.delete(userId, 6, YEAR);

        verify(monthlySummaryRepository).findFromPeriod(userId, YEAR, 7);
        verify(monthlySummaryRepository, never()).save(any(MonthlySummary.class));
    }

    private MonthlySummary summaryOf(int month) {
        var summary = MonthlySummary.builder()
            .month(month)
            .year(YEAR)
            .user(user)
            .build();
        summary.setId(UUID.randomUUID());
        return summary;
    }

}
