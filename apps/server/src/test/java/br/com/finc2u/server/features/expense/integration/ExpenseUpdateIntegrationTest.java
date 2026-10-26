package br.com.finc2u.server.features.expense.integration;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ExpenseUpdateIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

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

    // O PUT de uma parcela que só troca a descrição e não manda a numeração não renumera a linha nem mexe nas irmãs.
    @Test
    void update_shouldKeepTheNumberingAndTheGroup_whenOnlyTheDescriptionOfAnInstallmentChanges() {
        var userId = registerUser("parcela.descricao@email.com");
        var groupId = purchase(userId).getInstallmentGroupId();
        var fifth = installments(groupId).get(1);

        expenseService.update(fifth.getId(), edit(userId, "Notebook (remarcada)", ExpenseType.PARCELED, null, null, fifth.getDueDate()));

        var group = installments(groupId);
        assertEquals(List.of(4, 5, 6, 7, 8, 9, 10), group.stream().map(Expense::getCurrentInstallment).toList());
        assertTrue(group.stream().allMatch(installment -> installment.getTotalInstallment() == 10
            && installment.getExpenseType() == ExpenseType.PARCELED));
        assertEquals("Notebook (remarcada)", group.get(1).getDescription());
        assertTrue(group.stream().filter(installment -> installment.getCurrentInstallment() != 5)
            .allMatch(installment -> installment.getDescription().equals("Notebook")));
    }

    @Test
    void update_shouldFailAndKeepTheGroup_whenAnInstallmentChangesItsType() {
        var userId = registerUser("parcela.tipo@email.com");
        var groupId = purchase(userId).getInstallmentGroupId();
        var fifth = installments(groupId).get(1);

        assertThrows(BusinessException.class, () -> expenseService.update(fifth.getId(),
            edit(userId, "Notebook", ExpenseType.VARIABLE, null, null, fifth.getDueDate())));

        var group = installments(groupId);
        assertEquals(7, group.size());
        assertTrue(group.stream().allMatch(installment -> installment.getExpenseType() == ExpenseType.PARCELED));
    }

    @Test
    void update_shouldFailAndKeepTheGroup_whenAnInstallmentChangesItsNumber() {
        var userId = registerUser("parcela.numero@email.com");
        var groupId = purchase(userId).getInstallmentGroupId();
        var fifth = installments(groupId).get(1);

        assertThrows(BusinessException.class, () -> expenseService.update(fifth.getId(),
            edit(userId, "Notebook", ExpenseType.PARCELED, 1, 10, fifth.getDueDate())));

        assertEquals(List.of(4, 5, 6, 7, 8, 9, 10), installments(groupId).stream().map(Expense::getCurrentInstallment).toList());
    }

    @Test
    void update_shouldFail_whenAStandaloneExpenseBecomesParceled() {
        var userId = registerUser("parcela.avulsa@email.com");
        var standalone = expenseService.create(userId, edit(userId, "Mercado", ExpenseType.VARIABLE, null, null, LocalDate.of(2026, 11, 5)));

        assertThrows(BusinessException.class, () -> expenseService.update(standalone.getId(),
            edit(userId, "Mercado", ExpenseType.PARCELED, 1, 3, LocalDate.of(2026, 11, 5))));

        var unchanged = expenseService.getById(standalone.getId());
        assertEquals(ExpenseType.VARIABLE, unchanged.getExpenseType());
        assertNull(unchanged.getInstallmentGroupId());
    }

    @Test
    void update_shouldKeepWorking_forAStandaloneExpense() {
        var userId = registerUser("parcela.normal@email.com");
        var standalone = expenseService.create(userId, edit(userId, "Mercado", ExpenseType.VARIABLE, null, null, LocalDate.of(2026, 11, 5)));

        expenseService.update(standalone.getId(), edit(userId, "Feira", ExpenseType.FIXED, null, null, LocalDate.of(2026, 11, 12)));

        var updated = expenseService.getById(standalone.getId());
        assertEquals("Feira", updated.getDescription());
        assertEquals(ExpenseType.FIXED, updated.getExpenseType());
        assertEquals(LocalDate.of(2026, 11, 12), updated.getDueDate());
    }

    // Compra de "Notebook" em 10x, informada na parcela 4: o POST grava as parcelas 4 a 10 (7 linhas, um grupo só).
    private Expense purchase(UUID userId) {
        return expenseService.create(userId, edit(userId, "Notebook", ExpenseType.PARCELED, 4, 10, LocalDate.of(2026, 11, 5)));
    }

    private List<Expense> installments(UUID groupId) {
        return expenseService.getByInstallmentGroup(groupId);
    }

    private ExpenseCommand edit(UUID userId, String description, ExpenseType type, Integer current, Integer total, LocalDate dueDate) {
        var expense = Expense.builder()
            .description(description)
            .value(BigDecimal.valueOf(100))
            .dueDate(dueDate)
            .expenseType(type)
            .paymentStatus(PaymentStatus.PENDING)
            .currentInstallment(current)
            .totalInstallment(total)
            .build();
        return ExpenseCommand.of(expense).withCategoryId(categoryService.getByUser(userId).getFirst().getId());
    }

    private UUID registerUser(String email) {
        var user = User.builder()
            .name("Ana")
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1)).getId();
    }

}
