package br.com.finc2u.server.features.expense.vo;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InstallmentScheduleTest {

    private final UUID groupId = UUID.randomUUID();
    private final User user = User.builder().name("Lucas").build();
    private final CardAccount card = CardAccount.builder().cardName("Nubank").build();
    private final Category category = Category.of("Compras");

    @Test
    void from_shouldGenerateTheInstallmentsFromTheInformedOneToTheLast() {
        var first = purchase(4, 10, LocalDate.of(2026, 6, 15));

        var installments = InstallmentSchedule.from(first).installments();

        assertEquals(7, installments.size());
        assertEquals(List.of(4, 5, 6, 7, 8, 9, 10), installments.stream().map(Expense::getCurrentInstallment).toList());
    }

    @Test
    void from_shouldKeepTheInformedInstallmentAsTheFirstElement() {
        var first = purchase(4, 10, LocalDate.of(2026, 6, 15));

        var installments = InstallmentSchedule.from(first).installments();

        assertSame(first, installments.getFirst());
    }

    @Test
    void from_shouldGenerateASingleLine_whenItIsTheLastInstallment() {
        assertEquals(1, InstallmentSchedule.from(purchase(1, 1, LocalDate.of(2026, 6, 15))).installments().size());
        assertEquals(1, InstallmentSchedule.from(purchase(10, 10, LocalDate.of(2026, 6, 15))).installments().size());
    }

    @Test
    void from_shouldDueEachInstallmentOneMonthAfterThePrevious() {
        var first = purchase(4, 6, LocalDate.of(2026, 6, 15));

        var dueDates = InstallmentSchedule.from(first).installments().stream().map(Expense::getDueDate).toList();

        assertEquals(List.of(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15)), dueDates);
    }

    @Test
    void from_shouldCountMonthsFromTheFirstDueDate_notChainedFromThePreviousOne() {
        var first = purchase(1, 4, LocalDate.of(2026, 1, 31));

        var dueDates = InstallmentSchedule.from(first).installments().stream().map(Expense::getDueDate).toList();

        assertEquals(List.of(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 4, 30)), dueDates);
    }

    @Test
    void from_shouldKeepTheInformedStatusOnlyOnTheFirstInstallment() {
        var first = purchase(2, 4, LocalDate.of(2026, 6, 1));
        first.setPaymentStatus(PaymentStatus.PAID);
        first.setPaidAt(LocalDate.of(2026, 6, 1));

        var installments = InstallmentSchedule.from(first).installments();

        assertEquals(PaymentStatus.PAID, installments.getFirst().getPaymentStatus());
        assertEquals(LocalDate.of(2026, 6, 1), installments.getFirst().getPaidAt());
        installments.stream().skip(1).forEach(installment -> {
            assertEquals(PaymentStatus.PENDING, installment.getPaymentStatus());
            assertNull(installment.getPaidAt());
        });
    }

    @Test
    void from_shouldLinkAllInstallmentsToTheSamePurchaseGroup() {
        var installments = InstallmentSchedule.from(purchase(1, 5, LocalDate.of(2026, 6, 15))).installments();

        assertTrue(installments.stream().allMatch(installment -> groupId.equals(installment.getInstallmentGroupId())));
    }

    @Test
    void from_shouldCopyThePurchaseDataToTheGeneratedInstallments() {
        var installments = InstallmentSchedule.from(purchase(1, 3, LocalDate.of(2026, 6, 15))).installments();

        installments.forEach(installment -> {
            assertEquals("Notebook", installment.getDescription());
            assertEquals(0, BigDecimal.valueOf(500).compareTo(installment.getValue()));
            assertEquals(ExpenseType.PARCELED, installment.getExpenseType());
            assertEquals(3, installment.getTotalInstallment());
            assertSame(user, installment.getUser());
            assertSame(card, installment.getCardAccount());
            assertSame(category, installment.getCategory());
        });
    }

    @Test
    void from_shouldCreateNewEntitiesForTheGeneratedInstallments() {
        var installments = InstallmentSchedule.from(purchase(1, 3, LocalDate.of(2026, 6, 15))).installments();

        assertNotSame(installments.get(1), installments.get(2));
        assertNull(installments.get(1).getId());
        assertNull(installments.get(2).getId());
    }

    @Test
    void from_shouldSupportTheMaximumNumberOfInstallments() {
        var installments = InstallmentSchedule.from(purchase(1, 120, LocalDate.of(2026, 1, 31))).installments();

        assertEquals(120, installments.size());
        assertEquals(LocalDate.of(2035, 12, 31), installments.getLast().getDueDate());
    }

    @Test
    void from_shouldRejectAnExpenseThatIsNotParceled() {
        var first = purchase(1, 3, LocalDate.of(2026, 6, 15));
        first.setExpenseType(ExpenseType.VARIABLE);

        assertThrows(IllegalArgumentException.class, () -> InstallmentSchedule.from(first));
    }

    @Test
    void from_shouldRejectAPurchaseWithoutInstallmentGroup() {
        var first = purchase(1, 3, LocalDate.of(2026, 6, 15));
        first.setInstallmentGroupId(null);

        assertThrows(IllegalArgumentException.class, () -> InstallmentSchedule.from(first));
    }

    private Expense purchase(int current, int total, LocalDate dueDate) {
        return Expense.builder()
            .description("Notebook")
            .value(BigDecimal.valueOf(500))
            .dueDate(dueDate)
            .expenseType(ExpenseType.PARCELED)
            .paymentStatus(PaymentStatus.PENDING)
            .currentInstallment(current)
            .totalInstallment(total)
            .installmentGroupId(groupId)
            .user(user)
            .cardAccount(card)
            .category(category)
            .build();
    }

}
