package br.com.finc2u.server.features.expense.service;

import br.com.finc2u.server.exception.BusinessException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
        when(expenseRepository.save(any(Expense.class)))
            .thenReturn(expense);

        Expense response = expenseService.create(userId, command(expense, cardAccountId, null));

        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
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
    void create_shouldDefaultStatusToPaid_whenNullAndDueDateNotInFuture() {
        expense.setPaymentStatus(null);
        expense.setDueDate(LocalDate.now().minusDays(1));

        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        Expense response = expenseService.create(userId, command(expense, cardAccountId, null));

        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
    }

    @Test
    void create_shouldDefaultStatusToPending_whenNullAndDueDateInFuture() {
        expense.setPaymentStatus(null);
        expense.setDueDate(LocalDate.now().plusDays(5));

        when(userService.getById(userId))
            .thenReturn(user);
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        Expense response = expenseService.create(userId, command(expense, cardAccountId, null));

        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
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
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
        List<Expense> response = expenseService.getWithFilters(userId, filter);

        assertNotNull(response);
        assertEquals(1, response.size());
    }

    private ExpenseCommand command(Expense expense, UUID cardAccountId, LocalDate purchaseDate) {
        return new ExpenseCommand(expense, cardAccountId, null, categoryId, purchaseDate);
    }

}
