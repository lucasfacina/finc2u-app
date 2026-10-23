package br.com.finc2u.server.features.expense.vo;

import br.com.finc2u.server.features.expense.entity.Expense;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseCommandTest {

    private final Expense expense = Expense.builder().description("Mercado").build();

    @Test
    void of_shouldStartWithoutAnyReference() {
        var command = ExpenseCommand.of(expense);

        assertSame(expense, command.expense());
        assertNull(command.cardAccountId());
        assertNull(command.cardName());
        assertNull(command.categoryId());
        assertNull(command.categoryName());
        assertNull(command.purchaseDate());
    }

    @Test
    void with_shouldSetOnlyItsOwnFieldAndKeepTheOthers() {
        var cardId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var purchase = LocalDate.of(2026, 10, 1);

        var command = ExpenseCommand.of(expense)
            .withCardAccountId(cardId)
            .withCardName("Nubank")
            .withCategoryId(categoryId)
            .withCategoryName("Pets")
            .withPurchaseDate(purchase);

        assertEquals(new ExpenseCommand(expense, cardId, "Nubank", categoryId, "Pets", purchase), command);
    }

    @Test
    void with_shouldNotChangeTheOriginalCommand() {
        var original = ExpenseCommand.of(expense);

        original.withCategoryName("Pets");

        assertNull(original.categoryName());
    }

}
