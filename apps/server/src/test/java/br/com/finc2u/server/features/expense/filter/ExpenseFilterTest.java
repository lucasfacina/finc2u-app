package br.com.finc2u.server.features.expense.filter;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExpenseFilterTest {

    private final UUID cardId = UUID.randomUUID();

    private Expense sample() {
        CardAccount card = CardAccount.builder()
                .build();
        card.setId(cardId);
        return Expense.builder()
                .value(BigDecimal.valueOf(150))
                .dueDate(LocalDate.of(2026, 5, 10))
                .paymentStatus(PaymentStatus.PENDING)
                .expenseType(ExpenseType.VARIABLE)
                .cardAccount(card)
                .build();
    }

    @Test
    void matches_shouldReturnTrue_whenAllCriteriaNull() {
        ExpenseFilter filter =
                new ExpenseFilter(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        assertTrue(filter.matches(sample()));
    }

    @Test
    void matches_shouldReturnTrue_whenEveryCriterionSatisfied() {
        ExpenseFilter filter =
                new ExpenseFilter(
                        5,
                        2026,
                        PaymentStatus.PENDING,
                        ExpenseType.VARIABLE,
                        cardId,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(200)
                );

        assertTrue(filter.matches(sample()));
    }

    @Test
    void matches_shouldReturnFalse_whenMonthDiffers() {
        ExpenseFilter filter =
                new ExpenseFilter(
                        6,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        assertFalse(filter.matches(sample()));
    }

    @Test
    void matches_shouldReturnFalse_whenStatusDiffers() {
        ExpenseFilter filter =
                new ExpenseFilter(
                        null,
                        null,
                        PaymentStatus.PAID,
                        null,
                        null,
                        null,
                        null
                );

        assertFalse(filter.matches(sample()));
    }

    @Test
    void matches_shouldReturnFalse_whenCardDiffers() {
        ExpenseFilter filter =
                new ExpenseFilter(
                        null,
                        null,
                        null,
                        null,
                        UUID.randomUUID(),
                        null,
                        null
                );


        assertFalse(filter.matches(sample()));
    }

    @Test
    void matches_shouldReturnFalse_whenValueBelowMinPrice() {
        ExpenseFilter filter =
                new ExpenseFilter(
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.valueOf(200),
                        null
                );

        assertFalse(filter.matches(sample()));
    }

    @Test
    void matches_shouldReturnFalse_whenValueAboveMaxPrice() {
        ExpenseFilter filter =
                new ExpenseFilter(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.valueOf(100)
                );

        assertFalse(filter.matches(sample()));
    }

    @Test
    void matches_shouldHandleNullCardOnExpense_whenCardCriterionPresent() {
        Expense noCard = sample();
        noCard.setCardAccount(null);
        ExpenseFilter filter =
                new ExpenseFilter(
                        null,
                        null,
                        null,
                        null,
                        cardId,
                        null,
                        null
                );

        assertFalse(filter.matches(noCard));
    }

}
