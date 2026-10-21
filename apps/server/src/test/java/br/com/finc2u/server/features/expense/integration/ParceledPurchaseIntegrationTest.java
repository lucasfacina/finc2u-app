package br.com.finc2u.server.features.expense.integration;

import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class ParceledPurchaseIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }

    }

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private MonthlySummaryService monthlySummaryService;

    @MockitoSpyBean
    private ExpenseRepository expenseRepository;

    @Test
    void create_shouldPersistEveryInstallmentFromTheInformedOne() {
        var userId = registerUser("parcelas.todas@email.com");

        var first = expenseService.create(userId, purchase(userId, 4, 10));

        var saved = installmentsOf(userId);
        assertEquals(7, saved.size());
        assertEquals(List.of(4, 5, 6, 7, 8, 9, 10), saved.stream().map(Expense::getCurrentInstallment).toList());
        assertEquals(first.getId(), saved.getFirst().getId());
        assertNotNull(first.getInstallmentGroupId());
        assertTrue(saved.stream().allMatch(e -> first.getInstallmentGroupId().equals(e.getInstallmentGroupId())));
        assertEquals(LocalDate.of(2026, 7, 20), saved.get(1).getDueDate());
        assertEquals(LocalDate.of(2026, 12, 20), saved.getLast().getDueDate());
    }

    @Test
    void monthlySummary_shouldSeeTheInstallmentOfTheFollowingMonths() {
        var userId = registerUser("parcelas.resumo@email.com");
        expenseService.create(userId, purchase(userId, 4, 10));

        var july = monthlySummaryService.calculateOrRecalculate(userId, 7, 2026);
        var december = monthlySummaryService.calculateOrRecalculate(userId, 12, 2026);
        var january = monthlySummaryService.calculateOrRecalculate(userId, 1, 2027);

        assertEquals(0, BigDecimal.valueOf(100).compareTo(july.getTotalExpenses()));
        assertEquals(0, BigDecimal.valueOf(100).compareTo(december.getTotalExpenses()));
        assertEquals(0, BigDecimal.ZERO.compareTo(january.getTotalExpenses()));
    }

    @Test
    void create_shouldPersistNoInstallment_whenSavingAFollowingOneFails() {
        var userId = registerUser("parcelas.rollback@email.com");
        doAnswer(invocation -> {
            List<Expense> installments = invocation.getArgument(0);
            expenseRepository.saveAndFlush(installments.getFirst());
            throw new IllegalStateException("falha ao gravar as demais parcelas");
        }).when(expenseRepository).saveAll(any());

        // o Spring Data traduz a exceção do repositório (IllegalStateException vira DataAccessException)
        assertThrows(RuntimeException.class, () -> expenseService.create(userId, purchase(userId, 4, 10)));

        assertTrue(expenseRepository.findByUserId(userId).isEmpty());
    }

    private UUID registerUser(String email) {
        var user = User.builder()
            .name("Ana")
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1)).getId();
    }

    private ExpenseCommand purchase(UUID userId, int current, int total) {
        var expense = Expense.builder()
            .description("Notebook")
            .value(BigDecimal.valueOf(100))
            .dueDate(LocalDate.of(2026, 6, 20))
            .expenseType(ExpenseType.PARCELED)
            .paymentStatus(PaymentStatus.PENDING)
            .currentInstallment(current)
            .totalInstallment(total)
            .build();
        var categoryId = categoryService.getByUser(userId).getFirst().getId();
        return new ExpenseCommand(expense, null, null, categoryId, null);
    }

    private List<Expense> installmentsOf(UUID userId) {
        return expenseRepository.findByUserId(userId).stream()
            .sorted(Comparator.comparing(Expense::getCurrentInstallment))
            .toList();
    }

}
