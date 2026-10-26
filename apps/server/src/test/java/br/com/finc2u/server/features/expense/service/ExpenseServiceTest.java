package br.com.finc2u.server.features.expense.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.event.GeneratedExpenseDeletedEvent;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.mapper.ExpenseMapper;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.expense.vo.RecurrenceExpenseCommand;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseMapper expenseMapper;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    @SuppressWarnings("unused") // injetado no service pelo @InjectMocks
    private Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Captor
    private ArgumentCaptor<List<Expense>> installmentsCaptor;

    @InjectMocks
    private ExpenseService expenseService;

    private Expense expense;
    private CardAccount cardAccount;
    private User user;
    private UUID expenseId;
    private UUID cardAccountId;
    private UUID categoryId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        expenseId = UUID.randomUUID();
        cardAccountId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        userId = UUID.randomUUID();

        cardAccount = CardAccount.builder()
            .cardName("Cartão Nu")
            .build();
        cardAccount.setId(cardAccountId);

        user = User.builder()
            .name("Lucas")
            .build();
        user.setId(userId);

        expense = Expense.builder()
            .description("Mercado")
            .value(BigDecimal.valueOf(150.0))
            .dueDate(TODAY)
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PENDING)
            .user(user)
            .cardAccount(cardAccount)
            .build();
        expense.setId(expenseId);
    }

    @Test
    void create_shouldReturnExpense_whenValid() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.save(any(Expense.class)))
            .thenReturn(expense);

        var response = expenseService.create(userId, command(expense, cardAccountId, null));

        assertNotNull(response);
        assertEquals(expense.getDescription(), response.getDescription());
        verify(expenseMapper).applyCardAccount(expense, cardAccountId, null);
        verify(expenseMapper).applyCategory(expense, categoryId, null);
        verify(expenseRepository, times(1)).save(any(Expense.class));
    }

    @Test
    void create_shouldNotSave_whenCardOrCategoryDoesNotBelongToTheUser() {
        when(userService.getById(userId))
            .thenReturn(user);
        doThrow(new ResourceNotFoundException("Conta/Cartão não encontrado com o ID: " + cardAccountId))
            .when(expenseMapper).applyCardAccount(expense, cardAccountId, null);

        assertThrows(ResourceNotFoundException.class,
            () -> expenseService.create(userId, command(expense, cardAccountId, null)));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void create_shouldKeepPendingStatus_whenLastInstallmentOfParceled() {
        expense.setExpenseType(ExpenseType.PARCELED);
        expense.setCurrentInstallment(3);
        expense.setTotalInstallment(3);
        expense.setPaymentStatus(PaymentStatus.PENDING);

        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.saveAll(any()))
            .thenAnswer(inv -> inv.getArgument(0));

        var response = expenseService.create(userId, command(expense, cardAccountId, null));

        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
    }

    @Test
    void create_shouldSaveEveryInstallmentFromTheInformedOne_whenParceled() {
        expense.setExpenseType(ExpenseType.PARCELED);
        expense.setCurrentInstallment(4);
        expense.setTotalInstallment(10);
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.saveAll(any()))
            .thenAnswer(inv -> inv.getArgument(0));

        var response = expenseService.create(userId, command(expense, cardAccountId, null));

        verify(expenseRepository).saveAll(installmentsCaptor.capture());
        var saved = installmentsCaptor.getValue();
        assertEquals(7, saved.size());
        assertSame(expense, response);
        assertSame(expense, saved.getFirst());
        assertEquals(10, saved.getLast().getCurrentInstallment());
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void create_shouldLinkTheInstallmentsOfOnePurchaseToANewGroup() {
        expense.setExpenseType(ExpenseType.PARCELED);
        expense.setCurrentInstallment(1);
        expense.setTotalInstallment(3);
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.saveAll(any()))
            .thenAnswer(inv -> inv.getArgument(0));

        var response = expenseService.create(userId, command(expense, cardAccountId, null));

        assertNotNull(response.getInstallmentGroupId());
        verify(expenseRepository).saveAll(installmentsCaptor.capture());
        assertTrue(installmentsCaptor.getValue().stream()
            .allMatch(installment -> response.getInstallmentGroupId().equals(installment.getInstallmentGroupId())));
    }

    @Test
    void create_shouldNotGenerateInstallmentsNorGroup_whenNotParceled() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var response = expenseService.create(userId, command(expense, cardAccountId, null));

        assertNull(response.getInstallmentGroupId());
        verify(expenseRepository, never()).saveAll(any());
    }

    @Test
    void create_shouldNotSaveAnything_whenTheInformedInstallmentIsInvalid() {
        expense.setExpenseType(ExpenseType.PARCELED);
        expense.setCurrentInstallment(5);
        expense.setTotalInstallment(3);
        when(userService.getById(userId))
            .thenReturn(user);

        assertThrows(BusinessException.class,
            () -> expenseService.create(userId, command(expense, cardAccountId, null)));

        verify(expenseRepository, never()).saveAll(any());
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void create_shouldThrowBusinessException_whenParceledWithoutTotalInstallments() {
        expense.setExpenseType(ExpenseType.PARCELED);
        expense.setTotalInstallment(null);

        when(userService.getById(userId))
            .thenReturn(user);
        assertThrows(BusinessException.class,
            () -> expenseService.create(userId, command(expense, cardAccountId, null)));
    }

    @Test
    void create_shouldCalculateDueDateFromCard_whenDueDateIsNull() {
        cardAccount.setClosingDate(30);
        cardAccount.setDueDate(15);
        expense.setDueDate(null);

        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var response = expenseService.create(
            userId,
            command(expense, cardAccountId, LocalDate.of(2026, 5, 20))
        );

        assertEquals(LocalDate.of(2026, 6, 15), response.getDueDate());
    }

    @Test
    void create_shouldThrowBusinessException_whenNoCardAndNoDueDate() {
        expense.setCardAccount(null);
        expense.setDueDate(null);

        when(userService.getById(userId))
            .thenReturn(user);

        assertThrows(BusinessException.class,
            () -> expenseService.create(userId, command(expense, null, null)));
    }

    @Test
    void create_shouldThrowInvalidRequest_whenStatusIsNullAndDueDateInThePast() {
        expense.setPaymentStatus(null);
        expense.setDueDate(TODAY.minusDays(1));

        when(userService.getById(userId))
            .thenReturn(user);

        assertThrows(InvalidRequestException.class,
            () -> expenseService.create(userId, command(expense, cardAccountId, null)));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void create_shouldDefaultStatusToPending_whenNullAndDueDateIsNotInThePast() {
        expense.setPaymentStatus(null);
        expense.setDueDate(TODAY.plusDays(5));

        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var response = expenseService.create(userId, command(expense, cardAccountId, null));

        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
    }

    @Test
    void getByInstallmentGroup_shouldReturnTheInstallmentsOfThePurchase() {
        var groupId = UUID.randomUUID();
        when(expenseRepository.findByInstallmentGroupIdOrderByCurrentInstallment(groupId))
            .thenReturn(List.of(expense));

        var result = expenseService.getByInstallmentGroup(groupId);

        assertEquals(List.of(expense), result);
    }

    @Test
    void getByInstallmentGroup_shouldThrowResourceNotFound_whenTheGroupDoesNotExist() {
        var groupId = UUID.randomUUID();
        when(expenseRepository.findByInstallmentGroupIdOrderByCurrentInstallment(groupId))
            .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getByInstallmentGroup(groupId));
    }

    @Test
    void deleteInstallmentGroup_shouldDeleteEveryInstallmentOfThePurchase() {
        var groupId = UUID.randomUUID();
        var other = Expense.builder().description("Mercado 2/2").dueDate(TODAY.plusMonths(1)).user(user).build();
        when(expenseRepository.findByInstallmentGroupIdOrderByCurrentInstallment(groupId))
            .thenReturn(List.of(expense, other));

        expenseService.deleteInstallmentGroup(groupId);

        verify(expenseRepository).deleteAll(List.of(expense, other));
    }

    @Test
    void deleteInstallmentGroup_shouldThrowResourceNotFoundAndDeleteNothing_whenTheGroupDoesNotExist() {
        var groupId = UUID.randomUUID();
        when(expenseRepository.findByInstallmentGroupIdOrderByCurrentInstallment(groupId))
            .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.deleteInstallmentGroup(groupId));

        verify(expenseRepository, never()).deleteAll(any());
    }

    @Test
    void updateStatus_shouldMarkPaidWithToday_whenPaidAtIsNotInformed() {
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var response = expenseService.updateStatus(expenseId, PaymentStatus.PAID, null);

        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
        assertEquals(TODAY, response.getPaidAt());
    }

    @Test
    void updateStatus_shouldKeepInformedPaidAt() {
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var response = expenseService.updateStatus(expenseId, PaymentStatus.PAID, TODAY.minusDays(2));

        assertEquals(TODAY.minusDays(2), response.getPaidAt());
    }

    @Test
    void updateStatus_shouldClearPaidAt_whenBackToPending() {
        expense.setPaymentStatus(PaymentStatus.PAID);
        expense.setPaidAt(TODAY.minusDays(1));
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var response = expenseService.updateStatus(expenseId, PaymentStatus.PENDING, null);

        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
        assertNull(response.getPaidAt());
    }

    @Test
    void updateStatus_shouldThrowBusinessExceptionAndNotSave_whenPaidAtIsInTheFuture() {
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));

        assertThrows(BusinessException.class,
            () -> expenseService.updateStatus(expenseId, PaymentStatus.PAID, TODAY.plusDays(1)));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void update_shouldApplyCommandThroughMapperAndSave() {
        var updates = Expense.builder()
            .description("Mercado do mês")
            .build();
        var command = ExpenseCommand.of(updates).withCardAccountId(cardAccountId).withCategoryId(categoryId);

        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var response = expenseService.update(expenseId, command);

        assertEquals(expenseId, response.getId());
        verify(expenseMapper).applyCardAccount(expense, cardAccountId, null);
        verify(expenseMapper).applyCategory(expense, categoryId, null);
        verify(expenseMapper).applyEditableFields(expense, updates);
        verify(expenseRepository).save(expense);
    }

    @Test
    void create_shouldThrowBusinessException_whenPurchaseIsAfterTheCardExpiration() {
        cardAccount.setExpirationDate(LocalDate.of(2026, 5, 31));
        when(userService.getById(userId))
            .thenReturn(user);

        var exception = assertThrows(BusinessException.class,
            () -> expenseService.create(userId, command(expense, cardAccountId, LocalDate.of(2026, 6, 1))));

        assertEquals("Cartão expirado em 05/2026.", exception.getMessage());
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void create_shouldUseTodayAsPurchaseDate_whenItIsNotInformed() {
        cardAccount.setExpirationDate(TODAY.minusMonths(1).withDayOfMonth(1));
        when(userService.getById(userId))
            .thenReturn(user);

        assertThrows(BusinessException.class,
            () -> expenseService.create(userId, command(expense, cardAccountId, null)));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void create_shouldAccept_whenPurchaseIsWithinTheCardExpiration() {
        cardAccount.setExpirationDate(LocalDate.of(2026, 5, 31));
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.save(any(Expense.class)))
            .thenReturn(expense);

        assertDoesNotThrow(
            () -> expenseService.create(userId, command(expense, cardAccountId, LocalDate.of(2026, 5, 31))));
    }

    @Test
    void update_shouldNotValidateTheCardExpiration_whenPurchaseDateIsNotInformed() {
        cardAccount.setExpirationDate(TODAY.minusYears(1));
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> expenseService.update(expenseId, command(expense, cardAccountId, null)));
    }

    @Test
    void update_shouldThrowBusinessException_whenPurchaseDateIsInformedAfterTheCardExpiration() {
        cardAccount.setExpirationDate(LocalDate.of(2026, 5, 31));
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));

        assertThrows(BusinessException.class,
            () -> expenseService.update(expenseId, command(expense, cardAccountId, LocalDate.of(2026, 6, 1))));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void update_shouldNotSave_whenCardOrCategoryDoesNotBelongToTheUser() {
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));
        doThrow(new ResourceNotFoundException("Categoria não encontrada com o ID: " + categoryId))
            .when(expenseMapper).applyCategory(expense, categoryId, null);

        assertThrows(ResourceNotFoundException.class,
            () -> expenseService.update(expenseId, command(expense, cardAccountId, null)));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void update_shouldThrowResourceNotFoundException_whenIdDoesNotExist() {
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> expenseService.update(expenseId, command(expense, cardAccountId, null)));
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createFromRecurrence_shouldSaveAFixedPendingExpenseWithTheDataOfTheModel() {
        var category = Category.builder()
            .name("Assinaturas")
            .build();
        var recurringExpense = RecurringExpense.of("Spotify", BigDecimal.valueOf(21.90), 25, YearMonth.of(2026, 8), null);
        recurringExpense.setId(UUID.randomUUID());
        recurringExpense.setUser(user);
        recurringExpense.setCategory(category);
        recurringExpense.setCardAccount(cardAccount);
        var dueDate = LocalDate.of(2026, 10, 25);
        when(expenseRepository.existsByRecurringExpenseIdAndRecurrenceMonth(recurringExpense.getId(), LocalDate.of(2026, 10, 1)))
            .thenReturn(false);
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var created = expenseService.createFromRecurrence(recurrenceCommand(recurringExpense, dueDate)).orElseThrow();

        assertEquals("Spotify", created.getDescription());
        assertEquals(BigDecimal.valueOf(21.90), created.getValue());
        assertEquals(dueDate, created.getDueDate());
        assertEquals(ExpenseType.FIXED, created.getExpenseType());
        assertEquals(PaymentStatus.PENDING, created.getPaymentStatus());
        assertNull(created.getPaidAt());
        assertSame(user, created.getUser());
        assertSame(category, created.getCategory());
        assertSame(cardAccount, created.getCardAccount());
        assertEquals(recurringExpense.getId(), created.getRecurringExpenseId());
        assertEquals(LocalDate.of(2026, 10, 1), created.getRecurrenceMonth());
    }

    // Rodar duas vezes não duplica: a despesa do modelo naquele mês já existe.
    @Test
    void createFromRecurrence_shouldReturnEmptyAndNotSave_whenTheModelAlreadyHasTheExpenseInTheMonth() {
        var recurringExpense = RecurringExpense.of("Spotify", BigDecimal.valueOf(21.90), 25, YearMonth.of(2026, 8), null);
        recurringExpense.setId(UUID.randomUUID());
        var dueDate = LocalDate.of(2026, 10, 25);
        when(expenseRepository.existsByRecurringExpenseIdAndRecurrenceMonth(recurringExpense.getId(), LocalDate.of(2026, 10, 1)))
            .thenReturn(true);

        var created = expenseService.createFromRecurrence(recurrenceCommand(recurringExpense, dueDate));

        assertTrue(created.isEmpty());
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void detachFromRecurringExpense_shouldAskTheRepositoryToDetachTheExpensesOfTheModel() {
        var recurringExpenseId = UUID.randomUUID();

        expenseService.detachFromRecurringExpense(recurringExpenseId);

        verify(expenseRepository).detachFromRecurringExpense(recurringExpenseId);
    }

    @Test
    void getById_shouldReturnExpense_whenIdExists() {
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));

        var response = expenseService.getById(expenseId);

        assertNotNull(response);
        assertEquals(expenseId, response.getId());
    }

    @Test
    void getById_shouldThrowResourceNotFoundException_whenIdDoesNotExist() {
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getById(expenseId));
    }

    @Test
    void getByUser_shouldReturnListOfExpenses() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.findByUserId(userId))
            .thenReturn(List.of(expense));

        var response = expenseService.getByUser(userId);

        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void getByUser_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        when(userService.getById(userId))
            .thenThrow(new ResourceNotFoundException("Usuário não encontrado com o ID: " + userId));

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getByUser(userId));
        verify(expenseRepository, never()).findByUserId(any());
    }

    @Test
    void getByUserAndPeriod_shouldReturnTheExpensesOfThePeriod() {
        var start = LocalDate.of(2026, 6, 1);
        var end = LocalDate.of(2026, 6, 30);
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.findByUserIdAndDueDateBetween(userId, start, end))
            .thenReturn(List.of(expense));

        var response = expenseService.getByUserAndPeriod(userId, start, end);

        assertEquals(List.of(expense), response);
    }

    @Test
    void getByUserAndPeriod_shouldAcceptASingleDay_whenStartEqualsEnd() {
        var day = LocalDate.of(2026, 6, 15);
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.findByUserIdAndDueDateBetween(userId, day, day))
            .thenReturn(List.of(expense));

        assertEquals(List.of(expense), expenseService.getByUserAndPeriod(userId, day, day));
    }

    @Test
    void getByUserAndPeriod_shouldThrowInvalidRequestException_whenStartIsAfterEnd() {
        var start = LocalDate.of(2026, 6, 30);
        var end = LocalDate.of(2026, 6, 1);

        assertThrows(InvalidRequestException.class, () -> expenseService.getByUserAndPeriod(userId, start, end));
        verify(expenseRepository, never()).findByUserIdAndDueDateBetween(any(), any(), any());
    }

    @Test
    void getByUserAndPeriod_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        when(userService.getById(userId))
            .thenThrow(new ResourceNotFoundException("Usuário não encontrado com o ID: " + userId));

        assertThrows(ResourceNotFoundException.class,
            () -> expenseService.getByUserAndPeriod(userId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
        verify(expenseRepository, never()).findByUserIdAndDueDateBetween(any(), any(), any());
    }

    @Test
    void getWithFilters_shouldReturnFilteredExpenses() {
        when(userService.getById(userId))
            .thenReturn(user);
        Specification<Expense> anySpec = any();
        when(expenseRepository.findAll(anySpec))
            .thenReturn(List.of(expense));

        ExpenseFilter filter =
            new ExpenseFilter(expense.getDueDate().getMonthValue(),
                expense.getDueDate().getYear(),
                null, // status
                null, // type
                null, // cardAccountId
                null, //categoryId
                null, // minPrice
                null, // maxPrice
                null // overdue
            );
        var response = expenseService.getWithFilters(userId, filter);

        assertNotNull(response);
        assertEquals(1, response.size());
    }

    // ---- categoria por nome: o service só repassa id e nome ao mapper, que decide (e cria, se faltar) ----

    @Test
    void create_shouldPassTheCategoryNameToTheMapper() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.save(any(Expense.class)))
            .thenReturn(expense);

        expenseService.create(userId, ExpenseCommand.of(expense).withCardAccountId(cardAccountId).withCategoryName("Pets"));

        verify(expenseMapper).applyCategory(expense, null, "Pets");
    }

    @Test
    void update_shouldPassTheCategoryNameToTheMapper() {
        var updates = Expense.builder()
            .description("Mercado do mês")
            .build();
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        expenseService.update(expenseId, ExpenseCommand.of(updates).withCardAccountId(cardAccountId).withCategoryName("Pets"));

        verify(expenseMapper).applyCategory(expense, null, "Pets");
    }

    // ---- aviso ao resumo mensal (PeriodDataChangedEvent): sempre o mês mais antigo afetado ----

    @Test
    void create_shouldPublishThePeriodOfTheDueDate() {
        expense.setDueDate(LocalDate.of(2026, 8, 20));
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.save(any(Expense.class)))
            .thenReturn(expense);

        expenseService.create(userId, command(expense, cardAccountId, null));

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 8)));
    }

    // A compra parcelada avisa uma vez só, a partir da parcela informada (as seguintes caem em meses posteriores).
    @Test
    void create_shouldPublishOnceFromTheInformedInstallment_whenParceled() {
        expense.setDueDate(LocalDate.of(2026, 9, 5));
        expense.setExpenseType(ExpenseType.PARCELED);
        expense.setCurrentInstallment(4);
        expense.setTotalInstallment(10);
        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.saveAll(any()))
            .thenAnswer(inv -> inv.getArgument(0));

        expenseService.create(userId, command(expense, cardAccountId, null));

        verify(eventPublisher, times(1)).publishEvent(any(PeriodDataChangedEvent.class));
        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 9)));
    }

    @Test
    void create_shouldNotPublish_whenTheCreationFails() {
        cardAccount.setExpirationDate(LocalDate.of(2026, 5, 31));
        when(userService.getById(userId))
            .thenReturn(user);

        assertThrows(BusinessException.class,
            () -> expenseService.create(userId, command(expense, cardAccountId, LocalDate.of(2026, 6, 1))));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void update_shouldPublishTheOlderMonth_whenTheDueDateMovesToAnEarlierMonth() {
        updateMovingTheDueDate(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 7, 10));

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 7)));
    }

    @Test
    void update_shouldPublishTheOlderMonth_whenTheDueDateMovesToALaterMonth() {
        updateMovingTheDueDate(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10));

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 7)));
    }

    @Test
    void update_shouldPublishTheSameMonth_whenTheDueDateStaysInTheMonth() {
        updateMovingTheDueDate(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 25));

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 7)));
    }

    @Test
    void updateStatus_shouldPublishThePeriodOfTheDueDate() {
        expense.setDueDate(LocalDate.of(2026, 7, 10));
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        expenseService.updateStatus(expenseId, PaymentStatus.PAID, null);

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 7)));
    }

    @Test
    void updateStatus_shouldNotPublish_whenThePaymentDateIsInTheFuture() {
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));

        assertThrows(BusinessException.class,
            () -> expenseService.updateStatus(expenseId, PaymentStatus.PAID, TODAY.plusDays(1)));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void delete_shouldRemoveTheExpenseAndPublishItsPeriod() {
        expense.setDueDate(LocalDate.of(2026, 7, 10));
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));

        expenseService.delete(expenseId);

        verify(expenseRepository).delete(expense);
        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 7)));
    }

    // Apagar a despesa que um modelo recorrente gerou avisa qual vencimento do modelo foi apagado (o modelo registra o pulo).
    @Test
    void delete_shouldPublishTheDeletedGeneratedExpense_whenItCameFromARecurringModel() {
        var modelId = UUID.randomUUID();
        expense.setDueDate(LocalDate.of(2026, 7, 25));
        expense.setRecurringExpenseId(modelId);
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));

        expenseService.delete(expenseId);

        verify(eventPublisher).publishEvent(new GeneratedExpenseDeletedEvent(modelId, LocalDate.of(2026, 7, 25)));
        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 7)));
    }

    @Test
    void delete_shouldNotPublishTheDeletedGeneratedExpense_whenTheExpenseWasLaunchedByTheUser() {
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));

        expenseService.delete(expenseId);

        verify(eventPublisher, never()).publishEvent(any(GeneratedExpenseDeletedEvent.class));
    }

    @Test
    void delete_shouldNotPublish_whenTheExpenseDoesNotExist() {
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.delete(expenseId));

        verifyNoInteractions(eventPublisher);
    }

    // Mesmo que a lista venha fora de ordem, o aviso parte da parcela mais antiga.
    @Test
    void deleteInstallmentGroup_shouldPublishFromTheEarliestInstallment() {
        var groupId = UUID.randomUUID();
        expense.setDueDate(LocalDate.of(2026, 10, 5));
        var earlier = Expense.builder().description("Mercado 1/2").dueDate(LocalDate.of(2026, 8, 5)).user(user).build();
        when(expenseRepository.findByInstallmentGroupIdOrderByCurrentInstallment(groupId))
            .thenReturn(List.of(expense, earlier));

        expenseService.deleteInstallmentGroup(groupId);

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 8)));
    }

    // Quem chama é o próprio cálculo do resumo: avisar de lá seria recursão.
    @Test
    void createFromRecurrence_shouldNotPublish_toAvoidRecursionWithTheSummaryCalculation() {
        var recurringExpense = RecurringExpense.of("Spotify", BigDecimal.valueOf(21.90), 25, YearMonth.of(2026, 8), null);
        recurringExpense.setId(UUID.randomUUID());
        recurringExpense.setUser(user);
        recurringExpense.setCategory(Category.builder().name("Assinaturas").build());
        var dueDate = LocalDate.of(2026, 10, 25);
        when(expenseRepository.existsByRecurringExpenseIdAndRecurrenceMonth(recurringExpense.getId(), LocalDate.of(2026, 10, 1)))
            .thenReturn(false);
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        expenseService.createFromRecurrence(recurrenceCommand(recurringExpense, dueDate));

        verifyNoInteractions(eventPublisher);
    }

    // O mapper (mock aqui) é quem troca o vencimento no update: o doAnswer faz o papel dele.
    private void updateMovingTheDueDate(LocalDate previousDueDate, LocalDate newDueDate) {
        expense.setDueDate(previousDueDate);
        var updates = Expense.builder()
            .dueDate(newDueDate)
            .build();
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        doAnswer(invocation -> {
            expense.setDueDate(newDueDate);
            return null;
        }).when(expenseMapper).applyEditableFields(expense, updates);

        expenseService.update(expenseId, ExpenseCommand.of(updates).withCardAccountId(cardAccountId).withCategoryId(categoryId));
    }

    // Monta o VO como o materializer faz: a feature expense recebe os dados do modelo, não o modelo.
    private RecurrenceExpenseCommand recurrenceCommand(RecurringExpense model, LocalDate dueDate) {
        return new RecurrenceExpenseCommand(model.getId(), YearMonth.from(dueDate), model.getDescription(), model.getValue(), dueDate,
            model.getUser(), model.getCategory(), model.getCardAccount());
    }

    private ExpenseCommand command(Expense expense, UUID cardAccountId, LocalDate purchaseDate) {
        return ExpenseCommand.of(expense).withCardAccountId(cardAccountId).withCategoryId(categoryId).withPurchaseDate(purchaseDate);
    }

}
