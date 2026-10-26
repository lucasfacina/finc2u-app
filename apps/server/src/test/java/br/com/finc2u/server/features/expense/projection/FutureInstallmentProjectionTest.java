package br.com.finc2u.server.features.expense.projection;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FutureInstallmentProjectionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserService userService;

    private FutureInstallmentProjection projection;
    private UUID userId;

    @BeforeEach
    void setUp() {
        projection = new FutureInstallmentProjection(expenseRepository, userService);
        userId = UUID.randomUUID();
    }

    @Test
    void getProjectionsByUser_shouldQueryPendingParceledExpensesAfterTheReferenceDate() {
        when(expenseRepository.findByUserIdAndExpenseTypeAndPaymentStatusAndDueDateAfterOrderByDueDate(
            userId, ExpenseType.PARCELED, PaymentStatus.PENDING, TODAY)).thenReturn(List.of());

        assertTrue(projection.getProjectionsByUser(userId, TODAY).isEmpty());

        verify(expenseRepository).findByUserIdAndExpenseTypeAndPaymentStatusAndDueDateAfterOrderByDueDate(
            userId, ExpenseType.PARCELED, PaymentStatus.PENDING, TODAY);
    }

    @Test
    void getProjectionsByUser_shouldThrowResourceNotFound_andReadNothing_whenTheUserDoesNotExist() {
        doThrow(new ResourceNotFoundException("Usuário não encontrado com o ID: " + userId)).when(userService).getById(userId);

        assertThrows(ResourceNotFoundException.class, () -> projection.getProjectionsByUser(userId, TODAY));

        verifyNoInteractions(expenseRepository);
    }

    @Test
    void getProjectionsByUser_shouldGroupTheRealInstallmentsByDueMonth_keepingTheirData() {
        var july = installment("Notebook", 2, LocalDate.of(2026, 7, 10));
        var august = installment("Notebook", 3, LocalDate.of(2026, 8, 10));
        var otherJuly = installment("TV", 1, LocalDate.of(2026, 7, 25));
        when(expenseRepository.findByUserIdAndExpenseTypeAndPaymentStatusAndDueDateAfterOrderByDueDate(
            userId, ExpenseType.PARCELED, PaymentStatus.PENDING, TODAY)).thenReturn(List.of(july, otherJuly, august));

        var result = projection.getProjectionsByUser(userId, TODAY);

        assertEquals(2, result.size());
        assertEquals(List.of(july, otherJuly), result.get(YearMonth.of(2026, 7)));
        assertEquals(List.of(august), result.get(YearMonth.of(2026, 8)));
        assertSame(july, result.get(YearMonth.of(2026, 7)).getFirst());
        assertEquals("Notebook", result.get(YearMonth.of(2026, 8)).getFirst().getDescription());
    }

    // O repository ordena por vencimento, mas a ordem das chaves não pode depender disso (nem do mapa que agrupa): sempre crescente.
    @Test
    void getProjectionsByUser_shouldReturnTheMonthsInAscendingOrder_whateverTheOrderOfTheRows() {
        var december = installment("Notebook", 5, LocalDate.of(2026, 12, 10));
        var august = installment("Notebook", 1, LocalDate.of(2026, 8, 10));
        var january = installment("Notebook", 6, LocalDate.of(2027, 1, 10));
        var october = installment("Notebook", 3, LocalDate.of(2026, 10, 10));
        when(expenseRepository.findByUserIdAndExpenseTypeAndPaymentStatusAndDueDateAfterOrderByDueDate(
            userId, ExpenseType.PARCELED, PaymentStatus.PENDING, TODAY)).thenReturn(List.of(december, august, january, october));

        var result = projection.getProjectionsByUser(userId, TODAY);

        assertEquals(List.of(YearMonth.of(2026, 8), YearMonth.of(2026, 10), YearMonth.of(2026, 12), YearMonth.of(2027, 1)),
            new ArrayList<>(result.keySet()));
    }

    private Expense installment(String description, int number, LocalDate dueDate) {
        var expense = Expense.builder()
            .description(description)
            .value(BigDecimal.valueOf(500))
            .dueDate(dueDate)
            .expenseType(ExpenseType.PARCELED)
            .paymentStatus(PaymentStatus.PENDING)
            .currentInstallment(number)
            .totalInstallment(3)
            .build();
        expense.setId(UUID.randomUUID());
        return expense;
    }

}
