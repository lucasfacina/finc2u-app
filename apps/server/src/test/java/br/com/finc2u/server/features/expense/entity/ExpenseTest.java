package br.com.finc2u.server.features.expense.entity;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class ExpenseTest {

    // ---- resolveDueDate ----

    @Test
    void resolveDueDate_shouldKeepManualDueDate_whenAlreadyInformed() {
        LocalDate manual = LocalDate.of(2026, 7, 10);
        Expense expense = Expense.builder()
                .dueDate(manual)
                .build();

        expense.resolveDueDate(LocalDate.of(2026, 5, 1));

        assertEquals(manual, expense.getDueDate());
    }

    @Test
    void resolveDueDate_ShouldThrow_WhenNoCardAndNoDueDate() {
        Expense expense = Expense.builder()
                .build();

        assertThrows(BusinessException.class, () -> expense.resolveDueDate(null));
    }

    @Test
    void resolveDueDate_ShouldThrow_WhenCardWithoutClosingOrDueConfigured() {
        CardAccount card = CardAccount.builder()
                .build();
        Expense expense = Expense.builder()
                .cardAccount(card)
                .build();

        assertThrows(BusinessException.class, () -> expense.resolveDueDate(LocalDate.of(2026, 5, 1)));
    }

    @Test
    void resolveDueDate_ShouldCalculateFromCard_WhenDueDateNull() {
        CardAccount card = CardAccount.builder()
                .closingDate(30)
                .dueDate(15)
                .build();
        Expense expense = Expense.builder()
                .cardAccount(card)
                .build();

        expense.resolveDueDate(LocalDate.of(2026, 5, 20));

        assertEquals(LocalDate.of(2026, 6, 15), expense.getDueDate());
    }

    // ---- resolveDefaultPaymentStatus ----

    @Test
    void resolveDefaultPaymentStatus_shouldBePaid_whenNullAndDueDateNotInFuture() {
        Expense expense = Expense.builder()
                .dueDate(LocalDate.now().minusDays(1))
                .build();

        expense.resolveDefaultPaymentStatus();

        assertEquals(PaymentStatus.PAID, expense.getPaymentStatus());
    }

    @Test
    void resolveDefaultPaymentStatus_shouldBePending_whenNullAndDueDateInFuture() {
        Expense expense = Expense.builder()
                .dueDate(LocalDate.now().plusDays(5))
                .build();

        expense.resolveDefaultPaymentStatus();

        assertEquals(PaymentStatus.PENDING, expense.getPaymentStatus());
    }

    @Test
    void resolveDefaultPaymentStatus_shouldKeepExistingStatus_whenAlreadySet() {
        Expense expense = Expense.builder()
                .dueDate(LocalDate.now().minusDays(1))
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        expense.resolveDefaultPaymentStatus();

        assertEquals(PaymentStatus.PENDING, expense.getPaymentStatus());
    }

    // ---- applyInstallmentRules ----

    @Test
    void applyInstallmentRules_shouldThrow_whenParceledWithoutTotalInstallments() {
        Expense expense = Expense.builder()
                .expenseType(ExpenseType.PARCELED)
                .build();

        assertThrows(BusinessException.class, expense::applyInstallmentRules);
    }

    @Test
    void applyInstallmentRules_shouldThrow_whenParceledWithTotalBelowMinimum() {
        Expense expense = Expense.builder()
                .expenseType(ExpenseType.PARCELED)
                .totalInstallment(0)
                .build();

        assertThrows(BusinessException.class, expense::applyInstallmentRules);
    }

    @Test
    void applyInstallmentRules_shouldDefaultCurrentInstallment_whenNull() {
        Expense expense = Expense.builder()
                .expenseType(ExpenseType.PARCELED)
                .totalInstallment(3)
                .build();

        expense.applyInstallmentRules();

        assertEquals(1, expense.getCurrentInstallment());
    }

    @Test
    void applyInstallmentRules_shouldSetPaid_whenLastInstallment() {
        Expense expense = Expense.builder()
                .expenseType(ExpenseType.PARCELED)
                .currentInstallment(3)
                .totalInstallment(3)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        expense.applyInstallmentRules();

        assertEquals(PaymentStatus.PAID, expense.getPaymentStatus());
    }

    @Test
    void applyInstallmentRules_shouldThrow_whenCurrentInstallmentExceedsTotal() {
        Expense expense = Expense.builder()
                .expenseType(ExpenseType.PARCELED)
                .currentInstallment(10)
                .totalInstallment(5)
                .build();

        assertThrows(BusinessException.class, expense::applyInstallmentRules);
    }

    @Test
    void applyInstallmentRules_shouldDoNothing_whenNotParceled() {
        Expense expense = Expense.builder()
                .expenseType(ExpenseType.VARIABLE)
                .build();

        expense.applyInstallmentRules();

        assertNull(expense.getCurrentInstallment());
    }

    // ---- prepareForPersistence (orquestra a tríade na ordem correta) ----

    @Test
    void prepareForPersistence_shouldResolveDueDateFromCard() {
        CardAccount card = CardAccount.builder()
                .closingDate(30)
                .dueDate(15)
                .build();
        Expense expense = Expense.builder()
                .cardAccount(card)
                .expenseType(ExpenseType.VARIABLE)
                .build();

        expense.prepareForPersistence(LocalDate.of(2026, 5, 20));

        assertEquals(LocalDate.of(2026, 6, 15), expense.getDueDate());
    }

    @Test
    void prepareForPersistence_shouldDefaultStatusFromResolvedDueDate() {
        Expense future = Expense.builder()
                .dueDate(LocalDate.now().plusDays(5))
                .expenseType(ExpenseType.VARIABLE)
                .build();
        Expense past = Expense.builder()
                .dueDate(LocalDate.now().minusDays(5))
                .expenseType(ExpenseType.VARIABLE)
                .build();

        future.prepareForPersistence(null);
        past.prepareForPersistence(null);

        assertEquals(PaymentStatus.PENDING, future.getPaymentStatus());
        assertEquals(PaymentStatus.PAID, past.getPaymentStatus());
    }

    @Test
    void prepareForPersistence_shouldApplyInstallmentRules() {
        Expense expense = Expense.builder()
                .dueDate(LocalDate.now().plusDays(1))
                .expenseType(ExpenseType.PARCELED)
                .currentInstallment(3)
                .totalInstallment(3)
                .build();

        expense.prepareForPersistence(null);

        assertEquals(PaymentStatus.PAID, expense.getPaymentStatus());
    }
    
}
