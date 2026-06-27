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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseMapper expenseMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private ExpenseService expenseService;

    private Expense expense;
    private CardAccount cardAccount;
    private User user;
    private UUID expenseId;
    private UUID cardAccountId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        expenseId = UUID.randomUUID();
        cardAccountId = UUID.randomUUID();
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
                .dueDate(LocalDate.now())
                .expenseType(ExpenseType.VARIABLE)
                .paymentStatus(PaymentStatus.PENDING)
                .user(user)
                .cardAccount(cardAccount)
                .build();
        expense.setId(expenseId);
    }

    @Test
    void create_shouldReturnExpense_whenValid() {
        when(userService.getById(userId)).thenReturn(user);
        when(expenseRepository.save(any(Expense.class))).thenReturn(expense);

        Expense response = expenseService.create(expense, userId, cardAccountId, null, null);

        assertNotNull(response);
        assertEquals(expense.getDescription(), response.getDescription());
        verify(expenseRepository, times(1)).save(any(Expense.class));
    }

    @Test
    void create_shouldSetPaidStatus_whenLastInstallmentOfParceled() {
        expense.setExpenseType(ExpenseType.PARCELED);
        expense.setCurrentInstallment(3);
        expense.setTotalInstallment(3);
        expense.setPaymentStatus(PaymentStatus.PENDING);

        when(userService.getById(userId)).thenReturn(user);
        when(expenseRepository.save(any(Expense.class))).thenReturn(expense);

        Expense response = expenseService.create(expense, userId, cardAccountId, null, null);

        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
    }

    @Test
    void create_shouldThrowBusinessException_whenParceledWithoutTotalInstallments() {
        expense.setExpenseType(ExpenseType.PARCELED);
        expense.setTotalInstallment(null);

        when(userService.getById(userId)).thenReturn(user);
        assertThrows(BusinessException.class, () -> expenseService.create(expense, userId, cardAccountId, null, null));
    }

    @Test
    void create_shouldCalculateDueDateFromCard_whenDueDateIsNull() {
        cardAccount.setClosingDate(30);
        cardAccount.setDueDate(15);
        expense.setDueDate(null);

        when(userService.getById(userId)).thenReturn(user);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        Expense response = expenseService.create(expense, userId, cardAccountId, LocalDate.of(2026, 5, 20), null);

        assertEquals(LocalDate.of(2026, 6, 15), response.getDueDate());
    }

    @Test
    void create_shouldThrowBusinessException_whenNoCardAndNoDueDate() {
        expense.setCardAccount(null);
        expense.setDueDate(null);

        when(userService.getById(userId)).thenReturn(user);

        assertThrows(BusinessException.class,
                () -> expenseService.create(expense, userId, null, null, null));
    }

    @Test
    void create_shouldDefaultStatusToPaid_whenNullAndDueDateNotInFuture() {
        expense.setPaymentStatus(null);
        expense.setDueDate(LocalDate.now().minusDays(1));

        when(userService.getById(userId)).thenReturn(user);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        Expense response = expenseService.create(expense, userId, cardAccountId, null, null);

        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
    }

    @Test
    void create_shouldDefaultStatusToPending_whenNullAndDueDateInFuture() {
        expense.setPaymentStatus(null);
        expense.setDueDate(LocalDate.now().plusDays(5));

        when(userService.getById(userId)).thenReturn(user);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        Expense response = expenseService.create(expense, userId, cardAccountId, null, null);

        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
    }

    @Test
    void getById_shouldReturnExpense_whenIdExists() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));

        Expense response = expenseService.getById(expenseId);

        assertNotNull(response);
        assertEquals(expenseId, response.getId());
    }

    @Test
    void getById_shouldThrowResourceNotFoundException_whenIdDoesNotExist() {
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getById(expenseId));
    }

    @Test
    void getByUser_shouldReturnListOfExpenses() {
        when(userService.getById(userId)).thenReturn(user);
        when(expenseRepository.findByUserId(userId)).thenReturn(List.of(expense));

        List<Expense> response = expenseService.getByUser(userId);

        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void getByUser_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        when(userService.getById(userId)).thenThrow(new ResourceNotFoundException("Usuário não encontrado com o ID: " + userId));

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getByUser(userId));
        verify(expenseRepository, never()).findByUserId(any());
    }

    @Test
    void getWithFilters_shouldReturnFilteredExpenses() {
        when(userService.getById(userId)).thenReturn(user);
        when(expenseRepository.findByUserId(userId)).thenReturn(List.of(expense));

        ExpenseFilter filter = new ExpenseFilter(expense.getDueDate().getMonthValue(), null, null, null, null, null, null);
        List<Expense> response = expenseService.getWithFilters(userId, filter);

        assertNotNull(response);
        assertEquals(1, response.size());
    }

}
