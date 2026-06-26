package br.com.finc2u.server.features.expense.projection;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FutureInstallmentProjectionTest {

    @Mock
    private ExpenseRepository expenseRepository;

    private FutureInstallmentProjection projection;
    private UUID userId;

    @BeforeEach
    void setUp() {
        projection = new FutureInstallmentProjection(expenseRepository);
        userId = UUID.randomUUID();
    }

    @Test
    void getProjectionsByUser_shouldProjectPendingInstallmentsGroupedByMonth() {
        Expense parceled = Expense.builder()
                .description("Notebook")
                .value(BigDecimal.valueOf(500))
                .dueDate(LocalDate.of(2026, 5, 10))
                .expenseType(ExpenseType.PARCELED)
                .currentInstallment(1)
                .totalInstallment(3)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        when(expenseRepository.findByExpenseTypeAndUserId(ExpenseType.PARCELED, userId))
                .thenReturn(List.of(parceled));

        Map<YearMonth, List<Expense>> result = projection.getProjectionsByUser(userId);

        assertEquals(2, result.size()); // junho e julho de 2026
        assertTrue(result.containsKey(YearMonth.of(2026, 6)));
        assertTrue(result.containsKey(YearMonth.of(2026, 7)));

        Expense june = result.get(YearMonth.of(2026, 6)).get(0);

        assertEquals("Notebook (2/3)", june.getDescription());
        assertEquals(LocalDate.of(2026, 6, 10), june.getDueDate());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(june.getValue()));
        assertEquals(PaymentStatus.PENDING, june.getPaymentStatus());
        assertEquals("Notebook (3/3)", result.get(YearMonth.of(2026, 7)).get(0).getDescription());
    }

    @Test
    void getProjectionsByUser_shouldBeEmpty_whenOnLastInstallment() {
        Expense last = Expense.builder()
                .description("TV")
                .value(BigDecimal.valueOf(200))
                .dueDate(LocalDate.of(2026, 5, 10))
                .expenseType(ExpenseType.PARCELED)
                .currentInstallment(3)
                .totalInstallment(3)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        when(expenseRepository.findByExpenseTypeAndUserId(ExpenseType.PARCELED, userId))
                .thenReturn(List.of(last));

        assertTrue(projection.getProjectionsByUser(userId).isEmpty());
    }

    @Test
    void getProjectionsByUser_shouldIgnorePaidExpenses() {
        Expense paid = Expense.builder()
                .description("Geladeira")
                .value(BigDecimal.valueOf(300))
                .dueDate(LocalDate.of(2026, 5, 10))
                .expenseType(ExpenseType.PARCELED)
                .currentInstallment(1)
                .totalInstallment(3)
                .paymentStatus(PaymentStatus.PAID)
                .build();

        when(expenseRepository.findByExpenseTypeAndUserId(ExpenseType.PARCELED, userId))
                .thenReturn(List.of(paid));

        Map<YearMonth, List<Expense>> result = projection.getProjectionsByUser(userId);

        assertTrue(result.isEmpty());
    }

}
