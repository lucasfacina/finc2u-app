package br.com.finc2u.server.features.expense.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.mapper.ExpenseMapper;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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

    @Spy
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

        Expense response = expenseService.create(userId, command(expense, cardAccountId, null));

        assertNotNull(response);
        assertEquals(expense.getDescription(), response.getDescription());
        verify(expenseMapper).applyCardAccount(expense, cardAccountId, null);
        verify(expenseMapper).applyCategory(expense, categoryId);
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

        Expense response = expenseService.create(userId, command(expense, cardAccountId, null));

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

        Expense response = expenseService.create(
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

        Expense response = expenseService.create(userId, command(expense, cardAccountId, null));

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
        var other = Expense.builder().description("Mercado 2/2").build();
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
        var command = new ExpenseCommand(updates, cardAccountId, null, categoryId, null);

        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        Expense response = expenseService.update(expenseId, command);

        assertEquals(expenseId, response.getId());
        verify(expenseMapper).applyCardAccount(expense, cardAccountId, null);
        verify(expenseMapper).applyCategory(expense, categoryId);
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
            .when(expenseMapper).applyCategory(expense, categoryId);

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
    void getById_shouldReturnExpense_whenIdExists() {
        when(expenseRepository.findById(expenseId))
            .thenReturn(Optional.of(expense));

        Expense response = expenseService.getById(expenseId);

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

        List<Expense> response = expenseService.getByUser(userId);

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
        List<Expense> response = expenseService.getWithFilters(userId, filter);

        assertNotNull(response);
        assertEquals(1, response.size());
    }

    private ExpenseCommand command(Expense expense, UUID cardAccountId, LocalDate purchaseDate) {
        return new ExpenseCommand(expense, cardAccountId, null, categoryId, purchaseDate);
    }

}
