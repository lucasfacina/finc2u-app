package br.com.finc2u.server.features.recurringexpense.entity;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecurringExpenseTest {

    @Test
    void isActiveIn_shouldBeFalseBeforeTheStartMonth() {
        var recurringExpense = modelOf(5, YearMonth.of(2026, 10), YearMonth.of(2027, 3));

        assertFalse(recurringExpense.isActiveIn(YearMonth.of(2026, 9)));
    }

    @Test
    void isActiveIn_shouldIncludeTheStartAndTheEndMonths() {
        var recurringExpense = modelOf(5, YearMonth.of(2026, 10), YearMonth.of(2027, 3));

        assertTrue(recurringExpense.isActiveIn(YearMonth.of(2026, 10)));
        assertTrue(recurringExpense.isActiveIn(YearMonth.of(2027, 1)));
        assertTrue(recurringExpense.isActiveIn(YearMonth.of(2027, 3)));
    }

    @Test
    void isActiveIn_shouldBeFalseAfterTheEndMonth() {
        var recurringExpense = modelOf(5, YearMonth.of(2026, 10), YearMonth.of(2027, 3));

        assertFalse(recurringExpense.isActiveIn(YearMonth.of(2027, 4)));
    }

    @Test
    void isActiveIn_shouldNeverEnd_whenThereIsNoEndMonth() {
        var recurringExpense = modelOf(5, YearMonth.of(2026, 10), null);

        assertTrue(recurringExpense.isActiveIn(YearMonth.of(2040, 1)));
    }

    @Test
    void dueDateIn_shouldUseTheDayOfTheModel_whenThereIsNoCard() {
        var recurringExpense = modelOf(5, YearMonth.of(2026, 10), null);

        assertEquals(LocalDate.of(2026, 12, 5), recurringExpense.dueDateIn(YearMonth.of(2026, 12)));
    }

    @Test
    void dueDateIn_shouldFallOnTheLastDayOfTheMonth_whenTheMonthIsShorterThanTheDay() {
        var recurringExpense = modelOf(31, YearMonth.of(2026, 10), null);

        assertEquals(LocalDate.of(2027, 2, 28), recurringExpense.dueDateIn(YearMonth.of(2027, 2)));
        assertEquals(LocalDate.of(2028, 2, 29), recurringExpense.dueDateIn(YearMonth.of(2028, 2)));
        assertEquals(LocalDate.of(2026, 11, 30), recurringExpense.dueDateIn(YearMonth.of(2026, 11)));
        assertEquals(LocalDate.of(2026, 12, 31), recurringExpense.dueDateIn(YearMonth.of(2026, 12)));
    }

    // Compra dia 5, fatura fecha dia 10 e vence dia 17: cai na fatura do próprio mês.
    @Test
    void dueDateIn_shouldFollowTheInvoice_whenThereIsACard() {
        var recurringExpense = modelOf(5, YearMonth.of(2026, 10), null);
        recurringExpense.setCardAccount(cardOf(10, 17));

        assertEquals(LocalDate.of(2026, 10, 17), recurringExpense.dueDateIn(YearMonth.of(2026, 10)));
    }

    // Compra dia 5 depois do fechamento (dia 3): entra na fatura seguinte, que vence no mês seguinte.
    @Test
    void dueDateIn_shouldFallOnTheNextMonth_whenThePurchaseIsAfterTheClosingDay() {
        var recurringExpense = modelOf(5, YearMonth.of(2026, 10), null);
        recurringExpense.setCardAccount(cardOf(3, 10));

        assertEquals(LocalDate.of(2026, 11, 10), recurringExpense.dueDateIn(YearMonth.of(2026, 10)));
    }

    // Dia 31 em fevereiro: a compra é no dia 28 (último do mês) e a fatura é calculada a partir dela.
    @Test
    void dueDateIn_shouldUseTheClampedChargeDayToFindTheInvoice() {
        var recurringExpense = modelOf(31, YearMonth.of(2026, 10), null);
        recurringExpense.setCardAccount(cardOf(28, 10));

        assertEquals(LocalDate.of(2027, 3, 10), recurringExpense.dueDateIn(YearMonth.of(2027, 2)));
    }

    // ---- toExpenseCommand ----

    @Test
    void toExpenseCommand_shouldCarryTheDataOfTheModelAndTheGivenDueDate() {
        var recurringExpense = modelOf(5, YearMonth.of(2026, 10), null);
        recurringExpense.setId(UUID.randomUUID());
        recurringExpense.setUser(User.builder().name("Ana").build());
        recurringExpense.setCategory(Category.of("Assinaturas"));
        recurringExpense.setCardAccount(cardOf(9, 15));
        var dueDate = LocalDate.of(2026, 12, 20);

        var command = recurringExpense.toExpenseCommand(dueDate);

        assertEquals(recurringExpense.getId(), command.recurringExpenseId());
        assertEquals("Spotify", command.description());
        assertEquals(BigDecimal.valueOf(21.90), command.value());
        assertEquals(dueDate, command.dueDate(), "o vencimento é o informado, não o calculado pelo modelo");
        assertSame(recurringExpense.getUser(), command.user());
        assertSame(recurringExpense.getCategory(), command.category());
        assertSame(recurringExpense.getCardAccount(), command.cardAccount());
    }

    @Test
    void toExpenseCommand_shouldHaveNoCard_whenTheModelHasNoCard() {
        var recurringExpense = modelOf(5, YearMonth.of(2026, 10), null);

        var command = recurringExpense.toExpenseCommand(LocalDate.of(2026, 10, 5));

        assertNull(command.cardAccount());
    }

    private RecurringExpense modelOf(int dayOfMonth, YearMonth startMonth, YearMonth endMonth) {
        return RecurringExpense.of("Spotify", BigDecimal.valueOf(21.90), dayOfMonth, startMonth, endMonth);
    }

    private CardAccount cardOf(int closingDay, int dueDay) {
        return CardAccount.builder()
            .cardName("Nubank")
            .closingDate(closingDay)
            .dueDate(dueDay)
            .build();
    }

}
