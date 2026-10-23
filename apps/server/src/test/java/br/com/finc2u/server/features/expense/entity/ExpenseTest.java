package br.com.finc2u.server.features.expense.entity;

import br.com.finc2u.server.config.BusinessConstants;
import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class ExpenseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    // ---- resolveDueDate ----

    @Test
    void resolveDueDate_shouldKeepManualDueDate_whenAlreadyInformed() {
        var manual = LocalDate.of(2026, 7, 10);
        var expense = Expense.builder()
            .dueDate(manual)
            .build();

        expense.resolveDueDate(LocalDate.of(2026, 5, 1));

        assertEquals(manual, expense.getDueDate());
    }

    @Test
    void resolveDueDate_ShouldThrow_WhenNoCardAndNoDueDate() {
        var expense = Expense.builder()
            .build();

        assertThrows(BusinessException.class, () -> expense.resolveDueDate(null));
    }

    @Test
    void resolveDueDate_ShouldThrow_WhenCardWithoutClosingOrDueConfigured() {
        var card = CardAccount.builder()
            .build();
        var expense = Expense.builder()
            .cardAccount(card)
            .build();

        assertThrows(BusinessException.class,
            () -> expense.resolveDueDate(LocalDate.of(2026, 5, 1)));
    }

    @Test
    void resolveDueDate_ShouldCalculateFromCard_WhenDueDateNull() {
        var card = CardAccount.builder()
            .closingDate(30)
            .dueDate(15)
            .build();
        var expense = Expense.builder()
            .cardAccount(card)
            .build();

        expense.resolveDueDate(LocalDate.of(2026, 5, 20));

        assertEquals(LocalDate.of(2026, 6, 15), expense.getDueDate());
    }

    // ---- resolveDefaultPaymentStatus ----

    @Test
    void resolveDefaultPaymentStatus_shouldThrowInvalidRequest_whenNullAndDueDateInThePast() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusDays(1))
            .build();

        var exception = assertThrows(InvalidRequestException.class, () -> expense.resolveDefaultPaymentStatus(TODAY));

        assertEquals("Vencimento anterior a hoje: informe se a despesa já foi paga (paymentStatus).", exception.getMessage());
        assertNull(expense.getPaymentStatus());
    }

    @Test
    void resolveDefaultPaymentStatus_shouldBePending_whenNullAndDueDateIsToday() {
        var expense = Expense.builder()
            .dueDate(TODAY)
            .build();

        expense.resolveDefaultPaymentStatus(TODAY);

        assertEquals(PaymentStatus.PENDING, expense.getPaymentStatus());
    }

    @Test
    void resolveDefaultPaymentStatus_shouldBePending_whenNullAndDueDateInFuture() {
        var expense = Expense.builder()
            .dueDate(TODAY.plusDays(5))
            .build();

        expense.resolveDefaultPaymentStatus(TODAY);

        assertEquals(PaymentStatus.PENDING, expense.getPaymentStatus());
    }

    @Test
    void resolveDefaultPaymentStatus_shouldKeepExistingStatus_whenAlreadySet() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusDays(1))
            .paymentStatus(PaymentStatus.PENDING)
            .build();

        expense.resolveDefaultPaymentStatus(TODAY);

        assertEquals(PaymentStatus.PENDING, expense.getPaymentStatus());
    }

    // ---- paidAt (prepareForPersistence) ----

    @Test
    void prepareForPersistence_shouldKeepPaidAtNull_whenPaidWithoutDate() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusDays(3))
            .paymentStatus(PaymentStatus.PAID)
            .expenseType(ExpenseType.VARIABLE)
            .build();

        expense.prepareForPersistence(TODAY, TODAY);

        assertNull(expense.getPaidAt());
    }

    @Test
    void prepareForPersistence_shouldAcceptPaidAtUpToToday_whenPaid() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusDays(3))
            .paymentStatus(PaymentStatus.PAID)
            .paidAt(TODAY)
            .expenseType(ExpenseType.VARIABLE)
            .build();

        assertDoesNotThrow(() -> expense.prepareForPersistence(TODAY, TODAY));
    }

    @Test
    void prepareForPersistence_shouldThrowBusinessException_whenPaidAtIsInTheFuture() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusDays(3))
            .paymentStatus(PaymentStatus.PAID)
            .paidAt(TODAY.plusDays(1))
            .expenseType(ExpenseType.VARIABLE)
            .build();

        var exception = assertThrows(BusinessException.class, () -> expense.prepareForPersistence(TODAY, TODAY));

        assertEquals("A data do pagamento não pode ser posterior a hoje.", exception.getMessage());
    }

    @Test
    void prepareForPersistence_shouldThrowBusinessException_whenPaidAtIsInformedForPendingExpense() {
        var expense = Expense.builder()
            .dueDate(TODAY.plusDays(3))
            .paymentStatus(PaymentStatus.PENDING)
            .paidAt(TODAY)
            .expenseType(ExpenseType.VARIABLE)
            .build();

        var exception = assertThrows(BusinessException.class, () -> expense.prepareForPersistence(TODAY, TODAY));

        assertEquals("A data do pagamento só pode ser informada para despesa paga.", exception.getMessage());
    }

    @Test
    void prepareForPersistence_shouldThrowBusinessException_whenPaidAtIsInformedAndStatusDefaultsToPending() {
        var expense = Expense.builder()
            .dueDate(TODAY.plusDays(3))
            .paidAt(TODAY)
            .expenseType(ExpenseType.VARIABLE)
            .build();

        assertThrows(BusinessException.class, () -> expense.prepareForPersistence(TODAY, TODAY));
    }

    // ---- changePaymentStatus ----

    @Test
    void changePaymentStatus_shouldDefaultPaidAtToReferenceDate_whenMarkedPaidWithoutDate() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusDays(3))
            .paymentStatus(PaymentStatus.PENDING)
            .build();

        expense.changePaymentStatus(PaymentStatus.PAID, null, TODAY);

        assertEquals(PaymentStatus.PAID, expense.getPaymentStatus());
        assertEquals(TODAY, expense.getPaidAt());
    }

    @Test
    void changePaymentStatus_shouldKeepInformedPaidAt_whenMarkedPaid() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusDays(3))
            .paymentStatus(PaymentStatus.PENDING)
            .build();

        expense.changePaymentStatus(PaymentStatus.PAID, TODAY.minusDays(2), TODAY);

        assertEquals(TODAY.minusDays(2), expense.getPaidAt());
    }

    @Test
    void changePaymentStatus_shouldClearPaidAt_whenBackToPending() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusDays(3))
            .paymentStatus(PaymentStatus.PAID)
            .paidAt(TODAY.minusDays(2))
            .build();

        expense.changePaymentStatus(PaymentStatus.PENDING, null, TODAY);

        assertEquals(PaymentStatus.PENDING, expense.getPaymentStatus());
        assertNull(expense.getPaidAt());
    }

    @Test
    void changePaymentStatus_shouldThrowAndKeepState_whenPaidAtIsInTheFuture() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusDays(3))
            .paymentStatus(PaymentStatus.PENDING)
            .build();

        assertThrows(BusinessException.class,
            () -> expense.changePaymentStatus(PaymentStatus.PAID, TODAY.plusDays(1), TODAY));

        assertEquals(PaymentStatus.PENDING, expense.getPaymentStatus());
        assertNull(expense.getPaidAt());
    }

    @Test
    void changePaymentStatus_shouldThrow_whenPaidAtIsInformedForPending() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusDays(3))
            .paymentStatus(PaymentStatus.PAID)
            .build();

        assertThrows(BusinessException.class,
            () -> expense.changePaymentStatus(PaymentStatus.PENDING, TODAY, TODAY));
    }

    // ---- isOverdue ----

    @Test
    void isOverdue_shouldBeTrue_whenPendingAndDueDateIsBeforeReferenceDate() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusDays(1))
            .paymentStatus(PaymentStatus.PENDING)
            .build();

        assertTrue(expense.isOverdue(TODAY));
    }

    @Test
    void isOverdue_shouldBeFalse_whenDueDateIsToday() {
        var expense = Expense.builder()
            .dueDate(TODAY)
            .paymentStatus(PaymentStatus.PENDING)
            .build();

        assertFalse(expense.isOverdue(TODAY));
    }

    @Test
    void isOverdue_shouldBeFalse_whenDueDateIsInTheFuture() {
        var expense = Expense.builder()
            .dueDate(TODAY.plusDays(1))
            .paymentStatus(PaymentStatus.PENDING)
            .build();

        assertFalse(expense.isOverdue(TODAY));
    }

    @Test
    void isOverdue_shouldBeFalse_whenPaid() {
        var expense = Expense.builder()
            .dueDate(TODAY.minusMonths(1))
            .paymentStatus(PaymentStatus.PAID)
            .build();

        assertFalse(expense.isOverdue(TODAY));
    }

    // ---- applyInstallmentRules ----

    @Test
    void applyInstallmentRules_shouldThrow_whenParceledWithoutTotalInstallments() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.PARCELED)
            .build();

        assertThrows(BusinessException.class, expense::applyInstallmentRules);
    }

    @Test
    void applyInstallmentRules_shouldThrow_whenParceledWithTotalBelowMinimum() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.PARCELED)
            .totalInstallment(0)
            .build();

        assertThrows(BusinessException.class, expense::applyInstallmentRules);
    }

    @Test
    void applyInstallmentRules_shouldDefaultCurrentInstallment_whenNull() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.PARCELED)
            .totalInstallment(3)
            .build();

        expense.applyInstallmentRules();

        assertEquals(1, expense.getCurrentInstallment());
    }

    // A última parcela não vira PAID por ser a última: quem decide o status é o pagamento.
    @Test
    void applyInstallmentRules_shouldKeepStatus_whenLastInstallment() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.PARCELED)
            .currentInstallment(3)
            .totalInstallment(3)
            .paymentStatus(PaymentStatus.PENDING)
            .build();

        expense.applyInstallmentRules();

        assertEquals(PaymentStatus.PENDING, expense.getPaymentStatus());
    }

    @Test
    void applyInstallmentRules_shouldKeepStatus_whenSingleInstallmentPurchase() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.PARCELED)
            .totalInstallment(1)
            .paymentStatus(PaymentStatus.PENDING)
            .build();

        expense.applyInstallmentRules();

        assertEquals(1, expense.getCurrentInstallment());
        assertEquals(PaymentStatus.PENDING, expense.getPaymentStatus());
    }

    @Test
    void applyInstallmentRules_shouldKeepPaid_whenLastInstallmentWasInformedAsPaid() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.PARCELED)
            .currentInstallment(5)
            .totalInstallment(5)
            .paymentStatus(PaymentStatus.PAID)
            .build();

        expense.applyInstallmentRules();

        assertEquals(PaymentStatus.PAID, expense.getPaymentStatus());
    }

    @Test
    void applyInstallmentRules_shouldThrow_whenCurrentInstallmentExceedsTotal() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.PARCELED)
            .currentInstallment(10)
            .totalInstallment(5)
            .build();

        assertThrows(BusinessException.class, expense::applyInstallmentRules);
    }

    @Test
    void applyInstallmentRules_shouldThrow_whenCurrentInstallmentIsZero() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.PARCELED)
            .currentInstallment(0)
            .totalInstallment(3)
            .build();

        assertThrows(BusinessException.class, expense::applyInstallmentRules);
    }

    @Test
    void applyInstallmentRules_shouldThrow_whenCurrentInstallmentIsNegative() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.PARCELED)
            .currentInstallment(-5)
            .totalInstallment(3)
            .build();

        assertThrows(BusinessException.class, expense::applyInstallmentRules);
    }

    @Test
    void applyInstallmentRules_shouldThrow_whenTotalInstallmentExceedsMaximum() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.PARCELED)
            .totalInstallment(BusinessConstants.MAX_INSTALLMENTS + 1)
            .build();

        assertThrows(BusinessException.class, expense::applyInstallmentRules);
    }

    @Test
    void applyInstallmentRules_shouldAccept_whenTotalInstallmentIsTheMaximum() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.PARCELED)
            .totalInstallment(BusinessConstants.MAX_INSTALLMENTS)
            .build();

        expense.applyInstallmentRules();

        assertEquals(1, expense.getCurrentInstallment());
    }

    @Test
    void applyInstallmentRules_shouldDoNothing_whenNotParceled() {
        var expense = Expense.builder()
            .expenseType(ExpenseType.VARIABLE)
            .build();

        expense.applyInstallmentRules();

        assertNull(expense.getCurrentInstallment());
    }

    // ---- prepareForPersistence (orquestra a tríade na ordem correta) ----

    @Test
    void prepareForPersistence_shouldResolveDueDateFromCard() {
        var card = CardAccount.builder()
            .closingDate(30)
            .dueDate(15)
            .build();
        var expense = Expense.builder()
            .cardAccount(card)
            .expenseType(ExpenseType.VARIABLE)
            .build();

        expense.prepareForPersistence(LocalDate.of(2026, 5, 20), TODAY);

        assertEquals(LocalDate.of(2026, 6, 15), expense.getDueDate());
    }

    @Test
    void prepareForPersistence_shouldDefaultStatusToPending_whenResolvedDueDateIsNotInThePast() {
        var future = Expense.builder()
            .dueDate(TODAY.plusDays(5))
            .expenseType(ExpenseType.VARIABLE)
            .build();

        future.prepareForPersistence(TODAY, TODAY);

        assertEquals(PaymentStatus.PENDING, future.getPaymentStatus());
    }

    @Test
    void prepareForPersistence_shouldThrowInvalidRequest_whenStatusIsMissingAndResolvedDueDateIsInThePast() {
        var card = CardAccount.builder()
            .closingDate(30)
            .dueDate(15)
            .build();
        var expense = Expense.builder()
            .cardAccount(card)
            .expenseType(ExpenseType.VARIABLE)
            .build();

        // compra de 20/mai → fatura vence em 15/jun, que é anterior à data de referência (16/jun)
        assertThrows(InvalidRequestException.class,
            () -> expense.prepareForPersistence(LocalDate.of(2026, 5, 20), TODAY.plusDays(1)));
    }

    @Test
    void prepareForPersistence_shouldApplyInstallmentRules() {
        var expense = Expense.builder()
            .dueDate(TODAY.plusDays(1))
            .expenseType(ExpenseType.PARCELED)
            .totalInstallment(3)
            .build();

        expense.prepareForPersistence(TODAY, TODAY);

        assertEquals(1, expense.getCurrentInstallment());
    }

    @Test
    void prepareForPersistence_shouldKeepFutureLastInstallmentPending() {
        var expense = Expense.builder()
            .dueDate(TODAY.plusDays(1))
            .expenseType(ExpenseType.PARCELED)
            .currentInstallment(3)
            .totalInstallment(3)
            .build();

        expense.prepareForPersistence(TODAY, TODAY);

        assertEquals(PaymentStatus.PENDING, expense.getPaymentStatus());
    }

}
